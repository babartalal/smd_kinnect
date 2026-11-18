<?php
// POST {recipient_id, text, replyToMsgId?, isSystemMessage?, clientId?} plus at most one attachment:
//   media:     "uploads/<name>" exactly as media/upload.php returned it (image, video or file)
//   mediaType: image | video | file  (informational; the server uses what it recorded at upload)
//   fileName:  name to show for a file (defaults to the uploaded file's name)
//   image:     a Base64 picture (older format, still used by the offline queue)
// Sends a chat message to a friend and pushes a notification to them.
require __DIR__ . '/../core.php';
require __DIR__ . '/common.php';
require_method('POST');

$me = require_auth();
$other = int_param('recipient_id');
$text = (string) param('text');
$imageData = param('image');
$mediaPath = (string) param('media');
$isSystem = (bool) param('isSystemMessage', false);
$clientId = (string) param('clientId') ?: null;

if ($text === '' && $imageData === '' && $mediaPath === '') {
    fail('Message is empty');
}
if (!are_friends($me, $other)) {
    fail('You can only message your friends', 403);
}
$chatId = chat_id($me, $other);

if ($clientId !== null) {
    $existing = q('SELECT * FROM messages WHERE client_id = ?', [$clientId])->fetch();
    if ($existing) {
        respond(message_json($existing));
    }
}

// The quoted text is looked up here so it always matches the real message.
$replyTo = int_param('replyToMsgId') ?: null;
$replyText = '';
if ($replyTo !== null) {
    $quoted = q('SELECT text, image, media_type, file_name FROM messages WHERE id = ? AND chat_id = ?',
        [$replyTo, $chatId])->fetch();
    if ($quoted) {
        $replyText = mb_substr($quoted['text'] !== '' ? $quoted['text'] : message_preview($quoted), 0, 200);
    } else {
        $replyTo = null;
    }
}

// The attachment: a file uploaded through media/upload.php, or a Base64 picture.
$media = '';
$mediaType = '';
$fileName = '';
if (!$isSystem && $mediaPath !== '') {
    $upload = claim_upload($mediaPath, $me);
    $media = $upload['name'];
    $mediaType = $upload['media_type'];
    if ($mediaType === 'file') {
        $fileName = display_file_name(param('fileName')) ?: $upload['file_name'];
    }
} elseif (!$isSystem && $imageData !== '') {
    $media = save_image($imageData);
    $mediaType = $media !== '' ? 'image' : '';
}

q('INSERT INTO messages (client_id, chat_id, sender_id, recipient_id, text, image, media_type, file_name,
                         reply_to_msg_id, reply_to_text, is_system, created_at)
   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)', [
    $clientId, $chatId, $me, $other, $text, $media, $mediaType, $fileName,
    $replyTo, $replyText, $isSystem ? 1 : 0, now_ms(),
]);
$message = q('SELECT * FROM messages WHERE id = ?', [(int) db()->lastInsertId()])->fetch();

$sender = fetch_user_row($me);
$title = $isSystem ? 'Screenshot Alert' : ($sender['first_name'] ?: display_name($sender));
send_push($other, $title, message_preview($message));

respond(message_json($message));
