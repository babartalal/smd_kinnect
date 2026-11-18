<?php
// POST {email}
// Emails a 6-digit password reset code that is valid for 15 minutes.
// On a stock XAMPP install PHP's mail() does not send real email: it writes
// the message to C:\xampp\mailoutput, so look there for the code while testing.
require __DIR__ . '/../core.php';
require_method('POST');

$email = strtolower((string) param('email'));
if (!filter_var($email, FILTER_VALIDATE_EMAIL)) {
    fail('Please enter a valid email address');
}

$row = q('SELECT id FROM users WHERE email = ?', [$email])->fetch();
if ($row) {
    $code = (string) random_int(100000, 999999);
    q('UPDATE users SET reset_code_hash = ?, reset_code_expires = ?, reset_attempts = 0 WHERE id = ?',
        [password_hash($code, PASSWORD_DEFAULT), now_ms() + 15 * 60 * 1000, (int) $row['id']]);
    @mail($email, 'Your Kinnect password reset code',
        "Your Kinnect password reset code is $code\n\nIt expires in 15 minutes. If you did not ask for it, ignore this email.",
        'From: Kinnect <no-reply@kinnect.local>');
}

// Same answer whether or not the account exists, so emails cannot be probed.
respond(['sent' => true]);
