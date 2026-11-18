<?php
// GET ?story_id=            one story
// GET ?user_id=             that user's latest story (defaults to me)
// Only your own stories and your friends' stories can be opened.
require __DIR__ . '/../core.php';
require __DIR__ . '/common.php';

$me = require_auth();
$storyId = int_param('story_id');

if ($storyId) {
    $row = q('SELECT * FROM stories WHERE id = ?', [$storyId])->fetch();
} else {
    $row = q('SELECT * FROM stories WHERE user_id = ? ORDER BY created_at DESC LIMIT 1',
        [int_param('user_id') ?: $me])->fetch();
}

if (!$row) {
    fail('Story not found', 404);
}
$ownerId = (int) $row['user_id'];
if ($ownerId !== $me && !are_friends($me, $ownerId)) {
    fail('You can only view stories from your friends', 403);
}

respond(story_json($row));
