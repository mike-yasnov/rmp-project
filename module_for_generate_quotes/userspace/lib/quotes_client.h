#ifndef QUOTES_CLIENT_H
#define QUOTES_CLIENT_H

#include "quotes_ioctl.h" 

typedef struct qc_handle qc_handle_t;

qc_handle_t *qc_open(const char *dev_path);

void qc_close(qc_handle_t *h);

/* Count of quotes in ring buffer */
int qc_get_count(qc_handle_t *h);

int qc_get_latest(qc_handle_t *h, struct quote *out, int n);

int qc_set_interval(qc_handle_t *h, int ms);

int qc_clear(qc_handle_t *h);

char *qc_format(const struct quote *q, char *buf, int buf_len);

#endif
