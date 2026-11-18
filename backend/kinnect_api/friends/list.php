<?php
// GET ?user_id=   (defaults to the signed-in user)
// Returns that user's friends.
require __DIR__ . '/../core.php';

$me = require_auth();
$targetId = int_param('user_id') ?: $me;

$rows = q('SELECT u.* FROM friends f JOIN users u ON u.id = f.friend_id
           WHERE f.user_id = ? ORDER BY u.name', [$targetId])->fetchAll();

respond(array_map(fn($row) => user_with_relation($row, $me), $rows));
