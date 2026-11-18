<?php
// POST {text, images: [base64, ...], clientId?}
// Publishes a post. clientId makes a retried upload (for example after the
// phone was offline) harmless: the same clientId is only stored once.
require __DIR__ . '/../core.php';
require_method('POST');

$me = require_auth();
$text = (string) param('text');
$images = param('images', []);
$clientId = (string) param('clientId') ?: null;

if (!is_array($images)) {
    $images = [];
}
if ($text === '' && !$images) {
    fail('Please write something or attach a photo');
}
if (count($images) > 10) {
    fail('A post can have at most 10 photos');
}

if ($clientId !== null) {
    $existing = q('SELECT id FROM posts WHERE client_id = ?', [$clientId])->fetchColumn();
    if ($existing) {
        respond(['postId' => (string) $existing]);
    }
}

$files = array_values(array_filter(array_map('save_image', $images)));

$pdo = db();
$pdo->beginTransaction();
q('INSERT INTO posts (client_id, author_id, text, created_at) VALUES (?, ?, ?, ?)', [$clientId, $me, $text, now_ms()]);
$postId = (int) $pdo->lastInsertId();
foreach ($files as $position => $file) {
    q('INSERT INTO post_images (post_id, image, position) VALUES (?, ?, ?)', [$postId, $file, $position]);
}
$pdo->commit();

respond(['postId' => (string) $postId]);
