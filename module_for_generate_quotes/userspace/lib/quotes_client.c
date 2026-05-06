#include "quotes_client.h"

#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <fcntl.h>
#include <unistd.h>
#include <errno.h>
#include <sys/ioctl.h>
#include <time.h>

struct qc_handle {
    int fd;
};

qc_handle_t *qc_open(const char *dev_path)
{
    qc_handle_t *h = malloc(sizeof(*h));
    if (!h) return NULL;

    h->fd = open(dev_path ? dev_path : "/dev/quotes", O_RDONLY | O_NONBLOCK);
    if (h->fd < 0) {
        free(h);
        return NULL;
    }
    return h;
}

void qc_close(qc_handle_t *h)
{
    if (h) {
        close(h->fd);
        free(h);
    }
}

int qc_get_count(qc_handle_t *h)
{
    int cnt = 0;
    if (ioctl(h->fd, QUOTES_IOC_GET_COUNT, &cnt) < 0)
        return -1;
    return cnt;
}

int qc_get_latest(qc_handle_t *h, struct quote *out, int n)
{
    struct quotes_ioc_get_req req = { .count = n, .quotes = out };
    if (ioctl(h->fd, QUOTES_IOC_GET_LATEST, &req) < 0)
        return -1;
    return req.count;
}

int qc_set_interval(qc_handle_t *h, int ms)
{
    return ioctl(h->fd, QUOTES_IOC_SET_INTERVAL, &ms);
}

int qc_clear(qc_handle_t *h)
{
    return ioctl(h->fd, QUOTES_IOC_CLEAR, 0);
}

char *qc_format(const struct quote *q, char *buf, int buf_len)
{
    time_t sec  = (time_t)(q->timestamp / 1000000000ULL);
    struct tm *t = localtime(&sec);
    char ts[16];
    strftime(ts, sizeof(ts), "%H:%M:%S", t);

    snprintf(buf, buf_len, "%-6s %4u.%02u USD  %+5d bp  %s",
             q->symbol, q->price_int, q->price_frac, q->change_bp, ts);
    return buf;
}
