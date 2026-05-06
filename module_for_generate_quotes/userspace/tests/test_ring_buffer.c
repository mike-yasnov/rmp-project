#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>

typedef uint32_t __u32;
typedef int32_t  __s32;
typedef uint64_t __u64;
#include "quotes_ioctl.h"


typedef struct {
    struct quote *entries;
    int size, head, count;
} rb_t;

static rb_t *rb_create(int size)
{
    rb_t *rb = calloc(1, sizeof(*rb));
    rb->entries = calloc(size, sizeof(struct quote));
    rb->size = size;
    return rb;
}

static void rb_push(rb_t *rb, const struct quote *q)
{
    rb->entries[rb->head] = *q;
    rb->head = (rb->head + 1) % rb->size;
    if (rb->count < rb->size)
        rb->count++;
}

static int rb_read(rb_t *rb, struct quote *dst, int n)
{
    int count = n < rb->count ? n : rb->count;
    int start = (rb->head - count + rb->size) % rb->size;
    for (int i = 0; i < count; i++)
        dst[i] = rb->entries[(start + i) % rb->size];
    return count;
}

static void rb_free(rb_t *rb)
{
    free(rb->entries);
    free(rb);
}

/* ── Вспомогательные макросы ─────────────────────────────────────── */

#define ASSERT(cond, msg) \
    do { \
        if (!(cond)) { \
            fprintf(stderr, "FAIL [%s:%d] %s\n", __FILE__, __LINE__, msg); \
            exit(1); \
        } \
        printf("  PASS: %s\n", msg); \
    } while(0)

static struct quote make_quote(const char *sym, uint32_t price)
{
    struct quote q = {0};
    strncpy(q.symbol, sym, sizeof(q.symbol) - 1);
    q.price_int = price;
    return q;
}

/* ── Тесты ───────────────────────────────────────────────────────── */

static void test_empty(void)
{
    puts("test_empty:");
    rb_t *rb = rb_create(4);
    struct quote out[4];

    ASSERT(rb->count == 0, "initial count == 0");
    ASSERT(rb_read(rb, out, 4) == 0, "read from empty returns 0");

    rb_free(rb);
}

static void test_basic_push_read(void)
{
    puts("test_basic_push_read:");
    rb_t *rb = rb_create(4);
    struct quote out[4];

    struct quote a = make_quote("AAPL", 185);
    struct quote b = make_quote("GOOG", 140);
    rb_push(rb, &a);
    rb_push(rb, &b);

    ASSERT(rb->count == 2, "count == 2 after two pushes");

    int n = rb_read(rb, out, 4);
    ASSERT(n == 2, "read returns 2");
    ASSERT(out[0].price_int == 185, "first entry is AAPL");
    ASSERT(out[1].price_int == 140, "second entry is GOOG");

    rb_free(rb);
}

static void test_overwrite(void)
{
    puts("test_overwrite:");
    rb_t *rb = rb_create(3);   /* буфер на 3 */
    struct quote out[3];
    int i;

    /* Вталкиваем 5 котировок — первые 2 должны вытесниться */
    for (i = 0; i < 5; i++) {
        char sym[8];
        snprintf(sym, sizeof(sym), "T%d", i);
        struct quote q = make_quote(sym, (uint32_t)i * 10);
        rb_push(rb, &q);
    }

    ASSERT(rb->count == 3, "count stays at capacity after overwrite");

    int n = rb_read(rb, out, 3);
    ASSERT(n == 3, "read returns 3");
    /* Должны быть последние 3: T2, T3, T4 */
    ASSERT(out[0].price_int == 20, "oldest survivor is T2 (price=20)");
    ASSERT(out[2].price_int == 40, "newest is T4 (price=40)");

    rb_free(rb);
}

static void test_partial_read(void)
{
    puts("test_partial_read:");
    rb_t *rb = rb_create(8);
    struct quote out[2];
    int i;

    for (i = 0; i < 6; i++) {
        struct quote q = make_quote("XX", (uint32_t)i);
        rb_push(rb, &q);
    }

    int n = rb_read(rb, out, 2);   /* хотим только 2 из 6 */
    ASSERT(n == 2, "partial read returns requested count");
    /* Последние 2: индексы 4 и 5 */
    ASSERT(out[0].price_int == 4, "second to last");
    ASSERT(out[1].price_int == 5, "last");

    rb_free(rb);
}

static void test_single_element(void)
{
    puts("test_single_element:");
    rb_t *rb = rb_create(1);
    struct quote out[1];

    struct quote q1 = make_quote("A", 100);
    struct quote q2 = make_quote("B", 200);
    rb_push(rb, &q1);
    rb_push(rb, &q2);   /* вытесняет q1 */

    ASSERT(rb->count == 1, "count == 1 with size-1 buffer");
    rb_read(rb, out, 1);
    ASSERT(out[0].price_int == 200, "only newest survives in size-1 buffer");

    rb_free(rb);
}

int main(void)
{
    printf("=== Ring buffer unit tests ===\n\n");
    test_empty();
    test_basic_push_read();
    test_overwrite();
    test_partial_read();
    test_single_element();
    printf("\nAll tests passed.\n");
    return 0;
}
