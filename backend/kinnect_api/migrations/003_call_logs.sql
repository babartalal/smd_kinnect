-- Call history. The calls table only holds the call that is ringing or in
-- progress right now and its row is deleted when the call ends; every call
-- also gets one row here that is kept, so the app can show a call log.
-- Times are milliseconds since 1970, like every other table.
--   status: ringing -> answered -> completed   (the call took place)
--           ringing -> declined                (the recipient refused it)
--           ringing -> missed                  (caller hung up, or nobody answered)
CREATE TABLE IF NOT EXISTS call_logs (
    id               INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    caller_id        INT UNSIGNED NOT NULL,
    recipient_id     INT UNSIGNED NOT NULL,
    type             VARCHAR(10)  NOT NULL DEFAULT 'audio',
    status           VARCHAR(10)  NOT NULL DEFAULT 'ringing',
    started_at       BIGINT       NOT NULL,
    answered_at      BIGINT       NULL,
    ended_at         BIGINT       NULL,
    duration_seconds INT UNSIGNED NOT NULL DEFAULT 0,
    KEY idx_caller_started (caller_id, started_at),
    KEY idx_recipient_started (recipient_id, started_at),
    KEY idx_open_call (caller_id, recipient_id, ended_at),
    FOREIGN KEY (caller_id)    REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (recipient_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
