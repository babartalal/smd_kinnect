<?php
// POST {peer_id} — hangs up the call between the signed-in user and peer_id.
require __DIR__ . '/../core.php';
require_method('POST');

$me = require_auth();
$peer = int_param('peer_id');

q('DELETE FROM calls WHERE (recipient_id = ? AND caller_id = ?) OR (recipient_id = ? AND caller_id = ?)',
    [$me, $peer, $peer, $me]);
respond(['ended' => true]);
