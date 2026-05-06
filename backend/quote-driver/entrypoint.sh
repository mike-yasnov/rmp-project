#!/bin/sh
set -eu

MODULE_NAME="${MODULE_NAME:-quotes_driver}"
DRIVER_DIR="${DRIVER_DIR:-/driver/kernel}"
HOST_DEVICE_PATH="${HOST_DEVICE_PATH:-/host-dev/quotes}"
RING_SIZE="${DRIVER_RING_SIZE:-64}"
INTERVAL_MS="${DRIVER_INTERVAL_MS:-500}"
SMOKE_READ_LINES="${DRIVER_SMOKE_READ_LINES:-5}"
KERNEL_RELEASE="$(uname -r)"
KBUILD_DIR="/lib/modules/${KERNEL_RELEASE}/build"
LOADED_BY_THIS_CONTAINER=0

cleanup() {
    if [ "$LOADED_BY_THIS_CONTAINER" = "1" ]; then
        echo "quote-driver: unloading ${MODULE_NAME}"
        rmmod "$MODULE_NAME" 2>/dev/null || true
        rm -f "$HOST_DEVICE_PATH" 2>/dev/null || true
    fi
}
trap cleanup INT TERM EXIT

if [ ! -d "$KBUILD_DIR" ]; then
    echo "quote-driver: kernel build directory is missing: ${KBUILD_DIR}" >&2
    echo "quote-driver: install host headers, for example: sudo apt install linux-headers-$(uname -r)" >&2
    exit 1
fi

if [ ! -f "${DRIVER_DIR}/Makefile" ]; then
    echo "quote-driver: driver sources are missing in ${DRIVER_DIR}" >&2
    exit 1
fi

echo "quote-driver: building ${MODULE_NAME}.ko for kernel ${KERNEL_RELEASE}"
make -C "$DRIVER_DIR" KDIR="$KBUILD_DIR" clean
make -C "$DRIVER_DIR" KDIR="$KBUILD_DIR" all

if lsmod | awk '{print $1}' | grep -qx "$MODULE_NAME"; then
    echo "quote-driver: module ${MODULE_NAME} is already loaded"
else
    echo "quote-driver: loading module ring_size=${RING_SIZE} interval_ms=${INTERVAL_MS}"
    insmod "${DRIVER_DIR}/${MODULE_NAME}.ko" "ring_size=${RING_SIZE}" "interval_ms=${INTERVAL_MS}"
    LOADED_BY_THIS_CONTAINER=1
fi

for _ in $(seq 1 50); do
    if [ -e "$HOST_DEVICE_PATH" ]; then
        break
    fi

    if [ -r /sys/class/quotes/quotes/dev ]; then
        DEVNO="$(cat /sys/class/quotes/quotes/dev)"
        MAJOR="${DEVNO%:*}"
        MINOR="${DEVNO#*:}"
        rm -f "$HOST_DEVICE_PATH"
        mknod "$HOST_DEVICE_PATH" c "$MAJOR" "$MINOR"
        break
    fi

    sleep 0.1
done

if [ ! -e "$HOST_DEVICE_PATH" ]; then
    echo "quote-driver: ${HOST_DEVICE_PATH} was not created" >&2
    exit 1
fi

chmod 666 "$HOST_DEVICE_PATH" || true
echo "quote-driver: device is ready at ${HOST_DEVICE_PATH}"

if [ "$SMOKE_READ_LINES" != "0" ]; then
    echo "quote-driver: smoke read"
    timeout 5 head -n "$SMOKE_READ_LINES" "$HOST_DEVICE_PATH" || true
fi

while :; do
    sleep 3600 &
    wait $!
done
