<?php
// POST {name, username, bio, website, email, phone, gender, profilePic?, coverPhoto?}
// Updates the signed-in user's profile. The two photos are optional Base64
// images; when omitted the current photo is kept.
require __DIR__ . '/../core.php';
require_method('POST');

$me = require_auth();
$current = fetch_user_row($me);

$username = (string) param('username');
$bio      = (string) param('bio');
$email    = strtolower((string) param('email', $current['email']));
if ($username === '') {
    fail('Username is required');
}
if ($bio === '') {
    fail('Bio is required');
}
if (!filter_var($email, FILTER_VALIDATE_EMAIL)) {
    fail('Please enter a valid email address');
}
if (q('SELECT 1 FROM users WHERE username = ? AND id <> ?', [$username, $me])->fetch()) {
    fail("Username '$username' is already taken. Please choose another.", 409);
}
if (q('SELECT 1 FROM users WHERE email = ? AND id <> ?', [$email, $me])->fetch()) {
    fail('That email address is already used by another account', 409);
}

$profilePic = $current['profile_pic'];
$coverPhoto = $current['cover_photo'];
if (param('profilePic') !== '') {
    $profilePic = save_image(param('profilePic'));
    delete_image($current['profile_pic']);
}
if (param('coverPhoto') !== '') {
    $coverPhoto = save_image(param('coverPhoto'));
    delete_image($current['cover_photo']);
}

q('UPDATE users SET name = ?, username = ?, bio = ?, website = ?, email = ?, phone = ?, gender = ?,
          profile_pic = ?, cover_photo = ? WHERE id = ?', [
    (string) param('name', $current['name']), $username, $bio, (string) param('website'), $email,
    (string) param('phone'), (string) param('gender', $current['gender']), $profilePic, $coverPhoto, $me,
]);

respond(user_json(fetch_user_row($me), true));
