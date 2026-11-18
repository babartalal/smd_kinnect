<?php
// GET ?peer_id=
// The call between the signed-in user and peer_id, in either direction,
// or null once it has ended. Both sides poll this during a call.
require __DIR__ . '/../core.php';
require __DIR__ . '/common.php';

$me = require_auth();
$peer = int_param('peer_id');
expire_stale_calls();

$row = q('SELECT * FROM calls WHERE (recipient_id = ? AND caller_id = ?) OR (recipient_id = ? AND caller_id = ?)',
    [$me, $peer, $peer, $me])->fetch();
respond($row ? call_json($row) : null);
