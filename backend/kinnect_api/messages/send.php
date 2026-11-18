<?php
// POST {recipient_id, text, image?, replyToMsgId?, isSystemMessage?, clientId?}
// Sends a chat message to a friend and pushes a notification to them.
require __DIR__ . '/../core.php';
require __DIR__ . '/common.php';
require_method('POST');

$me = require_auth();
$other = int_param('recipient_id');
$text = (string) param('text');
$imageData = param('image');
$isSystem = (bool) param('isSystemMessage', false);
$clientId = (string) param('clientId') ?: null;

if ($text === '' && $imageData === '') {
    fail('Message is empty');
}
if (!are_friends($me, $other)) {
    fail('You can only message your friends', 403);
}
$chatId = chat_id($me, $other);

if ($clientId !== null) {
    $existing = q('SELECT * FROM messages WHERE client_id = ?', [$clientId])->fetch();
    if ($existing) {
        respond(message_json($existing));
    }
}

// The quoted text is looked up here so it always matches the real message.
$replyTo = int_param('replyToMsgId') ?: null;
$replyText = '';
if ($replyTo !== null) {
    $quoted = q('SELECT text, image FROM messages WHERE id = ? AND chat_id = ?', [$replyTo, $chatId])->fetch();
    if ($quoted) {
        $replyText = $quoted['text'] !== '' ? mb_substr($quoted['text'], 0, 200) : '📷 Photo';
    } else {
        $replyTo = null;
    }
}

q('INSERT INTO messages (client_id, chat_id, sender_id, recipient_id, text, image, reply_to_msg_id, reply_to_text, is_system, created_at)
   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)', [
    $clientId, $chatId, $me, $other, $text, $isSystem ? '' : save_image($imageData),
    $replyTo, $replyText, $isSystem ? 1 : 0, now_ms(),
]);
$message = q('SELECT * FROM messages WHERE id = ?', [(int) db()->lastInsertId()])->fetch();

$sender = fetch_user_row($me);
$title = $isSystem ? 'Screenshot Alert' : ($sender['first_name'] ?: display_name($sender));
send_push($other, $title, $message['image'] !== '' ? '📷 Photo' : $text);

respond(message_json($message));
