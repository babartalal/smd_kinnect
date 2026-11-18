<?php
// GET ?q=text&filter=everyone|friends|requests
// Finds other users whose username or name contains the text.
//   everyone (default) – any user; an empty text returns nothing.
//   friends            – only the signed-in user's friends.
//   requests           – only users with a pending friend request to or from
//                        the signed-in user.
// With friends or requests an empty text returns all of them.
require __DIR__ . '/../core.php';

$me = require_auth();
$query = (string) param('q');
$filter = (string) param('filter', 'everyone');
if (!in_array($filter, ['friends', 'requests'], true)) {
    $filter = 'everyone';
}
if ($query === '' && $filter === 'everyone') {
    respond([]);
}

// Escape LIKE wildcards so the text is matched literally.
$like = '%' . addcslashes($query, '\\%_') . '%';
$sql = 'SELECT u.* FROM users u
        WHERE u.id <> ? AND (u.username LIKE ? OR u.first_name LIKE ? OR u.last_name LIKE ? OR u.name LIKE ?)';
$params = [$me, $like, $like, $like, $like];

if ($filter === 'friends') {
    $sql .= ' AND u.id IN (SELECT friend_id FROM friends WHERE user_id = ?)';
    $params[] = $me;
} elseif ($filter === 'requests') {
    $sql .= ' AND (u.id IN (SELECT sender_id FROM friend_requests WHERE receiver_id = ?)
                OR u.id IN (SELECT receiver_id FROM friend_requests WHERE sender_id = ?))';
    array_push($params, $me, $me);
}

$rows = q($sql . ' ORDER BY u.name LIMIT 50', $params)->fetchAll();

respond(array_map(fn($row) => user_with_relation($row, $me), $rows));
