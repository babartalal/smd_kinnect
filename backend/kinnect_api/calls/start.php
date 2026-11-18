<?php
// POST {recipient_id, type}   type: audio | video
// Starts ringing a friend. Returns the Agora channel both sides should join.
require __DIR__ . '/../core.php';
require __DIR__ . '/common.php';
require_method('POST');

$me = require_auth();
$other = int_param('recipient_id');
$type = param('type') === 'video' ? 'video' : 'audio';

if (!are_friends($me, $other)) {
    fail('You can only call your friends', 403);
}
expire_stale_calls();
if (q('SELECT 1 FROM calls WHERE recipient_id = ? AND caller_id <> ?', [$other, $me])->fetch()) {
    fail('This person is on another call', 409);
}

$caller = fetch_user_row($me);
$callerName = $caller['first_name'] ?: display_name($caller);
$channel = 'kinnect_' . chat_id($me, $other);

q('REPLACE INTO calls (recipient_id, caller_id, caller_name, channel_name, type, status, created_at)
   VALUES (?, ?, ?, ?, ?, "ringing", ?)', [$other, $me, $callerName, $channel, $type, now_ms()]);

send_push($other, 'Incoming ' . ucfirst($type) . ' Call', "$callerName is calling you...");

respond(call_json(q('SELECT * FROM calls WHERE recipient_id = ?', [$other])->fetch()));
