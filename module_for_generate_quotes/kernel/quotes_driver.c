// SPDX-License-Identifier: GPL-2.0
/*
 * quotes_driver.c — биржевые котировки как char device /dev/quotes
 *
 * Архитектура:
 *   - Char device /dev/quotes: open/read/release/ioctl/poll
 *   - Ring buffer (spinlock): кольцевой буфер последних RING_SIZE котировок
 *   - Kernel thread + hrtimer: генерирует случайные котировки по таймеру
 */

#include <linux/module.h>
#include <linux/kernel.h>
#include <linux/fs.h>
#include <linux/cdev.h>
#include <linux/device.h>
#include <linux/uaccess.h>
#include <linux/spinlock.h>
#include <linux/kthread.h>
#include <linux/hrtimer.h>
#include <linux/random.h>
#include <linux/slab.h>
#include <linux/proc_fs.h>
#include <linux/seq_file.h>
#include <linux/wait.h>
#include <linux/poll.h>
#include <linux/version.h>

#include "quotes_ioctl.h"

MODULE_LICENSE("GPL");
MODULE_AUTHOR("quotes_driver");
MODULE_DESCRIPTION("Stock quotes generator char device");
MODULE_VERSION("1.0");

/* ─── Параметры модуля ─────────────────────────────────────────────── */

static int ring_size   = 64;
static int interval_ms = 500;

module_param(ring_size,   int, 0444);
module_param(interval_ms, int, 0644);
MODULE_PARM_DESC(ring_size,   "Ring buffer size (default 64)");
MODULE_PARM_DESC(interval_ms, "Quote generation interval ms (default 500)");

/* ─── Кольцевой буфер ──────────────────────────────────────────────── */

struct ring_buffer {
    struct quote  *entries;
    int            size;
    int            head;
    int            count;
    spinlock_t     lock;
    u64            total_written;
};

static int rb_init(struct ring_buffer *rb, int size)
{
    rb->entries = kcalloc(size, sizeof(struct quote), GFP_KERNEL);
    if (!rb->entries)
        return -ENOMEM;
    rb->size          = size;
    rb->head          = 0;
    rb->count         = 0;
    rb->total_written = 0;
    spin_lock_init(&rb->lock);
    return 0;
}

static void rb_free(struct ring_buffer *rb)
{
    kfree(rb->entries);
    rb->entries = NULL;
}

/* Вызывается при удержанном spinlock */
static void rb_push_locked(struct ring_buffer *rb, const struct quote *q)
{
    rb->entries[rb->head] = *q;
    rb->head = (rb->head + 1) % rb->size;
    if (rb->count < rb->size)
        rb->count++;
    rb->total_written++;
}

/* Копирует последние n котировок; берёт spinlock самостоятельно */
static int rb_read_latest(struct ring_buffer *rb, struct quote *dst, int n)
{
    int count, start, i;
    unsigned long flags;

    spin_lock_irqsave(&rb->lock, flags);
    count = min(n, rb->count);
    start = (rb->head - count + rb->size) % rb->size;
    for (i = 0; i < count; i++)
        dst[i] = rb->entries[(start + i) % rb->size];
    spin_unlock_irqrestore(&rb->lock, flags);
    return count;
}

/* ─── Глобальное состояние устройства ─────────────────────────────── */

struct quotes_dev {
    dev_t                  devno;
    struct cdev            cdev;
    struct class          *cls;
    struct device         *dev;
    struct ring_buffer     rb;
    struct task_struct    *thread;
    struct hrtimer         timer;
    bool                   running;
    wait_queue_head_t      wq;
    struct proc_dir_entry *proc_entry;
    atomic_t               reads_total;
    atomic_t               opens_total;
};

static struct quotes_dev qdev;

/* ─── Генератор котировок ──────────────────────────────────────────── */

static const char *symbols[] = {
    "AAPL","GOOG","MSFT","AMZN","TSLA","NVDA","META","NFLX","INTC","AMD",
};
static const u32 base_prices[] = {185,140,415,178,248,875,510,630,32,168};
#define NUM_SYMBOLS ARRAY_SIZE(symbols)

static struct quote generate_quote(void)
{
    struct quote q;
    u32 rnd, idx;
    s32 delta_bp;

    get_random_bytes(&rnd, sizeof(rnd));
    idx = rnd % NUM_SYMBOLS;
    strscpy(q.symbol, symbols[idx], sizeof(q.symbol));

    get_random_bytes(&rnd, sizeof(rnd));
    delta_bp     = (s32)(rnd % 400) - 200;
    q.price_int  = base_prices[idx] +
                   (u32)((s64)base_prices[idx] * delta_bp / 10000);
    get_random_bytes(&rnd, sizeof(rnd));
    q.price_frac  = rnd % 100;
    q.change_bp   = delta_bp;
    q.timestamp   = ktime_get_real_ns();
    return q;
}

static enum hrtimer_restart timer_callback(struct hrtimer *timer)
{
    struct quotes_dev *d = container_of(timer, struct quotes_dev, timer);
    if (!d->running)
        return HRTIMER_NORESTART;
    wake_up_process(d->thread);
    hrtimer_forward_now(timer, ms_to_ktime(interval_ms));
    return HRTIMER_RESTART;
}

static int quote_thread(void *data)
{
    struct quotes_dev *d = data;
    unsigned long flags;

    pr_info("quotes: generator thread started\n");
    while (!kthread_should_stop()) {
        struct quote q;
        set_current_state(TASK_INTERRUPTIBLE);
        schedule();
        if (kthread_should_stop())
            break;
        q = generate_quote();
        spin_lock_irqsave(&d->rb.lock, flags);
        rb_push_locked(&d->rb, &q);
        spin_unlock_irqrestore(&d->rb.lock, flags);
        wake_up_interruptible(&d->wq);
    }
    pr_info("quotes: generator thread stopped\n");
    return 0;
}

/* ─── File operations ──────────────────────────────────────────────── */

static int quotes_open(struct inode *inode, struct file *filp)
{
    atomic_inc(&qdev.opens_total);
    return 0;
}

static int quotes_release(struct inode *inode, struct file *filp)
{
    return 0;
}

static ssize_t quotes_read(struct file *filp, char __user *buf,
                            size_t len, loff_t *ppos)
{
    struct quote *local;
    char         *line;
    ssize_t       written = 0;
    int           n, i, ret;
    int           max_q = min((int)(len / 48) + 1, qdev.rb.size);

    if (max_q <= 0)
        return -EINVAL;

    if (qdev.rb.count == 0) {
        if (filp->f_flags & O_NONBLOCK)
            return -EAGAIN;
        ret = wait_event_interruptible(qdev.wq, qdev.rb.count > 0);
        if (ret)
            return -EINTR;
    }

    local = kcalloc(max_q, sizeof(*local), GFP_KERNEL);
    if (!local)
        return -ENOMEM;
    line = kmalloc(128, GFP_KERNEL);
    if (!line) { kfree(local); return -ENOMEM; }

    n = rb_read_latest(&qdev.rb, local, max_q);
    atomic_add(n, &qdev.reads_total);

    for (i = 0; i < n; i++) {
        struct quote *q = &local[i];
        int llen = snprintf(line, 128, "%-6s %4u.%02u  %+dbp  %llu\n",
            q->symbol, q->price_int, q->price_frac,
            q->change_bp, (unsigned long long)q->timestamp);
        if (written + llen > (ssize_t)len)
            break;
        if (copy_to_user(buf + written, line, llen)) {
            written = -EFAULT;
            break;
        }
        written += llen;
    }

    kfree(line);
    kfree(local);
    return written;
}

static __poll_t quotes_poll(struct file *filp, poll_table *wait)
{
    poll_wait(filp, &qdev.wq, wait);
    return (qdev.rb.count > 0) ? (EPOLLIN | EPOLLRDNORM) : 0;
}

static long quotes_ioctl(struct file *filp, unsigned int cmd, unsigned long arg)
{
    unsigned long flags;

    switch (cmd) {
    case QUOTES_IOC_GET_COUNT: {
        int cnt;
        spin_lock_irqsave(&qdev.rb.lock, flags);
        cnt = qdev.rb.count;
        spin_unlock_irqrestore(&qdev.rb.lock, flags);
        return put_user(cnt, (int __user *)arg) ? -EFAULT : 0;
    }
    case QUOTES_IOC_GET_LATEST: {
        struct quotes_ioc_get_req req;
        struct quote *tmp;
        int n, ret = 0;
        if (copy_from_user(&req, (void __user *)arg, sizeof(req)))
            return -EFAULT;
        if (req.count <= 0 || req.count > qdev.rb.size)
            return -EINVAL;
        tmp = kcalloc(req.count, sizeof(*tmp), GFP_KERNEL);
        if (!tmp)
            return -ENOMEM;
        n = rb_read_latest(&qdev.rb, tmp, req.count);
        if (copy_to_user(req.quotes, tmp, n * sizeof(*tmp)))
            ret = -EFAULT;
        else if (put_user(n, &((struct quotes_ioc_get_req __user *)arg)->count))
            ret = -EFAULT;
        kfree(tmp);
        return ret;
    }
    case QUOTES_IOC_SET_INTERVAL: {
        int ms;
        if (get_user(ms, (int __user *)arg))
            return -EFAULT;
        if (ms < 10 || ms > 60000)
            return -EINVAL;
        interval_ms = ms;
        return 0;
    }
    case QUOTES_IOC_CLEAR:
        spin_lock_irqsave(&qdev.rb.lock, flags);
        qdev.rb.head  = 0;
        qdev.rb.count = 0;
        spin_unlock_irqrestore(&qdev.rb.lock, flags);
        return 0;
    default:
        return -ENOTTY;
    }
}

static const struct file_operations quotes_fops = {
    .owner          = THIS_MODULE,
    .open           = quotes_open,
    .release        = quotes_release,
    .read           = quotes_read,
    .poll           = quotes_poll,
    .unlocked_ioctl = quotes_ioctl,
};

/* ─── procfs ───────────────────────────────────────────────────────── */

static int quotes_proc_show(struct seq_file *m, void *v)
{
    unsigned long flags;
    int count; u64 total;
    spin_lock_irqsave(&qdev.rb.lock, flags);
    count = qdev.rb.count; total = qdev.rb.total_written;
    spin_unlock_irqrestore(&qdev.rb.lock, flags);
    seq_printf(m, "ring_size:     %d\n",   qdev.rb.size);
    seq_printf(m, "current_count: %d\n",   count);
    seq_printf(m, "total_written: %llu\n", total);
    seq_printf(m, "interval_ms:   %d\n",   interval_ms);
    seq_printf(m, "opens_total:   %d\n",   atomic_read(&qdev.opens_total));
    seq_printf(m, "reads_total:   %d\n",   atomic_read(&qdev.reads_total));
    return 0;
}
static int quotes_proc_open(struct inode *i, struct file *f)
{ return single_open(f, quotes_proc_show, NULL); }

static const struct proc_ops quotes_proc_ops = {
    .proc_open    = quotes_proc_open,
    .proc_read    = seq_read,
    .proc_lseek   = seq_lseek,
    .proc_release = single_release,
};

/* ─── init / exit ──────────────────────────────────────────────────── */

static int __init quotes_init(void)
{
    int ret;

    ret = rb_init(&qdev.rb, ring_size);
    if (ret) return ret;

    atomic_set(&qdev.reads_total, 0);
    atomic_set(&qdev.opens_total, 0);
    init_waitqueue_head(&qdev.wq);

    ret = alloc_chrdev_region(&qdev.devno, 0, 1, "quotes");
    if (ret) goto err_rb;

    cdev_init(&qdev.cdev, &quotes_fops);
    qdev.cdev.owner = THIS_MODULE;
    ret = cdev_add(&qdev.cdev, qdev.devno, 1);
    if (ret) goto err_chrdev;

    qdev.cls = class_create("quotes");
    if (IS_ERR(qdev.cls)) { ret = PTR_ERR(qdev.cls); goto err_cdev; }

    qdev.dev = device_create(qdev.cls, NULL, qdev.devno, NULL, "quotes");
    if (IS_ERR(qdev.dev)) { ret = PTR_ERR(qdev.dev); goto err_class; }

    qdev.running = true;
    qdev.thread  = kthread_run(quote_thread, &qdev, "kquotes");
    if (IS_ERR(qdev.thread)) { ret = PTR_ERR(qdev.thread); goto err_device; }

#if LINUX_VERSION_CODE >= KERNEL_VERSION(6, 16, 0)
    hrtimer_setup(&qdev.timer, timer_callback, CLOCK_MONOTONIC, HRTIMER_MODE_REL);
#else
    hrtimer_init(&qdev.timer, CLOCK_MONOTONIC, HRTIMER_MODE_REL);
    qdev.timer.function = timer_callback;
#endif
    hrtimer_start(&qdev.timer, ms_to_ktime(interval_ms), HRTIMER_MODE_REL);

    qdev.proc_entry = proc_create("quotes_stat", 0444, NULL, &quotes_proc_ops);

    pr_info("quotes: /dev/quotes ready (major=%d)\n", MAJOR(qdev.devno));
    return 0;

err_device: device_destroy(qdev.cls, qdev.devno);
err_class:  class_destroy(qdev.cls);
err_cdev:   cdev_del(&qdev.cdev);
err_chrdev: unregister_chrdev_region(qdev.devno, 1);
err_rb:     rb_free(&qdev.rb);
    return ret;
}

static void __exit quotes_exit(void)
{
    if (qdev.proc_entry) proc_remove(qdev.proc_entry);
    qdev.running = false;
    hrtimer_cancel(&qdev.timer);
    if (qdev.thread) kthread_stop(qdev.thread);
    device_destroy(qdev.cls, qdev.devno);
    class_destroy(qdev.cls);
    cdev_del(&qdev.cdev);
    unregister_chrdev_region(qdev.devno, 1);
    rb_free(&qdev.rb);
    pr_info("quotes: unloaded\n");
}

module_init(quotes_init);
module_exit(quotes_exit);
