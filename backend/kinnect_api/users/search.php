<?php
// GET ?q=text
// Finds other users whose username or name contains the text.
require __DIR__ . '/../core.php';

$me = require_auth();
$query = (string) param('q');
if ($query === '') {
    respond([]);
}

// Escape LIKE wildcards so the text is matched literally.
$like = '%' . addcslashes($query, '\\%_') . '%';
$rows = q('SELECT * FROM users
           WHERE id <> ? AND (username LIKE ? OR first_name LIKE ? OR last_name LIKE ? OR name LIKE ?)
           ORDER BY name LIMIT 50', [$me, $like, $like, $like, $like])->fetchAll();

respond(array_map(fn($row) => user_with_relation($row, $me), $rows));
