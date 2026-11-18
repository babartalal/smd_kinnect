<?php
// POST {message_id, text} — edit your own message within 5 minutes of sending.
require __DIR__ . '/../core.php';
require __DIR__ . '/common.php';
require_method('POST');

$me = require_auth();
$text = (string) param('text');
if ($text === '') {
    fail('Message is empty');
}

$row = own_chat_message(int_param('message_id'), $me);
require_editable($row, $me);

q('UPDATE messages SET text = ?, is_edited = 1 WHERE id = ?', [$text, (int) $row['id']]);
respond(message_json(q('SELECT * FROM messages WHERE id = ?', [(int) $row['id']])->fetch()));
