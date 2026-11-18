<?php
// GET — users who sent the signed-in user a friend request, newest first.
// Each user also carries `timestamp`: when the request was sent (ms), which
// the Notifications screen shows as "5m", "2h" ...
require __DIR__ . '/../core.php';

$me = require_auth();
// requested_at, because users has its own created_at column.
$rows = q('SELECT u.*, r.created_at AS requested_at FROM friend_requests r JOIN users u ON u.id = r.sender_id
           WHERE r.receiver_id = ? ORDER BY r.created_at DESC', [$me])->fetchAll();

respond(array_map(function ($row) use ($me) {
    $user = user_with_relation($row, $me);
    $user['timestamp'] = (int) $row['requested_at'];
    return $user;
}, $rows));
