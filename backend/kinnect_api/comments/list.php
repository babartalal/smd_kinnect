<?php
// GET ?post_id=
// All comments on a post, oldest first, each with its author and like info.
require __DIR__ . '/../core.php';

$me = require_auth();
$postId = int_param('post_id');

$rows = q('SELECT * FROM comments WHERE post_id = ? ORDER BY created_at', [$postId])->fetchAll();

$authors = [];
$result = [];
foreach ($rows as $row) {
    $commentId = (int) $row['id'];
    $authorId = (int) $row['author_id'];
    if (!isset($authors[$authorId])) {
        $authors[$authorId] = author_json($authorId);
    }
    $result[] = [
        'commentId'         => (string) $commentId,
        'postId'            => (string) $row['post_id'],
        'authorId'          => (string) $authorId,
        'text'              => $row['text'],
        'timestamp'         => (int) $row['created_at'],
        'image'             => image_path($row['image']),
        'replyToCommentId'  => $row['reply_to_comment_id'] ? (string) $row['reply_to_comment_id'] : '',
        'replyToAuthorName' => $row['reply_to_author_name'],
        'author'            => $authors[$authorId],
        'likeCount'         => (int) q('SELECT COUNT(*) FROM comment_likes WHERE comment_id = ?', [$commentId])->fetchColumn(),
        'likedByMe'         => (bool) q('SELECT 1 FROM comment_likes WHERE comment_id = ? AND user_id = ?', [$commentId, $me])->fetch(),
    ];
}

respond($result);
