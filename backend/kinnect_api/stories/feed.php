<?php
// GET — stories from the last 24 hours by the signed-in user and their
// friends, newest first, each with its author.
require __DIR__ . '/../core.php';
require __DIR__ . '/common.php';

$me = require_auth();
$userIds = array_merge([$me], friend_ids($me));
$in = implode(',', array_fill(0, count($userIds), '?'));

$rows = q("SELECT * FROM stories WHERE user_id IN ($in) AND created_at > ? ORDER BY created_at DESC",
    array_merge($userIds, [now_ms() - STORY_LIFETIME_MS]))->fetchAll();

$authors = [];
$result = [];
foreach ($rows as $row) {
    $authorId = (int) $row['user_id'];
    if (!isset($authors[$authorId])) {
        $author = user_json(fetch_user_row($authorId));
        unset($author['isOnline']); // keeps the feed's etag stable while people come and go
        $authors[$authorId] = $author;
    }
    $result[] = story_json($row) + ['author' => $authors[$authorId]];
}

respond($result);
