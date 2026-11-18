<?php
// POST {textLabel?, audience?, clientId?} plus the story itself, either
//   media:     "uploads/<name>" exactly as media/upload.php returned it (an image or a video)
//   mediaType: image | video  (informational; the server uses what it recorded at upload)
// or
//   image:     a Base64 picture (older format, still used by the offline queue)
require __DIR__ . '/../core.php';
require_method('POST');

$me = require_auth();
$clientId = (string) param('clientId') ?: null;
$audience = param('audience') === 'close_friends' ? 'close_friends' : 'public';
$mediaPath = (string) param('media');

if ($mediaPath === '' && param('image') === '') {
    fail('Please select an image or a video for your story');
}
if ($clientId !== null) {
    $existing = q('SELECT id FROM stories WHERE client_id = ?', [$clientId])->fetchColumn();
    if ($existing) {
        respond(['storyId' => (string) $existing]);
    }
}

if ($mediaPath !== '') {
    $upload = claim_upload($mediaPath, $me, ['image', 'video']);
    $media = $upload['name'];
    $mediaType = $upload['media_type'];
} else {
    $media = save_image(param('image'));
    $mediaType = 'image';
}

q('INSERT INTO stories (client_id, user_id, image, media_type, text_label, audience, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)',
    [$clientId, $me, $media, $mediaType, (string) param('textLabel'), $audience, now_ms()]);

respond(['storyId' => (string) db()->lastInsertId()]);
