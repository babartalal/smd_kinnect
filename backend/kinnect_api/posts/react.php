<?php
// POST {post_id, reaction}
// reaction: like | love | haha | wow | sad | angry
// Choosing the reaction you already have removes it; any other one replaces it.
require __DIR__ . '/../core.php';
require_method('POST');

$me = require_auth();
$postId = int_param('post_id');
$reaction = (string) param('reaction');

if (!in_array($reaction, ['like', 'love', 'haha', 'wow', 'sad', 'angry'], true)) {
    fail('Unknown reaction');
}
if (!q('SELECT 1 FROM posts WHERE id = ?', [$postId])->fetch()) {
    fail('Post not found', 404);
}

$current = q('SELECT reaction FROM post_reactions WHERE post_id = ? AND user_id = ?', [$postId, $me])->fetchColumn();
if ($current === $reaction) {
    q('DELETE FROM post_reactions WHERE post_id = ? AND user_id = ?', [$postId, $me]);
    $reaction = '';
} else {
    q('INSERT INTO post_reactions (post_id, user_id, reaction) VALUES (?, ?, ?)
       ON DUPLICATE KEY UPDATE reaction = VALUES(reaction)', [$postId, $me, $reaction]);
}

respond([
    'myReaction'    => $reaction,
    'reactionCount' => (int) q('SELECT COUNT(*) FROM post_reactions WHERE post_id = ?', [$postId])->fetchColumn(),
]);
