<?php
// GET — one conversation row per friend, most recent first. Each row has
// the friend's profile, the last message preview (text, or "📷 Photo",
// "🎥 Video", "📎 <file name>" for an attachment) and whether it is unread.
require __DIR__ . '/../core.php';
require __DIR__ . '/../messages/common.php';

$me = require_auth();
$friends = q('SELECT u.* FROM friends f JOIN users u ON u.id = f.friend_id WHERE f.user_id = ?', [$me])->fetchAll();

$result = [];
foreach ($friends as $friend) {
    $friendId = (int) $friend['id'];
    $chatId = chat_id($me, $friendId);
    $last = q('SELECT text, image, media_type, file_name, created_at FROM messages WHERE chat_id = ? ORDER BY created_at DESC, id DESC LIMIT 1',
        [$chatId])->fetch();

    $result[] = [
        'recipientId' => (string) $friendId,
        'lastMessage' => !$last ? 'Start a conversation' : message_preview($last),
        'timestamp'   => $last ? (int) $last['created_at'] : 0,
        'unread'      => (bool) q('SELECT 1 FROM messages WHERE chat_id = ? AND recipient_id = ? AND is_read = 0 LIMIT 1',
            [$chatId, $me])->fetch(),
        'user'        => user_json($friend),
    ];
}

usort($result, fn($a, $b) => $b['timestamp'] <=> $a['timestamp']);
respond($result);
