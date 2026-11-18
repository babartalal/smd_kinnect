<?php
// GET ?user_id=   (defaults to the signed-in user)
// Returns the profile plus friendState, friendCount and mutualCount.
require __DIR__ . '/../core.php';

$me = require_auth();
$targetId = int_param('user_id') ?: $me;

$row = fetch_user_row($targetId);
if (!$row) {
    fail('User not found', 404);
}
respond(user_with_relation($row, $me));
