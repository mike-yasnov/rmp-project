CREATE TABLE IF NOT EXISTS users (
    id          UUID PRIMARY KEY,
    username    VARCHAR(100) UNIQUE NOT NULL,
    email       VARCHAR(255) UNIQUE NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS accounts (
    id          UUID PRIMARY KEY,
    user_id     UUID UNIQUE NOT NULL REFERENCES users(id),
    balance     DECIMAL(18, 2) NOT NULL DEFAULT 0.00,
    currency    VARCHAR(3) NOT NULL DEFAULT 'RUB'
);

CREATE TABLE IF NOT EXISTS trades (
    id              UUID PRIMARY KEY,
    user_id         UUID NOT NULL REFERENCES users(id),
    ticker          VARCHAR(20) NOT NULL,
    action          VARCHAR(4) NOT NULL CHECK (action IN ('BUY', 'SELL')),
    lots            INT NOT NULL CHECK (lots > 0),
    price_per_lot   DECIMAL(18, 2) NOT NULL,
    total_amount    DECIMAL(18, 2) NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS portfolio (
    user_id     UUID NOT NULL REFERENCES users(id),
    ticker      VARCHAR(20) NOT NULL,
    lots        INT NOT NULL DEFAULT 0,
    avg_price   DECIMAL(18, 2) NOT NULL DEFAULT 0.00,
    PRIMARY KEY (user_id, ticker)
);

CREATE INDEX IF NOT EXISTS idx_trades_user_id ON trades(user_id);
CREATE INDEX IF NOT EXISTS idx_trades_created_at ON trades(created_at);
