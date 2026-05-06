#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "quotes_client.h"

#define DEV "/dev/quotes"

static void usage(const char *prog)
{
    fprintf(stderr,
        "Usage:\n"
        "  %s count              — print quote count in ring buffer\n"
        "  %s get <N>            — print last N quotes\n"
        "  %s interval <ms>      — set generation interval (10-60000)\n"
        "  %s clear              — clear ring buffer\n",
        prog, prog, prog, prog);
}

int main(int argc, char *argv[])
{
    qc_handle_t *h;
    int ret = 0;

    if (argc < 2) { usage(argv[0]); return 1; }

    h = qc_open(DEV);
    if (!h) { perror("open " DEV); return 1; }

    if (strcmp(argv[1], "count") == 0) {
        int n = qc_get_count(h);
        if (n < 0) { perror("ioctl"); ret = 1; }
        else        printf("%d\n", n);

    } else if (strcmp(argv[1], "get") == 0) {
        int n = argc > 2 ? atoi(argv[2]) : 5;
        struct quote *buf = calloc(n, sizeof(*buf));
        char line[80];
        int  got, i;

        if (!buf) { perror("calloc"); ret = 1; goto out; }
        got = qc_get_latest(h, buf, n);
        if (got < 0) { perror("ioctl"); ret = 1; free(buf); goto out; }

        printf("%-6s  %-14s  %-8s  %s\n",
               "SYMBOL", "PRICE", "CHANGE", "TIME");
        for (i = 0; i < got; i++) {
            qc_format(&buf[i], line, sizeof(line));
            printf("%s\n", line);
        }
        free(buf);

    } else if (strcmp(argv[1], "interval") == 0) {
        int ms = argc > 2 ? atoi(argv[2]) : 0;
        if (ms < 10 || ms > 60000) {
            fprintf(stderr, "interval must be 10–60000 ms\n");
            ret = 1; goto out;
        }
        if (qc_set_interval(h, ms) < 0) { perror("ioctl"); ret = 1; }
        else printf("Interval set to %d ms\n", ms);

    } else if (strcmp(argv[1], "clear") == 0) {
        if (qc_clear(h) < 0) { perror("ioctl"); ret = 1; }
        else printf("Ring buffer cleared\n");

    } else {
        usage(argv[0]);
        ret = 1;
    }

out:
    qc_close(h);
    return ret;
}
