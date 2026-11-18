<?php
// POST multipart/form-data: file=<the file>, clientId?
// Stores one image, video or document in uploads/ and answers with
//   {path: "uploads/<name>", mediaType: "image"|"video"|"file", fileName, size}
// The app then passes "path" as `media` to messages/send.php or
// stories/create.php. Allowed: jpg png webp, mp4 3gp webm mkv,
// pdf doc docx xls xlsx ppt pptx txt zip, up to MAX_UPLOAD_BYTES (config.php).
// The file is checked by its real content, and its stored name is generated
// here; the client's file name is only kept as a label to display.
require __DIR__ . '/../core.php';
require_method('POST');

$me = require_auth();
$stored = store_upload($_FILES['file'] ?? null);

// Remember who uploaded it, so only they can attach it (see claim_upload).
q('INSERT INTO media_uploads (name, user_id, media_type, file_name, size, created_at) VALUES (?, ?, ?, ?, ?, ?)',
    [$stored['name'], $me, $stored['mediaType'], $stored['fileName'], $stored['size'], now_ms()]);

respond([
    'path'      => image_path($stored['name']),
    'mediaType' => $stored['mediaType'],
    'fileName'  => $stored['fileName'],
    'size'      => $stored['size'],
]);
