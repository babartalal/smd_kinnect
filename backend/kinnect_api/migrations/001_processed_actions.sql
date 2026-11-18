-- Remembers the answer to every write the app sends with a clientId (UUID).
-- When the app retries a queued offline action, the server replays the saved
-- answer instead of performing the action twice.
CREATE TABLE IF NOT EXISTS processed_actions (
    client_id  VARCHAR(40) NOT NULL,
    user_id    INT UNSIGNED NOT NULL,
    response   MEDIUMTEXT NOT NULL,
    created_at BIGINT NOT NULL,
    PRIMARY KEY (client_id, user_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
