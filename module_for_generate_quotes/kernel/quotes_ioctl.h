/* SPDX-License-Identifier: GPL-2.0 */
/*
 * quotes_ioctl.h — публичный ABI между ядром и user-space
 * Используется обеими сторонами без изменений.
 */

#ifndef _QUOTES_IOCTL_H
#define _QUOTES_IOCTL_H

#ifdef __KERNEL__
#  include <linux/types.h>
#  include <linux/ioctl.h>
#else
#  include <stdint.h>
#  include <sys/ioctl.h>
   typedef uint8_t  __u8;
   typedef uint32_t __u32;
   typedef int32_t  __s32;
   typedef uint64_t __u64;
#endif

/* Одна котировка — совместимая раскладка ядро/user */
struct quote {
    char  symbol[8];   /* "AAPL\0..."                          */
    __u32 price_int;   /* 185                                  */
    __u32 price_frac;  /* 73  → $185.73                        */
    __s32 change_bp;   /* +57 базисных пункта = +0.57%         */
    __u64 timestamp;   /* ktime_get_real_ns()                  */
};

/* Запрос на получение N последних котировок */
struct quotes_ioc_get_req {
    int          count;   /* [in] сколько хотим / [out] сколько есть */
    struct quote *quotes; /* [out] user-space буфер                  */
};

#define QUOTES_IOC_MAGIC        'Q'
#define QUOTES_IOC_GET_COUNT    _IOR (QUOTES_IOC_MAGIC, 1, int)
#define QUOTES_IOC_GET_LATEST   _IOWR(QUOTES_IOC_MAGIC, 2, struct quotes_ioc_get_req)
#define QUOTES_IOC_SET_INTERVAL _IOW (QUOTES_IOC_MAGIC, 3, int)
#define QUOTES_IOC_CLEAR        _IO  (QUOTES_IOC_MAGIC, 4)

#endif /* _QUOTES_IOCTL_H */
