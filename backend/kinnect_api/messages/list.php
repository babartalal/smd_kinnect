<?php
// GET ?recipient_id=
// The conversation between the signed-in user and recipient_id, oldest
// first (latest 300 messages). Opening it marks their messages as read.
require __DIR__ . '/../core.php';
require __DIR__ . '/common.php';

$me = require_auth();
$other = int_param('recipient_id');
if (!$other || $other === $me) {
    fail('Recipient not found', 404);
}
$chatId = chat_id($me, $other);

q('UPDATE messages SET is_read = 1 WHERE chat_id = ? AND recipient_id = ? AND is_read = 0', [$chatId, $me]);

$rows = q('SELECT * FROM (SELECT * FROM messages WHERE chat_id = ? ORDER BY created_at DESC, id DESC LIMIT 300) recent
           ORDER BY created_at, id', [$chatId])->fetchAll();

respond(array_map('message_json', $rows));
