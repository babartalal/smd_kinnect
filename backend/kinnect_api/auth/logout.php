<?php
// POST — ends this session, marks the user offline and forgets the device's push token.
require __DIR__ . '/../core.php';
require_method('POST');

$userId = require_auth();
q('DELETE FROM auth_tokens WHERE token_hash = ?', [hash('sha256', request_token())]);
q('UPDATE users SET is_online = 0, fcm_token = "" WHERE id = ?', [$userId]);

respond(['loggedOut' => true]);
