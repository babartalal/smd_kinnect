<?php
// POST {fcmToken?}
// Called by the app every few seconds while it is open. require_auth()
// refreshes the user's "online" time; an FCM device token can ride along.
require __DIR__ . '/../core.php';
require_method('POST');

$me = require_auth();
$fcmToken = (string) param('fcmToken');
if ($fcmToken !== '') {
    // A device belongs to whoever signed in on it last.
    q('UPDATE users SET fcm_token = "" WHERE fcm_token = ? AND id <> ?', [$fcmToken, $me]);
    q('UPDATE users SET fcm_token = ? WHERE id = ?', [$fcmToken, $me]);
}

respond(['serverTime' => now_ms()]);
