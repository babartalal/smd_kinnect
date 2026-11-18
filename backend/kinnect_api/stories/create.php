<?php
// POST {image (base64), textLabel?, audience?, clientId?}
require __DIR__ . '/../core.php';
require_method('POST');

$me = require_auth();
$clientId = (string) param('clientId') ?: null;
$audience = param('audience') === 'close_friends' ? 'close_friends' : 'public';

if (param('image') === '') {
    fail('Please select an image for your story');
}
if ($clientId !== null) {
    $existing = q('SELECT id FROM stories WHERE client_id = ?', [$clientId])->fetchColumn();
    if ($existing) {
        respond(['storyId' => (string) $existing]);
    }
}

q('INSERT INTO stories (client_id, user_id, image, text_label, audience, created_at) VALUES (?, ?, ?, ?, ?, ?)',
    [$clientId, $me, save_image(param('image')), (string) param('textLabel'), $audience, now_ms()]);

respond(['storyId' => (string) db()->lastInsertId()]);
