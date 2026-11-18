<?php
// POST {message_id} — delete your own message within 5 minutes of sending.
// Like the original app, the message stays in the chat as a placeholder.
require __DIR__ . '/../core.php';
require __DIR__ . '/common.php';
require_method('POST');

$me = require_auth();
$row = own_chat_message(int_param('message_id'), $me);
require_editable($row, $me);

delete_image($row['image']);
q('UPDATE messages SET text = ?, image = "" WHERE id = ?', ['This message was deleted.', (int) $row['id']]);
respond(message_json(q('SELECT * FROM messages WHERE id = ?', [(int) $row['id']])->fetch()));
