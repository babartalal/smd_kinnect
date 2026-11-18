<?php
// Shared by the messages endpoints.

const MESSAGE_EDIT_WINDOW_MS = 5 * 60 * 1000;

function message_json(array $row): array
{
    return [
        'messageId'       => (string) $row['id'],
        'chatId'          => $row['chat_id'],
        'senderId'        => (string) $row['sender_id'],
        'text'            => $row['text'],
        'image'           => image_path($row['image']),
        'timestamp'       => (int) $row['created_at'],
        'isEdited'        => (bool) $row['is_edited'],
        'replyToMsgId'    => $row['reply_to_msg_id'] ? (string) $row['reply_to_msg_id'] : '',
        'replyToText'     => $row['reply_to_text'],
        'heartReaction'   => (bool) $row['heart_reaction'],
        'isSystemMessage' => (bool) $row['is_system'],
    ];
}

/** Load a message the signed-in user takes part in, or stop with 404. */
function own_chat_message(int $messageId, int $me): array
{
    $row = q('SELECT * FROM messages WHERE id = ? AND (sender_id = ? OR recipient_id = ?)',
        [$messageId, $me, $me])->fetch();
    if (!$row) {
        fail('Message not found', 404);
    }
    return $row;
}

/** Only the sender may change a message, and only for five minutes. */
function require_editable(array $row, int $me): void
{
    if ((int) $row['sender_id'] !== $me || (int) $row['is_system'] === 1) {
        fail('You can only change your own messages', 403);
    }
    if (now_ms() - (int) $row['created_at'] > MESSAGE_EDIT_WINDOW_MS) {
        fail('Messages can only be edited or deleted within 5 minutes', 403);
    }
}
