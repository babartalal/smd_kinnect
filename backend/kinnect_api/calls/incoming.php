<?php
// GET — the call currently ringing the signed-in user, or null.
// The app polls this while it is open.
require __DIR__ . '/../core.php';
require __DIR__ . '/common.php';

$me = require_auth();
expire_stale_calls();

$row = q('SELECT * FROM calls WHERE recipient_id = ? AND status = "ringing"', [$me])->fetch();
respond($row ? call_json($row) : null);
