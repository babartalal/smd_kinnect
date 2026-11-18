<?php
// POST {post_id, text, image?, replyToCommentId?, replyToAuthorName?, clientId?}
require __DIR__ . '/../core.php';
require_method('POST');

$me = require_auth();
$postId = int_param('post_id');
$text = (string) param('text');
$imageData = param('image');
$replyTo = int_param('replyToCommentId') ?: null;
$clientId = (string) param('clientId') ?: null;

if ($text === '' && $imageData === '') {
    fail('Please enter a comment or attach an image');
}
if (!q('SELECT 1 FROM posts WHERE id = ?', [$postId])->fetch()) {
    fail('Post not found', 404);
}
if ($replyTo !== null && !q('SELECT 1 FROM comments WHERE id = ? AND post_id = ?', [$replyTo, $postId])->fetch()) {
    fail('The comment you are replying to no longer exists', 404);
}

if ($clientId !== null) {
    $existing = q('SELECT id FROM comments WHERE client_id = ?', [$clientId])->fetchColumn();
    if ($existing) {
        respond(['commentId' => (string) $existing]);
    }
}

q('INSERT INTO comments (client_id, post_id, author_id, text, image, reply_to_comment_id, reply_to_author_name, created_at)
   VALUES (?, ?, ?, ?, ?, ?, ?, ?)', [
    $clientId, $postId, $me, $text, save_image($imageData), $replyTo,
    $replyTo !== null ? (string) param('replyToAuthorName') : '', now_ms(),
]);

respond(['commentId' => (string) db()->lastInsertId()]);
