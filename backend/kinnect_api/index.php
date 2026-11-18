<?php
// Health check: http://localhost/kinnect_api/
require __DIR__ . '/core.php';

try {
    db()->query('SELECT 1 FROM users LIMIT 1');
    respond(['service' => 'Kinnect API', 'database' => 'ok', 'serverTime' => now_ms()]);
} catch (Throwable $e) {
    fail('Database is not ready. Start MySQL in XAMPP and open setup.php once.', 503);
}
