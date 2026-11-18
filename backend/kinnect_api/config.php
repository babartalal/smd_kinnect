<?php
// Kinnect API configuration.
// To override any value on your machine without touching Git, create
// config.local.php next to this file and define the constants there first.

if (file_exists(__DIR__ . '/config.local.php')) {
    require __DIR__ . '/config.local.php';
}

defined('DB_HOST') || define('DB_HOST', '127.0.0.1');
defined('DB_PORT') || define('DB_PORT', 3306);
defined('DB_NAME') || define('DB_NAME', 'kinnect_db');
defined('DB_USER') || define('DB_USER', 'root');
defined('DB_PASS') || define('DB_PASS', '');

// Show exception details in API responses. Turn off outside development.
defined('API_DEBUG') || define('API_DEBUG', true);

// A user counts as online if the app contacted the server this recently.
defined('ONLINE_WINDOW_MS') || define('ONLINE_WINDOW_MS', 45000);

// Optional: Firebase service account JSON, used only to send FCM push
// notifications. Push is skipped when this file does not exist.
defined('FCM_SERVICE_ACCOUNT') || define('FCM_SERVICE_ACCOUNT', __DIR__ . '/firebase-service-account.json');

defined('UPLOAD_DIR') || define('UPLOAD_DIR', __DIR__ . '/uploads');
defined('MAX_IMAGE_BYTES') || define('MAX_IMAGE_BYTES', 5 * 1024 * 1024);

// Largest file media/upload.php (and a multipart post) accepts. PHP's own
// upload_max_filesize / post_max_size in php.ini must be at least this big
// (a stock XAMPP allows 40 MB).
defined('MAX_UPLOAD_BYTES') || define('MAX_UPLOAD_BYTES', 25 * 1024 * 1024);
