FROM ubuntu:24.04

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        build-essential \
        ca-certificates \
        kmod \
        libelf1 \
        make \
    && rm -rf /var/lib/apt/lists/*

COPY backend/quote-driver/entrypoint.sh /usr/local/bin/quote-driver-entrypoint
RUN chmod +x /usr/local/bin/quote-driver-entrypoint

ENTRYPOINT ["/usr/local/bin/quote-driver-entrypoint"]
