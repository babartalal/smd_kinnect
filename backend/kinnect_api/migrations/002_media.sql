-- Media in chats and stories: a message or story can now carry a video or a
-- document as well as an image. The existing "image" column keeps holding the
-- stored file name. media_type is image, video or file. An older image
-- message has an empty media_type and is treated as an image.
ALTER TABLE messages
    ADD COLUMN media_type VARCHAR(10)  NOT NULL DEFAULT '' AFTER image,
    ADD COLUMN file_name  VARCHAR(255) NOT NULL DEFAULT '' AFTER media_type;

ALTER TABLE stories
    ADD COLUMN media_type VARCHAR(10) NOT NULL DEFAULT 'image' AFTER image;

-- One row per file stored by media/upload.php. A message or story may only
-- attach a file its own author uploaded, and each upload can be attached once,
-- so nobody can attach (and later delete) a file that belongs to someone else.
CREATE TABLE IF NOT EXISTS media_uploads (
    name       VARCHAR(100) NOT NULL PRIMARY KEY,
    user_id    INT UNSIGNED NOT NULL,
    media_type VARCHAR(10)  NOT NULL,
    file_name  VARCHAR(255) NOT NULL DEFAULT '',
    size       BIGINT NOT NULL DEFAULT 0,
    used       TINYINT(1) NOT NULL DEFAULT 0,
    created_at BIGINT NOT NULL,
    KEY idx_user (user_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
