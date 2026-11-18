<?php
// POST {comment_id} — toggles the signed-in user's like on a comment.
require __DIR__ . '/../core.php';
require_method('POST');

$me = require_auth();
$commentId = int_param('comment_id');

if (!q('SELECT 1 FROM comments WHERE id = ?', [$commentId])->fetch()) {
    fail('Comment not found', 404);
}

$removed = q('DELETE FROM comment_likes WHERE comment_id = ? AND user_id = ?', [$commentId, $me])->rowCount();
if (!$removed) {
    q('INSERT IGNORE INTO comment_likes (comment_id, user_id) VALUES (?, ?)', [$commentId, $me]);
}

respond([
    'likedByMe' => !$removed,
    'likeCount' => (int) q('SELECT COUNT(*) FROM comment_likes WHERE comment_id = ?', [$commentId])->fetchColumn(),
]);
