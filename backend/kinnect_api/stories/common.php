<?php
// Shared by the stories endpoints.

const STORY_LIFETIME_MS = 24 * 60 * 60 * 1000;

function story_json(array $row): array
{
    return [
        'storyId'   => (string) $row['id'],
        'userId'    => (string) $row['user_id'],
        'timestamp' => (int) $row['created_at'],
        // Path of the story's picture or video (see mediaType).
        'image'     => image_path($row['image']),
        'mediaType' => ($row['media_type'] ?? '') ?: 'image',
        'textLabel' => $row['text_label'],
        'audience'  => $row['audience'],
    ];
}
