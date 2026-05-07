-- Reserved balance for limit orders (BUY-side reservation)
ALTER TABLE accounts
    ADD COLUMN IF NOT EXISTS reserved_balance DECIMAL(18, 2) NOT NULL DEFAULT 0.00;
