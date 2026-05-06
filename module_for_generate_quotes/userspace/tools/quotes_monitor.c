#include <stdio.h>
#include <stdlib.h>
#include <signal.h>
#include <unistd.h>
#include <string.h>

#include "quotes_client.h"

#define REFRESH_SECS 1
#define SHOW_N       10

static volatile int running = 1;

static void on_signal(int sig)
{
    (void)sig;
    running = 0;
}

int main(int argc, char *argv[])
{
    const char *dev  = argc > 1 ? argv[1] : "/dev/quotes";
    qc_handle_t *h   = qc_open(dev);
    struct quote buf[SHOW_N];
    char line[80];
    int  n, i;

    if (!h) {
        perror("qc_open");
        return 1;
    }

    signal(SIGINT,  on_signal);
    signal(SIGTERM, on_signal);

    printf("Monitoring %s (Ctrl-C to quit)\n\n", dev);

    while (running) {
        n = qc_get_latest(h, buf, SHOW_N);
        if (n < 0) { perror("qc_get_latest"); break; }

        /* Простая "перерисовка": поднимаемся на n+2 строки */
        if (n > 0)
            printf("\033[%dA", n + 2);

        printf("%-6s  %-14s  %-8s  %s\n",
               "SYMBOL", "PRICE", "CHANGE", "TIME");
        printf("%-6s  %-14s  %-8s  %s\n",
               "------", "--------------", "--------", "--------");

        for (i = 0; i < n; i++) {
            qc_format(&buf[i], line, sizeof(line));
            printf("%s\n", line);
        }
        fflush(stdout);
        sleep(REFRESH_SECS);
    }

    printf("\nExiting.\n");
    qc_close(h);
    return 0;
}
