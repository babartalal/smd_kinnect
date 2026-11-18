<?php
// POST {email, code, password}
// Sets a new password using the code from request_reset.php and signs out
// every existing session of that account.
require __DIR__ . '/../core.php';
require_method('POST');

$email    = strtolower((string) param('email'));
$code     = (string) param('code');
$password = (string) (input()['password'] ?? '');
if (strlen($password) < 6) {
    fail('Password must be at least 6 characters');
}

$row = q('SELECT id, reset_code_hash, reset_code_expires, reset_attempts FROM users WHERE email = ?', [$email])->fetch();
$invalid = 'The reset code is incorrect or has expired';
if (!$row || $row['reset_code_hash'] === '' || (int) $row['reset_code_expires'] < now_ms() || (int) $row['reset_attempts'] >= 5) {
    fail($invalid);
}
if (!password_verify($code, $row['reset_code_hash'])) {
    q('UPDATE users SET reset_attempts = reset_attempts + 1 WHERE id = ?', [(int) $row['id']]);
    fail($invalid);
}

q('UPDATE users SET password_hash = ?, reset_code_hash = "", reset_code_expires = 0, reset_attempts = 0 WHERE id = ?',
    [password_hash($password, PASSWORD_DEFAULT), (int) $row['id']]);
q('DELETE FROM auth_tokens WHERE user_id = ?', [(int) $row['id']]);

respond(['reset' => true]);
