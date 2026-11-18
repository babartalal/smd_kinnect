<?php
// POST {email, password}  (email may also be the account's phone number)
// Returns {token, user}.
require __DIR__ . '/../core.php';
require_method('POST');

$login    = strtolower((string) param('email'));
$password = (string) (input()['password'] ?? '');
if ($login === '' || $password === '') {
    fail('Please enter your email and password');
}

$row = q('SELECT * FROM users WHERE email = ? OR (phone <> "" AND phone = ?) LIMIT 1', [$login, $login])->fetch();
if (!$row || !password_verify($password, $row['password_hash'])) {
    fail('Incorrect email or password', 401);
}

$userId = (int) $row['id'];
q('UPDATE users SET is_online = 1, last_seen = ? WHERE id = ?', [now_ms(), $userId]);

respond([
    'token' => issue_token($userId),
    'user'  => user_json(fetch_user_row($userId), true),
]);
