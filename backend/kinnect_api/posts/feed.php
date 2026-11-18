<?php
// GET — home feed: the newest posts by the signed-in user and their friends.
// Each post carries its author, image paths, reaction count and my reaction.
require __DIR__ . '/../core.php';

$me = require_auth();
$authorIds = array_merge([$me], friend_ids($me));
$in = implode(',', array_fill(0, count($authorIds), '?'));

$posts = q("SELECT * FROM posts WHERE author_id IN ($in) ORDER BY created_at DESC LIMIT 100", $authorIds)->fetchAll();

$authors = [];
$result = [];
foreach ($posts as $post) {
    $postId = (int) $post['id'];
    $authorId = (int) $post['author_id'];
    if (!isset($authors[$authorId])) {
        $authors[$authorId] = author_json($authorId);
    }
    $images = q('SELECT image FROM post_images WHERE post_id = ? ORDER BY position', [$postId])->fetchAll(PDO::FETCH_COLUMN);
    $myReaction = q('SELECT reaction FROM post_reactions WHERE post_id = ? AND user_id = ?', [$postId, $me])->fetchColumn();

    $result[] = [
        'postId'        => (string) $postId,
        'authorId'      => (string) $authorId,
        'text'          => $post['text'],
        'timestamp'     => (int) $post['created_at'],
        'images'        => array_map('image_path', $images),
        'author'        => $authors[$authorId],
        'reactionCount' => (int) q('SELECT COUNT(*) FROM post_reactions WHERE post_id = ?', [$postId])->fetchColumn(),
        'myReaction'    => $myReaction ?: '',
        'commentCount'  => (int) q('SELECT COUNT(*) FROM comments WHERE post_id = ?', [$postId])->fetchColumn(),
    ];
}

respond($result);
