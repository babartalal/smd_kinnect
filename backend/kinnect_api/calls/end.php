<?php
// POST {peer_id} — hangs up the call between the signed-in user and peer_id.
// In the call history (call_logs) a call that was answered becomes
// "completed" with its duration; one that was still ringing becomes "missed"
// (or "declined" when it is the recipient who ends it).
require __DIR__ . '/../core.php';
require __DIR__ . '/common.php';
require_method('POST');

$me = require_auth();
$peer = int_param('peer_id');

q('DELETE FROM calls WHERE (recipient_id = ? AND caller_id = ?) OR (recipient_id = ? AND caller_id = ?)',
    [$me, $peer, $peer, $me]);
log_call_ended($me, $peer, $me);
respond(['ended' => true]);
