<?php
// GET ?peer_id=   (peer_id is optional)
// The signed-in user's call history, newest first (at most 100 calls).
// With peer_id, only the calls between the signed-in user and that user.
// Each entry:
//   callId, user (the other person), direction: outgoing | incoming,
//   type: audio | video,
//   status: ringing | answered | missed | declined | completed,
//   startedAt, answeredAt, endedAt (ms; 0 when it has not happened),
//   durationSeconds (time talked; 0 unless the call was completed)
require __DIR__ . '/../core.php';
require __DIR__ . '/common.php';

$me = require_auth();
$peer = int_param('peer_id');
// Calls that rang out are marked as missed before the history is read.
expire_stale_calls();

// The log's own columns are renamed so they cannot clash with the columns of
// the joined user (the other side of the call).
$sql = 'SELECT l.id AS log_id, l.caller_id AS log_caller_id, l.type AS log_type, l.status AS log_status,
               l.started_at, l.answered_at, l.ended_at, l.duration_seconds, u.*
        FROM call_logs l
        JOIN users u ON u.id = IF(l.caller_id = ?, l.recipient_id, l.caller_id)
        WHERE (l.caller_id = ? OR l.recipient_id = ?)';
$params = [$me, $me, $me];
if ($peer > 0) {
    $sql .= ' AND (l.caller_id = ? OR l.recipient_id = ?)';
    array_push($params, $peer, $peer);
}
$sql .= ' ORDER BY l.started_at DESC, l.id DESC LIMIT 100';

$logs = [];
foreach (q($sql, $params)->fetchAll() as $row) {
    // Like feeds, leave out the online flag so the etag only changes when the history does.
    $user = user_json($row);
    unset($user['isOnline']);
    $logs[] = [
        'callId'          => (string) $row['log_id'],
        'user'            => $user,
        'direction'       => (int) $row['log_caller_id'] === $me ? 'outgoing' : 'incoming',
        'type'            => $row['log_type'],
        'status'          => $row['log_status'],
        'startedAt'       => (int) $row['started_at'],
        'answeredAt'      => (int) $row['answered_at'],
        'endedAt'         => (int) $row['ended_at'],
        'durationSeconds' => (int) $row['duration_seconds'],
    ];
}

respond($logs);
