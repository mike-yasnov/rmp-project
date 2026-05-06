# kernel/Makefile
# Используется напрямую (make -C kernel/) или через CMake (KernelModule.cmake).
#
# Переменные:
#   KDIR        — путь к Kbuild (по умолчанию: /lib/modules/$(uname -r)/build)
#   ring_size   — размер кольцевого буфера (передаётся через insmod)
#   interval_ms — интервал генерации котировок в мс

KDIR ?= /lib/modules/$(shell uname -r)/build
PWD  := $(shell pwd)

# Kbuild: собираем один объектный файл в модуль
obj-m += quotes_driver.o

# Дополнительные флаги компилятора (раскомментировать при отладке)
# ccflags-y := -DDEBUG -g

.PHONY: all clean load unload reload test info

all:
	$(MAKE) -C $(KDIR) M=$(PWD) modules

clean:
	$(MAKE) -C $(KDIR) M=$(PWD) clean

# ── Управление модулем ───────────────────────────────────────────────

load: all
	@echo "[+] Inserting module..."
	sudo insmod quotes_driver.ko ring_size=64 interval_ms=500
	@echo "[+] Module loaded. Device info:"
	@cat /sys/class/quotes/quotes/dev
	@echo "[+] Fixing /dev/quotes permissions..."
	sudo chmod 666 /dev/quotes
	@echo "[+] Ready: cat /dev/quotes"

unload:
	@echo "[-] Removing module..."
	sudo rmmod quotes_driver
	@echo "[-] Done."

reload: unload load

# ── Тестирование ─────────────────────────────────────────────────────

test:
	@echo "=== /dev/quotes (10 lines) ==="
	head -10 /dev/quotes
	@echo ""
	@echo "=== /proc/quotes_stat ==="
	cat /proc/quotes_stat

info:
	@echo "Kernel release : $(shell uname -r)"
	@echo "Kbuild dir     : $(KDIR)"
	@echo "Module file    : $(PWD)/quotes_driver.ko"
	@if [ -f quotes_driver.ko ]; then \
	    modinfo quotes_driver.ko; \
	else \
	    echo "(not built yet — run 'make all')"; \
	fi
