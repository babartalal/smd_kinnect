<?php
// Shared by the stories endpoints.

const STORY_LIFETIME_MS = 24 * 60 * 60 * 1000;

function story_json(array $row): array
{
    return [
        'storyId'   => (string) $row['id'],
        'userId'    => (string) $row['user_id'],
        'timestamp' => (int) $row['created_at'],
        'image'     => image_path($row['image']),
        'textLabel' => $row['text_label'],
        'audience'  => $row['audience'],
    ];
}
