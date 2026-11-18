<?php
// POST {firstName, lastName, email, password, birthday, gender}
// Creates an account and signs it in. Returns {token, user}.
require __DIR__ . '/../core.php';
require_method('POST');

$firstName = (string) param('firstName');
$lastName  = (string) param('lastName');
$email     = strtolower((string) param('email'));
$password  = (string) (input()['password'] ?? '');

if ($firstName === '' || $lastName === '') {
    fail('Please enter your first and last name');
}
if (!filter_var($email, FILTER_VALIDATE_EMAIL)) {
    fail('Please enter a valid email address');
}
if (strlen($password) < 6) {
    fail('Password must be at least 6 characters');
}
if (q('SELECT 1 FROM users WHERE email = ?', [$email])->fetch()) {
    fail('The email address is already in use by another account', 409);
}

q('INSERT INTO users (first_name, last_name, name, birthday, gender, email, password_hash, is_online, last_seen, created_at)
   VALUES (?, ?, ?, ?, ?, ?, ?, 1, ?, ?)', [
    $firstName, $lastName, trim("$firstName $lastName"),
    (string) param('birthday'), (string) param('gender', 'Not Specified'),
    $email, password_hash($password, PASSWORD_DEFAULT), now_ms(), now_ms(),
]);
$userId = (int) db()->lastInsertId();

respond([
    'token' => issue_token($userId),
    'user'  => user_json(fetch_user_row($userId), true),
]);
