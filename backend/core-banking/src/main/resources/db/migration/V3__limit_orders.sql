-- Limit orders table
CREATE TABLE IF NOT EXISTS limit_orders (
    id              UUID PRIMARY KEY,
    user_id         UUID NOT NULL REFERENCES users(id),
    ticker          VARCHAR(20) NOT NULL,
    side            VARCHAR(4) NOT NULL CHECK (side IN ('BUY', 'SELL')),
    lots            INT NOT NULL CHECK (lots > 0),
    limit_price     DECIMAL(18, 2) NOT NULL CHECK (limit_price > 0),
    status          VARCHAR(24) NOT NULL DEFAULT 'PENDING'
                    CHECK (status IN ('PENDING', 'FILLED', 'CANCELLED', 'INSUFFICIENT_FUNDS', 'INSUFFICIENT_LOTS')),
    reserved_amount DECIMAL(18, 2) NOT NULL DEFAULT 0.00,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    filled_at       TIMESTAMP WITH TIME ZONE,
    cancelled_at    TIMESTAMP WITH TIME ZONE,
    fill_trade_id   UUID REFERENCES trades(id),
    fill_price      DECIMAL(18, 2)
);

-- Tiny partial index for matcher hot-path
CREATE INDEX IF NOT EXISTS idx_limit_orders_pending_ticker
    ON limit_orders (ticker, side, created_at)
    WHERE status = 'PENDING';

-- User-side query index
CREATE INDEX IF NOT EXISTS idx_limit_orders_user_status_created
    ON limit_orders (user_id, status, created_at DESC);
