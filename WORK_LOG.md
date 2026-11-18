# Kinnect – work log

Everything done on this project in this session, in order. Status as of 18 Nov 2026.

## 1. Backend migration: Firebase → PHP + MySQL (done, committed)

**Goal:** replace Firebase Auth and Firebase Realtime Database with PHP web services on XAMPP, keep FCM for push only, and add SQLite on the phone.

### PHP web services (`backend/kinnect_api/`)

| Area | Endpoints |
| --- | --- |
| Accounts | `auth/signup.php`, `auth/login.php`, `auth/logout.php`, `auth/request_reset.php`, `auth/reset_password.php` |
| Users | `users/get.php`, `users/update.php`, `users/search.php`, `users/heartbeat.php` |
| Friends | `friends/list.php`, `friends/requests.php`, `friends/action.php` |
| Posts | `posts/feed.php`, `posts/create.php`, `posts/react.php` |
| Comments | `comments/list.php`, `comments/create.php`, `comments/like.php` |
| Stories | `stories/feed.php`, `stories/get.php`, `stories/create.php` |
| Chat | `chats/list.php`, `messages/list.php`, `messages/send.php`, `messages/edit.php`, `messages/delete.php`, `messages/react.php` |
| Calls | `calls/start.php`, `calls/incoming.php`, `calls/status.php`, `calls/answer.php`, `calls/end.php` |

Supporting files:

- `core.php` – database access (PDO, prepared statements), JSON responses, token authentication, image storage, FCM push sender.
- `config.php` – database settings (defaults match stock XAMPP: `root`, no password).
- `schema.sql` – 12 MySQL tables: users, auth_tokens, friends, friend_requests, posts, post_images, post_reactions, comments, comment_likes, stories, messages, calls.
- `setup.php` – creates the `kinnect_db` database and tables.
- `.htaccess` – blocks web access to `.sql` and `.json` files.
- `backend/deploy.bat` – copies the services into `C:\xampp\htdocs\kinnect_api`.

How it behaves:

- Passwords are hashed with `password_hash`; login returns a session token sent on every request in an `X-Auth-Token` header.
- Images are uploaded as Base64, validated, and stored as files in `uploads/`; the API returns their paths.
- New posts, comments, stories and messages carry a client-generated UUID (`clientId`) so a retried request is stored only once.
- Messages can be edited or deleted only by the sender and only within 5 minutes; the server enforces this.
- Stories older than 24 hours are no longer returned.
- Online status comes from a heartbeat: a user is online if the app contacted the server in the last 45 seconds.
- The server sends FCM pushes for new messages, friend requests, calls and screenshot alerts, once a Firebase service account key is added (see README).

### Android app (`app/`)

New files:

- `Api.kt` – HTTP client (`Api.get`, `Api.post`) and `LiveQuery`, which polls an endpoint so screens update without leaving them.
- `Session.kt` – signed-in user id and token.
- `LocalDb.kt` – SQLite: cached API responses for offline reading, and a queue of writes made offline that is replayed in order when the server is reachable.
- `KinnectApp.kt` – app start-up and the online heartbeat.
- `IncomingCallWatcher.kt`, `Friendship.kt`, `UiUtil.kt` – shared helpers.

Changed: every Activity and Adapter that used Firebase now calls the web services. Firebase Auth and Database dependencies were removed; only Firebase Messaging remains.

Also added:

- `ACCESS_LOCAL_NETWORK` permission and a prompt on the splash screen. Android 17 blocks apps targeting SDK 37 from reaching LAN addresses (the XAMPP PC) without it.
- `usesCleartextTraffic` so the app can call `http://` on the local network.
- `API_BASE_URL` build setting (default `http://10.0.2.2/kinnect_api/` for the emulator; set it in `local.properties` for a real phone).

### What was tested

- Every endpoint passed a scripted run against a throwaway test database.
- On the emulator against XAMPP: login, home feed, a post reaction, chat list and sending a message all worked, with rows confirmed in MySQL.
- Not run in the app: sign-up, profile editing, stories, comments, friend requests, calls, password reset, offline mode, real push delivery.

### Known limits

- Old Firebase data was not migrated; the database starts empty.
- Password reset codes are written to `C:\xampp\mailoutput` (XAMPP does not send real email).

## 2. UI rebuild to match `Kinnect_UI.pdf` (in progress, not yet built or committed)

**Goal:** all 23 screens match the design, using only LinearLayouts, RecyclerViews and scroll containers.

### Shared design kit (done, committed)

- `res/values/kinnect_design.xml` – colours, text styles and button/chip styles. Primary teal set to `#0B5F63`.
- 85 shared drawables: `k_bg_*` shapes (avatar circles, pills, buttons, cards, bubbles, badges) and `k_ic_*` icons.

### Screens

| Group | Screens | Status |
| --- | --- | --- |
| Auth | 01 Splash, 02 Log in, 03 Sign up, 16 Edit profile | Rebuilt, not yet built |
| Feed | 04 Home feed, 05 Reaction picker, 06 Comments, 07 Create post | Still being rebuilt |
| Stories and calls | 08 Photo picker, 09 Camera, 10 Story editor, 11 Story viewer, 12 Your story, 22 Voice call | Rebuilt, not yet built |
| People | 13 Search, 14 Friends, 15 Profile, 17 Other profile | Rebuilt, not yet built |
| Hub | 18 Notifications, 19 Menu, 20 Chats, 21 Chat, 23 Marketplace | Still being rebuilt |

"Rebuilt, not yet built" means the XML and small Kotlin UI changes are written and statically checked, but the app has not been compiled or run with them yet.

### Deviations from "LinearLayout only"

- Photo picker, photo grid item, Camera, Story editor, Story viewer, Your story and Voice call each use one FrameLayout as an overlay container, because controls sit on top of a full-screen image or video. Everything inside is LinearLayouts.
- The Voice call screen keeps two FrameLayouts that the Agora SDK needs for video.
- Auth and People screens use no FrameLayout; overlaps use negative margins.

### Known differences from the design so far

- Fonts: the design's display typeface is approximated with the system `sans-serif-black`.
- Sample content (badge counts, "Seen by 24", filter chips, Groups, recent searches, profile details, photo tiles) is static dummy content.
- Avatar colours are fixed per screen rather than varied per user.
- Search rows show "N mutual friends" rather than the design's "Friend · Lives in Karachi".

### Still to do for the UI

1. Finish the Feed and Hub groups.
2. Build the app and fix any errors.
3. Check each screen on the emulator against the PDF.
4. Commit.

## 3. Assignment 03 requirements (not started)

Read `SMD_Assignment_03.pdf`. Gaps already visible against the current backend, to be worked through after the UI:

- Offline: the assignment asks for SQLite tables for messages, posts, stories, users and friends, a `pending_actions` table, and a visible "pending" state. The app currently caches whole API responses and queues writes in `pending_requests`.
- Token storage: should use encrypted storage (e.g. EncryptedSharedPreferences); it is in plain SharedPreferences now.
- Uploads: posts should use a multipart upload endpoint; they are sent as Base64 in JSON now.
- Stories and chat: video stories, and video/file messages, are not supported yet.
- Calls: call logs are not stored yet.
- Search: Friends / Requests / Everyone filter is missing.
- Notifications screen: friend requests should be confirmable there.
- Friend requests and message edit/delete are not queued offline yet.
- Two Espresso tests exist from Assignment 2 but have not been updated or run.
- Submission rules: private GitHub repo, commits from both members, and `google-services.json` must not be in a public repo (it is currently committed locally; nothing is pushed).

## Git

Local commits so far (nothing pushed; waiting for the repository URL):

1. Baseline: Kinnect app with Firebase backend
2. Add PHP web services and MySQL schema for XAMPP
3. Replace Firebase Auth and Realtime Database with PHP web services
4. Request local network access, log unreachable server, add README
5. Add shared Kinnect design kit: colours, text and button styles, icons

## How to run

See `README.md`.
