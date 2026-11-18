<?php
// POST {message_id} — toggles the heart reaction on a message in one of your chats.
require __DIR__ . '/../core.php';
require __DIR__ . '/common.php';
require_method('POST');

$me = require_auth();
$row = own_chat_message(int_param('message_id'), $me);

q('UPDATE messages SET heart_reaction = 1 - heart_reaction WHERE id = ?', [(int) $row['id']]);
respond(message_json(q('SELECT * FROM messages WHERE id = ?', [(int) $row['id']])->fetch()));
