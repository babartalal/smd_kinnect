<?php
// Shared by the calls endpoints. Calls are signalled through the calls table
// (who is ringing whom); the audio and video themselves travel through Agora.
//
// Every call is also written to call_logs (migration 003), which is the call
// history: the calls row disappears when the call ends, the log row stays.
// A log row is "open" while its ended_at is NULL.

const CALL_RING_TIMEOUT_MS = 45000;

/** Forget calls that rang without an answer; their log rows become "missed". */
function expire_stale_calls(): void
{
    $cutoff = now_ms() - CALL_RING_TIMEOUT_MS;
    log_call_safely(function () use ($cutoff) {
        q('UPDATE call_logs l
           JOIN calls c ON c.caller_id = l.caller_id AND c.recipient_id = l.recipient_id
           SET l.status = "missed", l.ended_at = ?
           WHERE c.status = "ringing" AND c.created_at < ?
             AND l.status = "ringing" AND l.ended_at IS NULL', [now_ms(), $cutoff]);
    });
    q('DELETE FROM calls WHERE status = "ringing" AND created_at < ?', [$cutoff]);
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

// ---------- call history (call_logs) ----------

/**
 * The log must never break a call: if writing it fails (for example the
 * call_logs migration has not been run yet) the error is noted in the PHP
 * log and the signalling carries on as before.
 */
function log_call_safely(callable $write): void
{
    try {
        $write();
    } catch (Throwable $e) {
        error_log('Kinnect call log failed: ' . $e->getMessage());
    }
}

/** A new call starts ringing: add its row to the history. */
function log_call_started(int $caller, int $recipient, string $type): void
{
    log_call_safely(function () use ($caller, $recipient, $type) {
        // start.php replaces any earlier call from this caller to this person,
        // so a log row still open for that earlier call is closed first.
        close_open_call_logs($caller, $recipient, $caller);
        q('INSERT INTO call_logs (caller_id, recipient_id, type, status, started_at) VALUES (?, ?, ?, "ringing", ?)',
            [$caller, $recipient, $type, now_ms()]);
    });
}

/** The recipient picked up. */
function log_call_answered(int $caller, int $recipient): void
{
    log_call_safely(function () use ($caller, $recipient) {
        q('UPDATE call_logs SET status = "answered", answered_at = ?
           WHERE caller_id = ? AND recipient_id = ? AND status = "ringing" AND ended_at IS NULL',
            [now_ms(), $caller, $recipient]);
    });
}

/** The recipient refused the call. */
function log_call_declined(int $caller, int $recipient): void
{
    log_call_safely(function () use ($caller, $recipient) {
        q('UPDATE call_logs SET status = "declined", ended_at = ?
           WHERE caller_id = ? AND recipient_id = ? AND status = "ringing" AND ended_at IS NULL',
            [now_ms(), $caller, $recipient]);
    });
}

/** $endedBy hung up the call between $a and $b (either of them may have started it). */
function log_call_ended(int $a, int $b, int $endedBy): void
{
    log_call_safely(function () use ($a, $b, $endedBy) {
        close_open_call_logs($a, $b, $endedBy);
        close_open_call_logs($b, $a, $endedBy);
    });
}

/**
 * Close the open log rows of calls from $caller to $recipient:
 *   answered      -> completed, with the time talked as duration_seconds
 *   still ringing -> missed when the caller gave up, declined when the
 *                    recipient was the one who ended it.
 */
function close_open_call_logs(int $caller, int $recipient, int $endedBy): void
{
    $now = now_ms();
    q('UPDATE call_logs
       SET status = "completed", ended_at = ?, duration_seconds = GREATEST(0, FLOOR((? - answered_at) / 1000))
       WHERE caller_id = ? AND recipient_id = ? AND status = "answered" AND ended_at IS NULL',
        [$now, $now, $caller, $recipient]);
    q('UPDATE call_logs SET status = ?, ended_at = ?
       WHERE caller_id = ? AND recipient_id = ? AND status = "ringing" AND ended_at IS NULL',
        [$endedBy === $recipient ? 'declined' : 'missed', $now, $caller, $recipient]);
}
