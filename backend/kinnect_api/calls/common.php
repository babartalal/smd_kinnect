<?php
// Shared by the calls endpoints. Calls are signalled through the calls table
// (who is ringing whom); the audio and video themselves travel through Agora.

const CALL_RING_TIMEOUT_MS = 45000;

/** Forget calls that rang without an answer. */
function expire_stale_calls(): void
{
    q('DELETE FROM calls WHERE status = "ringing" AND created_at < ?', [now_ms() - CALL_RING_TIMEOUT_MS]);
}

function call_json(array $row): array
{
    return [
        'callerId'    => (string) $row['caller_id'],
        'recipientId' => (string) $row['recipient_id'],
        'callerName'  => $row['caller_name'],
        'channelName' => $row['channel_name'],
        'type'        => $row['type'],
        'status'      => $row['status'],
    ];
}
