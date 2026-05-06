#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <errno.h>

#include "quotes_client.h"

#define DEV "/dev/quotes"

static int passed = 0;
static int failed = 0;

#define CHECK(cond, msg) \
    do { \
        if (cond) { printf("  PASS: %s\n", msg); passed++; } \
        else      { printf("  FAIL: %s\n", msg); failed++; } \
    } while(0)

int main(void)
{
    qc_handle_t *h;
    struct quote buf[16];
    int n, cnt;

    printf("=== Integration tests (%s) ===\n\n", DEV);

    h = qc_open(DEV);
    CHECK(h != NULL, "open /dev/quotes");
    if (!h) {
        fprintf(stderr, "Cannot open %s: %s\n", DEV, strerror(errno));
        fprintf(stderr, "Load the kernel module first: sudo insmod quotes_driver.ko\n");
        return 1;
    }

    /* Даём генератору время наполнить буфер */
    printf("  Waiting 1.5s for quotes...\n");
    usleep(1500000);

    /* GET_COUNT */
    cnt = qc_get_count(h);
    CHECK(cnt > 0, "ioctl GET_COUNT returns > 0");
    printf("    count = %d\n", cnt);

    /* GET_LATEST */
    n = qc_get_latest(h, buf, 5);
    CHECK(n > 0, "ioctl GET_LATEST returns > 0 entries");
    if (n > 0) {
        char line[80];
        printf("  Sample quote: %s\n", qc_format(&buf[0], line, sizeof(line)));
        CHECK(buf[0].price_int > 0,   "price_int > 0");
        CHECK(buf[0].timestamp > 0,   "timestamp > 0");
        CHECK(buf[0].symbol[0] != 0,  "symbol non-empty");
    }

    /* SET_INTERVAL */
    CHECK(qc_set_interval(h, 100) == 0, "ioctl SET_INTERVAL 100ms");
    usleep(600000);
    int cnt2 = qc_get_count(h);
    CHECK(cnt2 >= cnt, "more quotes generated after interval change");

    /* Restore */
    qc_set_interval(h, 500);

    /* CLEAR */
    CHECK(qc_clear(h) == 0, "ioctl CLEAR");
    /* буфер очищён, но генератор сразу же может добавить новые — проверяем быстро */
    usleep(50000);
    int cnt3 = qc_get_count(h);
    CHECK(cnt3 <= 2, "count small immediately after clear");

    qc_close(h);

    printf("\nResults: %d passed, %d failed\n", passed, failed);
    return failed ? 1 : 0;
}
