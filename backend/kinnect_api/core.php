<?php
// Shared plumbing for every Kinnect web service: database access, request
// parsing, JSON responses, token authentication, image and media storage and push.

require_once __DIR__ . '/config.php';

header('Content-Type: application/json; charset=utf-8');

set_exception_handler(function (Throwable $e) {
    fail(API_DEBUG ? 'Server error: ' . $e->getMessage() : 'Server error', 500);
});

// ---------- database ----------

function db(): PDO
{
    static $pdo = null;
    if ($pdo === null) {
        $dsn = 'mysql:host=' . DB_HOST . ';port=' . DB_PORT . ';dbname=' . DB_NAME . ';charset=utf8mb4';
        $pdo = new PDO($dsn, DB_USER, DB_PASS, [
            PDO::ATTR_ERRMODE            => PDO::ERRMODE_EXCEPTION,
            PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
            PDO::ATTR_EMULATE_PREPARES   => false,
        ]);
    }
    return $pdo;
}

/** Run a prepared statement and return it. */
function q(string $sql, array $params = []): PDOStatement
{
    $stmt = db()->prepare($sql);
    $stmt->execute($params);
    return $stmt;
}

function now_ms(): int
{
    return (int) round(microtime(true) * 1000);
}

// ---------- request / response ----------

/** Query string, form fields and JSON body merged into one array. */
function input(): array
{
    static $in = null;
    if ($in === null) {
        $json = json_decode(file_get_contents('php://input') ?: '', true);
        $in = array_merge($_GET, $_POST, is_array($json) ? $json : []);
    }
    return $in;
}

function param(string $key, $default = '')
{
    $in = input();
    if (!isset($in[$key])) {
        return $default;
    }
    return is_string($in[$key]) ? trim($in[$key]) : $in[$key];
}

function int_param(string $key): int
{
    return (int) param($key, 0);
}

function require_method(string $method): void
{
    if (($_SERVER['REQUEST_METHOD'] ?? 'GET') !== $method) {
        fail("This endpoint only accepts $method", 405);
    }
}

/**
 * Send a success response. The client may pass back the etag it last saw;
 * when the data is unchanged we answer with notModified and no body, which
 * keeps the app's polling cheap.
 */
function respond($data = null): void
{
    $etag = md5(json_encode($data));
    if ($etag === (string) param('etag')) {
        echo json_encode(['ok' => true, 'notModified' => true, 'etag' => $etag]);
        exit;
    }
    $body = json_encode(['ok' => true, 'etag' => $etag, 'data' => $data], JSON_UNESCAPED_SLASHES);
    remember_action($body);
    echo $body;
    exit;
}

// ---------- repeat-safe writes ----------
// Every write the app queues offline carries a clientId (UUID). The first
// time it arrives we save the answer; if the same clientId arrives again
// (a retry after a lost connection) we return that answer without redoing
// the action, so nothing is ever duplicated or toggled twice.

function action_client_id(): string
{
    if (($_SERVER['REQUEST_METHOD'] ?? 'GET') !== 'POST') {
        return '';
    }
    $clientId = (string) param('clientId');
    return preg_match('/^[A-Za-z0-9-]{8,40}$/', $clientId) ? $clientId : '';
}

/** Called once the user is known: answer a repeated request from the saved copy. */
function replay_if_repeated(int $userId): void
{
    $GLOBALS['kinnect_user_id'] = $userId;
    $clientId = action_client_id();
    if ($clientId === '') {
        return;
    }
    $saved = q('SELECT response FROM processed_actions WHERE client_id = ? AND user_id = ?',
        [$clientId, $userId])->fetchColumn();
    if ($saved !== false) {
        echo $saved;
        exit;
    }
}

function remember_action(string $responseBody): void
{
    $clientId = action_client_id();
    $userId = $GLOBALS['kinnect_user_id'] ?? 0;
    if ($clientId === '' || !$userId) {
        return;
    }
    q('INSERT IGNORE INTO processed_actions (client_id, user_id, response, created_at) VALUES (?, ?, ?, ?)',
        [$clientId, $userId, $responseBody, now_ms()]);
}

function fail(string $message, int $status = 400): void
{
    http_response_code($status);
    echo json_encode(['ok' => false, 'error' => $message]);
    exit;
}

// ---------- authentication ----------

function issue_token(int $userId): string
{
    $token = bin2hex(random_bytes(32));
    q('INSERT INTO auth_tokens (token_hash, user_id, created_at) VALUES (?, ?, ?)',
        [hash('sha256', $token), $userId, now_ms()]);
    return $token;
}

function request_token(): string
{
    return trim($_SERVER['HTTP_X_AUTH_TOKEN'] ?? '');
}

/** Return the signed-in user's id or stop with 401. Also refreshes presence. */
function require_auth(): int
{
    $token = request_token();
    if ($token === '') {
        fail('Not signed in', 401);
    }
    $row = q('SELECT user_id FROM auth_tokens WHERE token_hash = ?', [hash('sha256', $token)])->fetch();
    if (!$row) {
        fail('Session expired. Please log in again.', 401);
    }
    $userId = (int) $row['user_id'];
    q('UPDATE users SET last_seen = ?, is_online = 1 WHERE id = ?', [now_ms(), $userId]);
    replay_if_repeated($userId);
    return $userId;
}

// ---------- images ----------

/**
 * Store a Base64 image from the app in uploads/ and return its file name.
 * Only real JPEG/PNG/WebP data is accepted, and the name is generated here.
 */
function save_image($base64): string
{
    if (!is_string($base64) || $base64 === '') {
        return '';
    }
    $bytes = base64_decode($base64, true);
    if ($bytes === false || strlen($bytes) > MAX_IMAGE_BYTES) {
        fail('Invalid or oversized image');
    }
    $info = @getimagesizefromstring($bytes);
    $extensions = [IMAGETYPE_JPEG => 'jpg', IMAGETYPE_PNG => 'png', IMAGETYPE_WEBP => 'webp'];
    if (!$info || !isset($extensions[$info[2]])) {
        fail('Unsupported image format');
    }
    if (!is_dir(UPLOAD_DIR)) {
        mkdir(UPLOAD_DIR, 0775, true);
    }
    $name = bin2hex(random_bytes(16)) . '.' . $extensions[$info[2]];
    if (file_put_contents(UPLOAD_DIR . '/' . $name, $bytes) === false) {
        fail('Could not store image', 500);
    }
    return $name;
}

/** Path the app appends to its base URL to download an image. */
function image_path(string $name): string
{
    return $name === '' ? '' : 'uploads/' . $name;
}

function delete_image(string $name): void
{
    if (is_stored_media_name($name)) {
        @unlink(UPLOAD_DIR . '/' . $name);
    }
}

// ---------- uploaded media (multipart) ----------
// Files arrive as multipart/form-data. What may be stored is decided here:
// the extension must be on the whitelist below AND the real content type
// (finfo) must be one that extension is allowed to have. The stored name is
// always "<32 random hex>.<whitelisted extension>", never the client's name,
// so nothing executable (.php, .phtml, .htaccess ...) can ever land in uploads/.

/** extension => [media type, content types that extension may really contain] */
function media_rules(): array
{
    $office = ['application/msword', 'application/vnd.ms-excel', 'application/vnd.ms-powerpoint',
               'application/vnd.ms-office', 'application/x-ole-storage', 'application/CDFV2'];
    $officeXml = ['application/zip', 'application/octet-stream',
                  'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
                  'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
                  'application/vnd.openxmlformats-officedocument.presentationml.presentation'];
    $mp4 = ['video/mp4', 'video/3gpp', 'video/quicktime', 'video/x-m4v'];
    $matroska = ['video/webm', 'video/x-matroska'];
    return [
        'jpg'  => ['image', ['image/jpeg']],
        'jpeg' => ['image', ['image/jpeg']],
        'png'  => ['image', ['image/png']],
        'webp' => ['image', ['image/webp']],
        'mp4'  => ['video', $mp4],
        '3gp'  => ['video', $mp4],
        'webm' => ['video', $matroska],
        'mkv'  => ['video', $matroska],
        'pdf'  => ['file', ['application/pdf']],
        'doc'  => ['file', $office],
        'xls'  => ['file', $office],
        'ppt'  => ['file', $office],
        'docx' => ['file', $officeXml],
        'xlsx' => ['file', $officeXml],
        'pptx' => ['file', $officeXml],
        'txt'  => ['file', ['text/plain']],
        'zip'  => ['file', ['application/zip', 'application/x-zip-compressed']],
    ];
}

/** True for a file name this server generated: 32 hex characters plus a whitelisted extension. */
function is_stored_media_name(string $name): bool
{
    return preg_match('/^[a-f0-9]{32}\.([a-z0-9]{2,4})$/', $name, $m) === 1
        && $m[1] !== 'jpeg' && isset(media_rules()[$m[1]]);
}

/** The client's file name made safe to show: no folders, no control or reserved characters. */
function display_file_name($name): string
{
    $name = is_string($name) ? $name : '';
    $name = basename(str_replace('\\', '/', $name));
    $name = preg_replace('~[\x00-\x1F\x7F<>:"/|?*]+~', '', $name) ?? '';
    return mb_substr(trim($name, ' .'), 0, 120);
}

/**
 * The files sent under one multipart field as a plain list, whether the app
 * sent one file ("file") or several ("images[]").
 */
function uploaded_files(string $field): array
{
    $entry = $_FILES[$field] ?? null;
    if (!is_array($entry) || !isset($entry['error'])) {
        return [];
    }
    if (!is_array($entry['error'])) {
        return $entry['error'] === UPLOAD_ERR_NO_FILE ? [] : [$entry];
    }
    $files = [];
    foreach ($entry['error'] as $i => $error) {
        if ($error !== UPLOAD_ERR_NO_FILE) {
            $files[] = [
                'name'     => $entry['name'][$i],
                'tmp_name' => $entry['tmp_name'][$i],
                'error'    => $error,
                'size'     => $entry['size'][$i],
            ];
        }
    }
    return $files;
}

/**
 * Check one uploaded file without storing it. Stops the request with an error
 * when it is missing, too large, or not an allowed type. Returns
 * [tmp, extension, mediaType, fileName, size].
 */
function inspect_upload($file, array $allowedTypes = ['image', 'video', 'file']): array
{
    $limitMb = (int) (MAX_UPLOAD_BYTES / 1048576);
    if (!is_array($file) || !isset($file['error']) || is_array($file['error'])) {
        // PHP drops the whole body when it is bigger than post_max_size.
        if ((int) ($_SERVER['CONTENT_LENGTH'] ?? 0) > MAX_UPLOAD_BYTES) {
            fail("File is too large (limit $limitMb MB)", 413);
        }
        fail('No file was uploaded');
    }
    if ($file['error'] === UPLOAD_ERR_INI_SIZE || $file['error'] === UPLOAD_ERR_FORM_SIZE) {
        fail("File is too large (limit $limitMb MB)", 413);
    }
    if ($file['error'] !== UPLOAD_ERR_OK || !is_uploaded_file($file['tmp_name'])) {
        fail('The upload did not complete. Please try again.');
    }
    $tmp = $file['tmp_name'];
    $size = (int) filesize($tmp);
    if ($size <= 0) {
        fail('The file is empty');
    }
    if ($size > MAX_UPLOAD_BYTES) {
        fail("File is too large (limit $limitMb MB)", 413);
    }

    $rules = media_rules();
    $extension = strtolower(pathinfo((string) $file['name'], PATHINFO_EXTENSION));
    $mime = (string) (new finfo(FILEINFO_MIME_TYPE))->file($tmp);

    // Pictures: the content decides, whatever the client called the file.
    $imageTypes = ['image/jpeg' => 'jpg', 'image/png' => 'png', 'image/webp' => 'webp'];
    // A camera or gallery item often has no extension in its name; for these
    // unmistakable content types the extension is taken from the content.
    $byContent = $imageTypes + ['video/mp4' => 'mp4', 'video/3gpp' => '3gp', 'video/webm' => 'webm',
                                'video/x-matroska' => 'mkv', 'application/pdf' => 'pdf'];

    if (isset($imageTypes[$mime])) {
        if (!@getimagesize($tmp)) {
            fail('Unsupported image format');
        }
        $extension = $imageTypes[$mime];
    } elseif (!isset($rules[$extension]) && isset($byContent[$mime])) {
        $extension = $byContent[$mime];
    }

    if (!isset($rules[$extension])) {
        fail('This file type is not allowed');
    }
    [$mediaType, $contentTypes] = $rules[$extension];
    $matches = in_array($mime, $contentTypes, true)
        || ($extension === 'txt' && strncmp($mime, 'text/', 5) === 0);
    if (!$matches) {
        fail('The file content does not match its type');
    }
    if (!in_array($mediaType, $allowedTypes, true)) {
        fail('Only ' . implode(' or ', $allowedTypes) . ' files are accepted here');
    }

    $fileName = display_file_name($file['name']);
    return [
        'tmp'       => $tmp,
        'extension' => $extension === 'jpeg' ? 'jpg' : $extension,
        'mediaType' => $mediaType,
        'fileName'  => $fileName !== '' ? $fileName : $mediaType . '.' . $extension,
        'size'      => $size,
    ];
}

/**
 * Validate an uploaded file and move it into uploads/ under a random,
 * server-chosen name (the multipart counterpart of save_image). $file is one
 * entry of $_FILES, or the result of inspect_upload when it was checked
 * already. Returns [name, mediaType, fileName, size]: "name" goes in the
 * database and image_path(name) is what the app downloads.
 */
function store_upload($file, array $allowedTypes = ['image', 'video', 'file']): array
{
    $info = is_array($file) && isset($file['tmp'], $file['extension']) ? $file : inspect_upload($file, $allowedTypes);
    if (!is_dir(UPLOAD_DIR)) {
        mkdir(UPLOAD_DIR, 0775, true);
    }
    $name = bin2hex(random_bytes(16)) . '.' . $info['extension'];
    if (!move_uploaded_file($info['tmp'], UPLOAD_DIR . '/' . $name)) {
        fail('Could not store the file', 500);
    }
    return ['name' => $name, 'mediaType' => $info['mediaType'], 'fileName' => $info['fileName'], 'size' => $info['size']];
}

/**
 * Take a file the signed-in user uploaded earlier through media/upload.php so
 * it can be attached to a message or story. $path is what the upload returned
 * ("uploads/<name>"). The file must exist, be theirs, be of an allowed kind and
 * not be attached to anything yet. Returns its media_uploads row.
 */
function claim_upload($path, int $userId, array $allowedTypes = ['image', 'video', 'file']): array
{
    $name = is_string($path) && strncmp($path, 'uploads/', 8) === 0 ? substr($path, 8) : '';
    if (!is_stored_media_name($name) || !is_file(UPLOAD_DIR . '/' . $name)) {
        fail('Attachment not found. Please upload it again.');
    }
    $row = q('SELECT * FROM media_uploads WHERE name = ? AND user_id = ?', [$name, $userId])->fetch();
    if (!$row) {
        fail('Attachment not found. Please upload it again.');
    }
    if (!in_array($row['media_type'], $allowedTypes, true)) {
        fail('Only ' . implode(' or ', $allowedTypes) . ' files are accepted here');
    }
    $claimed = q('UPDATE media_uploads SET used = 1 WHERE name = ? AND used = 0', [$name])->rowCount();
    if ($claimed !== 1) {
        fail('This attachment was already sent. Please upload it again.');
    }
    return $row;
}

// ---------- users and friendships ----------

function user_json(array $row, bool $private = false): array
{
    $online = (int) $row['is_online'] === 1 && (now_ms() - (int) $row['last_seen']) < ONLINE_WINDOW_MS;
    return [
        'userId'     => (string) $row['id'],
        'firstName'  => $row['first_name'],
        'lastName'   => $row['last_name'],
        'name'       => $row['name'],
        'username'   => (string) $row['username'],
        'bio'        => $row['bio'],
        'website'    => $row['website'],
        'birthday'   => $row['birthday'],
        'gender'     => $row['gender'],
        'email'      => $private ? $row['email'] : '',
        'phone'      => $private ? $row['phone'] : '',
        'profilePic' => image_path($row['profile_pic']),
        'coverPhoto' => image_path($row['cover_photo']),
        'isOnline'   => $online,
    ];
}

function fetch_user_row(int $id): ?array
{
    $row = q('SELECT * FROM users WHERE id = ?', [$id])->fetch();
    return $row ?: null;
}

function are_friends(int $a, int $b): bool
{
    return (bool) q('SELECT 1 FROM friends WHERE user_id = ? AND friend_id = ?', [$a, $b])->fetch();
}

function friend_ids(int $userId): array
{
    return array_map('intval',
        q('SELECT friend_id FROM friends WHERE user_id = ?', [$userId])->fetchAll(PDO::FETCH_COLUMN));
}

/** self | friends | requested (I asked them) | incoming (they asked me) | none */
function friend_state(int $me, int $other): string
{
    if ($me === $other) {
        return 'self';
    }
    if (are_friends($me, $other)) {
        return 'friends';
    }
    if (q('SELECT 1 FROM friend_requests WHERE sender_id = ? AND receiver_id = ?', [$me, $other])->fetch()) {
        return 'requested';
    }
    if (q('SELECT 1 FROM friend_requests WHERE sender_id = ? AND receiver_id = ?', [$other, $me])->fetch()) {
        return 'incoming';
    }
    return 'none';
}

/** User JSON plus how that user relates to the signed-in user. */
function user_with_relation(array $row, int $me): array
{
    $other = (int) $row['id'];
    $user = user_json($row, $other === $me);
    $user['friendState'] = friend_state($me, $other);
    $user['friendCount'] = (int) q('SELECT COUNT(*) FROM friends WHERE user_id = ?', [$other])->fetchColumn();
    $user['mutualCount'] = $other === $me ? 0 : (int) q(
        'SELECT COUNT(*) FROM friends a JOIN friends b ON a.friend_id = b.friend_id
         WHERE a.user_id = ? AND b.user_id = ?', [$me, $other])->fetchColumn();
    return $user;
}

function display_name(array $row): string
{
    $name = trim($row['name']) ?: trim($row['first_name'] . ' ' . $row['last_name']);
    return $name ?: (string) $row['username'];
}

function chat_id(int $a, int $b): string
{
    return min($a, $b) . '_' . max($a, $b);
}

// ---------- push notifications (FCM HTTP v1) ----------

function base64url(string $data): string
{
    return rtrim(strtr(base64_encode($data), '+/', '-_'), '=');
}

function http_post(string $url, array $headers, string $body): ?array
{
    $ch = curl_init($url);
    curl_setopt_array($ch, [
        CURLOPT_POST           => true,
        CURLOPT_HTTPHEADER     => $headers,
        CURLOPT_POSTFIELDS     => $body,
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_CONNECTTIMEOUT => 3,
        CURLOPT_TIMEOUT        => 5,
    ]);
    $response = curl_exec($ch);
    curl_close($ch);
    return is_string($response) ? json_decode($response, true) : null;
}

/** OAuth access token for the Firebase service account, cached until it expires. */
function fcm_access_token(array $account): ?string
{
    $cacheFile = sys_get_temp_dir() . '/kinnect_fcm_' . md5($account['client_email']) . '.json';
    $cached = @json_decode((string) @file_get_contents($cacheFile), true);
    if (is_array($cached) && ($cached['expires'] ?? 0) > time() + 60) {
        return $cached['token'];
    }
    $now = time();
    $unsigned = base64url(json_encode(['alg' => 'RS256', 'typ' => 'JWT'])) . '.' . base64url(json_encode([
        'iss'   => $account['client_email'],
        'scope' => 'https://www.googleapis.com/auth/firebase.messaging',
        'aud'   => 'https://oauth2.googleapis.com/token',
        'iat'   => $now,
        'exp'   => $now + 3600,
    ]));
    if (!openssl_sign($unsigned, $signature, $account['private_key'], OPENSSL_ALGO_SHA256)) {
        return null;
    }
    $result = http_post('https://oauth2.googleapis.com/token',
        ['Content-Type: application/x-www-form-urlencoded'],
        http_build_query([
            'grant_type' => 'urn:ietf:params:oauth:grant-type:jwt-bearer',
            'assertion'  => $unsigned . '.' . base64url($signature),
        ]));
    if (empty($result['access_token'])) {
        return null;
    }
    @file_put_contents($cacheFile, json_encode([
        'token'   => $result['access_token'],
        'expires' => $now + (int) ($result['expires_in'] ?? 3600),
    ]));
    return $result['access_token'];
}

/**
 * Best-effort push to one user's device. Never fails the calling request:
 * without a service account file or a device token it simply does nothing.
 */
function send_push(int $userId, string $title, string $body): void
{
    try {
        if (!is_file(FCM_SERVICE_ACCOUNT) || !function_exists('curl_init')) {
            return;
        }
        $deviceToken = (string) q('SELECT fcm_token FROM users WHERE id = ?', [$userId])->fetchColumn();
        $account = json_decode((string) file_get_contents(FCM_SERVICE_ACCOUNT), true);
        if ($deviceToken === '' || empty($account['project_id'])) {
            return;
        }
        $accessToken = fcm_access_token($account);
        if ($accessToken === null) {
            return;
        }
        http_post(
            'https://fcm.googleapis.com/v1/projects/' . $account['project_id'] . '/messages:send',
            ['Authorization: Bearer ' . $accessToken, 'Content-Type: application/json'],
            json_encode(['message' => [
                'token'        => $deviceToken,
                'notification' => ['title' => $title, 'body' => $body],
                'data'         => ['title' => $title, 'message' => $body],
            ]])
        );
    } catch (Throwable $e) {
        error_log('Kinnect push failed: ' . $e->getMessage());
    }
}

/**
 * User JSON for embedding in feeds (post, comment and story authors). Leaves
 * out the online flag so a feed's etag does not change every time someone
 * opens or closes the app.
 */
function author_json(int $userId): array
{
    $author = user_json(fetch_user_row($userId));
    unset($author['isOnline']);
    return $author;
}
