CREATE TABLE payment_inbox (
    transaction_id VARCHAR(64) PRIMARY KEY,
    raw_body       TEXT NOT NULL,
    processed_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
