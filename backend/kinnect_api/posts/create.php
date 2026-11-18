<?php
// Publishes a post. Two request formats are accepted:
//   multipart/form-data: text, clientId?, images[] = the photo files   (normal case)
//   JSON:                {text, images: [base64, ...], clientId?}      (replayed offline queue)
// clientId makes a retried upload (for example after the phone was offline)
// harmless: the same clientId is only stored once.
require __DIR__ . '/../core.php';
require_method('POST');

$me = require_auth();
$text = (string) param('text');
$images = param('images', []);
$uploads = uploaded_files('images');
$clientId = (string) param('clientId') ?: null;

if (!is_array($images)) {
    $images = [];
}
if ($text === '' && !$images && !$uploads) {
    // A multipart body larger than PHP's post_max_size arrives with no fields at all.
    if ((int) ($_SERVER['CONTENT_LENGTH'] ?? 0) > MAX_UPLOAD_BYTES) {
        fail('These photos are too large to upload together', 413);
    }
    fail('Please write something or attach a photo');
}
if (count($images) + count($uploads) > 10) {
    fail('A post can have at most 10 photos');
}

if ($clientId !== null) {
    $existing = q('SELECT id FROM posts WHERE client_id = ?', [$clientId])->fetchColumn();
    if ($existing) {
        respond(['postId' => (string) $existing]);
    }
}

// Check every uploaded photo before storing any, so a bad one leaves nothing behind.
$checked = [];
foreach ($uploads as $upload) {
    $checked[] = inspect_upload($upload, ['image']);
}

$files = array_values(array_filter(array_map('save_image', $images)));
foreach ($checked as $photo) {
    $files[] = store_upload($photo)['name'];
}

$pdo = db();
$pdo->beginTransaction();
q('INSERT INTO posts (client_id, author_id, text, created_at) VALUES (?, ?, ?, ?)', [$clientId, $me, $text, now_ms()]);
$postId = (int) $pdo->lastInsertId();
foreach ($files as $position => $file) {
    q('INSERT INTO post_images (post_id, image, position) VALUES (?, ?, ?)', [$postId, $file, $position]);
}
$pdo->commit();

respond(['postId' => (string) $postId]);
