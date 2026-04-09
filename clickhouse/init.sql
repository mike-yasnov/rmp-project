CREATE TABLE IF NOT EXISTS quotes (
    ticker      String,
    price       Float64,
    volume      UInt64,
    timestamp   DateTime64(3)
) ENGINE = MergeTree()
ORDER BY (ticker, timestamp);
