<?php
// POST {user_id, action}
// action: send | cancel | accept | decline | unfriend
// Returns the resulting friendState between the two users.
require __DIR__ . '/../core.php';
require_method('POST');

$me = require_auth();
$other = int_param('user_id');
$action = (string) param('action');

if ($other === $me || !fetch_user_row($other)) {
    fail('User not found', 404);
}

switch ($action) {
    case 'send':
        if (are_friends($me, $other)) {
            break;
        }
        // If they already asked me, sending a request back simply accepts theirs.
        if (q('SELECT 1 FROM friend_requests WHERE sender_id = ? AND receiver_id = ?', [$other, $me])->fetch()) {
            make_friends($me, $other);
            break;
        }
        $inserted = q('INSERT IGNORE INTO friend_requests (sender_id, receiver_id, created_at) VALUES (?, ?, ?)',
            [$me, $other, now_ms()])->rowCount();
        if ($inserted) {
            send_push($other, 'New friend request', display_name(fetch_user_row($me)) . ' sent you a friend request');
        }
        break;

    case 'cancel':
        q('DELETE FROM friend_requests WHERE sender_id = ? AND receiver_id = ?', [$me, $other]);
        break;

    case 'accept':
        if (!q('SELECT 1 FROM friend_requests WHERE sender_id = ? AND receiver_id = ?', [$other, $me])->fetch()) {
            fail('There is no friend request from this user', 404);
        }
        make_friends($me, $other);
        send_push($other, 'Friend request accepted', display_name(fetch_user_row($me)) . ' accepted your friend request');
        break;

    case 'decline':
        q('DELETE FROM friend_requests WHERE sender_id = ? AND receiver_id = ?', [$other, $me]);
        break;

    case 'unfriend':
        q('DELETE FROM friends WHERE (user_id = ? AND friend_id = ?) OR (user_id = ? AND friend_id = ?)',
            [$me, $other, $other, $me]);
        break;

    default:
        fail('Unknown action');
}

respond(['friendState' => friend_state($me, $other)]);

function make_friends(int $a, int $b): void
{
    $pdo = db();
    $pdo->beginTransaction();
    q('INSERT IGNORE INTO friends (user_id, friend_id, created_at) VALUES (?, ?, ?), (?, ?, ?)',
        [$a, $b, now_ms(), $b, $a, now_ms()]);
    q('DELETE FROM friend_requests WHERE (sender_id = ? AND receiver_id = ?) OR (sender_id = ? AND receiver_id = ?)',
        [$a, $b, $b, $a]);
    $pdo->commit();
}
