-- Kinnect database schema (MySQL / MariaDB, as shipped with XAMPP).
-- Run through setup.php, or import this file in phpMyAdmin.
-- All timestamps are Unix time in milliseconds, matching the Android app.

CREATE TABLE IF NOT EXISTS users (
    id            INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    first_name    VARCHAR(100) NOT NULL DEFAULT '',
    last_name     VARCHAR(100) NOT NULL DEFAULT '',
    name          VARCHAR(200) NOT NULL DEFAULT '',
    username      VARCHAR(60)  NULL UNIQUE,
    bio           VARCHAR(500) NOT NULL DEFAULT '',
    website       VARCHAR(255) NOT NULL DEFAULT '',
    birthday      VARCHAR(40)  NOT NULL DEFAULT '',
    gender        VARCHAR(40)  NOT NULL DEFAULT '',
    email         VARCHAR(190) NOT NULL UNIQUE,
    phone         VARCHAR(40)  NOT NULL DEFAULT '',
    password_hash VARCHAR(255) NOT NULL,
    profile_pic   VARCHAR(100) NOT NULL DEFAULT '',
    cover_photo   VARCHAR(100) NOT NULL DEFAULT '',
    is_online     TINYINT(1)   NOT NULL DEFAULT 0,
    last_seen     BIGINT       NOT NULL DEFAULT 0,
    fcm_token     VARCHAR(512) NOT NULL DEFAULT '',
    reset_code_hash    VARCHAR(255) NOT NULL DEFAULT '',
    reset_code_expires BIGINT       NOT NULL DEFAULT 0,
    reset_attempts     INT          NOT NULL DEFAULT 0,
    created_at    BIGINT       NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS auth_tokens (
    token_hash CHAR(64) PRIMARY KEY,
    user_id    INT UNSIGNED NOT NULL,
    created_at BIGINT NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- One row per direction, so "friends of X" is a single indexed lookup.
CREATE TABLE IF NOT EXISTS friends (
    user_id    INT UNSIGNED NOT NULL,
    friend_id  INT UNSIGNED NOT NULL,
    created_at BIGINT NOT NULL,
    PRIMARY KEY (user_id, friend_id),
    FOREIGN KEY (user_id)   REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (friend_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS friend_requests (
    sender_id   INT UNSIGNED NOT NULL,
    receiver_id INT UNSIGNED NOT NULL,
    created_at  BIGINT NOT NULL,
    PRIMARY KEY (sender_id, receiver_id),
    KEY idx_receiver (receiver_id),
    FOREIGN KEY (sender_id)   REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (receiver_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS posts (
    id         INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    client_id  VARCHAR(40) NULL UNIQUE,
    author_id  INT UNSIGNED NOT NULL,
    text       TEXT NOT NULL,
    created_at BIGINT NOT NULL,
    KEY idx_author_time (author_id, created_at),
    FOREIGN KEY (author_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS post_images (
    id       INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    post_id  INT UNSIGNED NOT NULL,
    image    VARCHAR(100) NOT NULL,
    position INT NOT NULL DEFAULT 0,
    KEY idx_post (post_id, position),
    FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS post_reactions (
    post_id  INT UNSIGNED NOT NULL,
    user_id  INT UNSIGNED NOT NULL,
    reaction VARCHAR(20) NOT NULL,
    PRIMARY KEY (post_id, user_id),
    FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS comments (
    id                   INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    client_id            VARCHAR(40) NULL UNIQUE,
    post_id              INT UNSIGNED NOT NULL,
    author_id            INT UNSIGNED NOT NULL,
    text                 TEXT NOT NULL,
    image                VARCHAR(100) NOT NULL DEFAULT '',
    reply_to_comment_id  INT UNSIGNED NULL,
    reply_to_author_name VARCHAR(200) NOT NULL DEFAULT '',
    created_at           BIGINT NOT NULL,
    KEY idx_post_time (post_id, created_at),
    FOREIGN KEY (post_id)   REFERENCES posts(id) ON DELETE CASCADE,
    FOREIGN KEY (author_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS comment_likes (
    comment_id INT UNSIGNED NOT NULL,
    user_id    INT UNSIGNED NOT NULL,
    PRIMARY KEY (comment_id, user_id),
    FOREIGN KEY (comment_id) REFERENCES comments(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id)    REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS stories (
    id         INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    client_id  VARCHAR(40) NULL UNIQUE,
    user_id    INT UNSIGNED NOT NULL,
    image      VARCHAR(100) NOT NULL,
    text_label VARCHAR(500) NOT NULL DEFAULT '',
    audience   VARCHAR(20)  NOT NULL DEFAULT 'public',
    created_at BIGINT NOT NULL,
    KEY idx_user_time (user_id, created_at),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- chat_id is "<smaller user id>_<larger user id>".
CREATE TABLE IF NOT EXISTS messages (
    id              INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    client_id       VARCHAR(40) NULL UNIQUE,
    chat_id         VARCHAR(30) NOT NULL,
    sender_id       INT UNSIGNED NOT NULL,
    recipient_id    INT UNSIGNED NOT NULL,
    text            TEXT NOT NULL,
    image           VARCHAR(100) NOT NULL DEFAULT '',
    is_edited       TINYINT(1) NOT NULL DEFAULT 0,
    reply_to_msg_id INT UNSIGNED NULL,
    reply_to_text   VARCHAR(500) NOT NULL DEFAULT '',
    heart_reaction  TINYINT(1) NOT NULL DEFAULT 0,
    is_system       TINYINT(1) NOT NULL DEFAULT 0,
    is_read         TINYINT(1) NOT NULL DEFAULT 0,
    created_at      BIGINT NOT NULL,
    KEY idx_chat_time (chat_id, created_at),
    KEY idx_unread (recipient_id, is_read),
    FOREIGN KEY (sender_id)    REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (recipient_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- At most one incoming call per user, like the old calls/{recipientId} node.
CREATE TABLE IF NOT EXISTS calls (
    recipient_id INT UNSIGNED PRIMARY KEY,
    caller_id    INT UNSIGNED NOT NULL,
    caller_name  VARCHAR(200) NOT NULL DEFAULT '',
    channel_name VARCHAR(64)  NOT NULL,
    type         VARCHAR(10)  NOT NULL DEFAULT 'audio',
    status       VARCHAR(10)  NOT NULL DEFAULT 'ringing',
    created_at   BIGINT NOT NULL,
    KEY idx_caller (caller_id),
    FOREIGN KEY (recipient_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (caller_id)    REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
