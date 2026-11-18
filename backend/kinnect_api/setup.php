<?php
// One-time installer: creates the database and all tables from schema.sql.
// Open http://localhost/kinnect_api/setup.php on the XAMPP machine, or run
//   C:\xampp\php\php.exe setup.php
// It is safe to run again; existing tables and data are left alone.

require_once __DIR__ . '/config.php';

header('Content-Type: text/plain; charset=utf-8');

$isCli = PHP_SAPI === 'cli';
$isLocal = in_array($_SERVER['REMOTE_ADDR'] ?? '', ['127.0.0.1', '::1'], true);
if (!$isCli && !$isLocal) {
    http_response_code(403);
    exit("setup.php can only be run from the server machine itself.\n");
}

try {
    $pdo = new PDO('mysql:host=' . DB_HOST . ';port=' . DB_PORT . ';charset=utf8mb4', DB_USER, DB_PASS,
        [PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION]);
    $pdo->exec('CREATE DATABASE IF NOT EXISTS `' . DB_NAME . '` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci');
    $pdo->exec('USE `' . DB_NAME . '`');

    // Drop comment lines, then run each statement.
    $sql = preg_replace('/^\s*--.*$/m', '', file_get_contents(__DIR__ . '/schema.sql'));
    foreach (array_filter(array_map('trim', explode(';', $sql))) as $statement) {
        $pdo->exec($statement);
    }

    // Then run each migration in migrations/ exactly once, in file-name order.
    $pdo->exec('CREATE TABLE IF NOT EXISTS schema_migrations (name VARCHAR(190) PRIMARY KEY, applied_at BIGINT NOT NULL)');
    $applied = $pdo->query('SELECT name FROM schema_migrations')->fetchAll(PDO::FETCH_COLUMN);
    $files = glob(__DIR__ . '/migrations/*.sql') ?: [];
    sort($files);
    foreach ($files as $file) {
        $name = basename($file);
        if (in_array($name, $applied, true)) {
            continue;
        }
        $migration = preg_replace('/^\s*--.*$/m', '', file_get_contents($file));
        foreach (array_filter(array_map('trim', explode(';', $migration))) as $statement) {
            $pdo->exec($statement);
        }
        $pdo->prepare('INSERT INTO schema_migrations (name, applied_at) VALUES (?, ?)')
            ->execute([$name, (int) round(microtime(true) * 1000)]);
        echo "Applied migration $name\n";
    }

    if (!is_dir(UPLOAD_DIR)) {
        mkdir(UPLOAD_DIR, 0775, true);
    }

    $tables = $pdo->query('SHOW TABLES')->fetchAll(PDO::FETCH_COLUMN);
    echo "Kinnect database '" . DB_NAME . "' is ready.\nTables: " . implode(', ', $tables) . "\n";
} catch (Throwable $e) {
    http_response_code(500);
    echo "Setup failed: " . $e->getMessage() . "\nIs MySQL running in the XAMPP control panel?\n";
}
