<?php
// POST {action}   action: accept | decline
// Answers the call that is ringing the signed-in user. The call history
// (call_logs) records it as "answered" or "declined".
require __DIR__ . '/../core.php';
require __DIR__ . '/common.php';
require_method('POST');

$me = require_auth();
$row = q('SELECT * FROM calls WHERE recipient_id = ?', [$me])->fetch();
if (!$row) {
    fail('The call has already ended', 404);
}

if (param('action') === 'accept') {
    q('UPDATE calls SET status = "accepted" WHERE recipient_id = ?', [$me]);
    log_call_answered((int) $row['caller_id'], $me);
    $row['status'] = 'accepted';
    respond(call_json($row));
}

q('DELETE FROM calls WHERE recipient_id = ?', [$me]);
log_call_declined((int) $row['caller_id'], $me);
respond(null);
