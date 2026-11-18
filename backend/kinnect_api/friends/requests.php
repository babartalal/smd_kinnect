<?php
// GET — users who sent the signed-in user a friend request, newest first.
require __DIR__ . '/../core.php';

$me = require_auth();
$rows = q('SELECT u.* FROM friend_requests r JOIN users u ON u.id = r.sender_id
           WHERE r.receiver_id = ? ORDER BY r.created_at DESC', [$me])->fetchAll();

respond(array_map(fn($row) => user_with_relation($row, $me), $rows));
