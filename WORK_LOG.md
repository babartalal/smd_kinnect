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

## 2. UI rebuild to match `Kinnect_UI.pdf` (done, committed)

**Goal:** all 23 screens match the design, using only LinearLayouts, RecyclerViews and scroll containers.

### Shared design kit

- `res/values/kinnect_design.xml` – colours, text styles and button/chip styles. Primary teal set to `#0B5F63`.
- 85 shared drawables: `k_bg_*` shapes (avatar circles, pills, buttons, cards, bubbles, badges) and `k_ic_*` icons.

### Screens

| Group | Screens | Layouts rebuilt | Checked on emulator |
| --- | --- | --- | --- |
| Auth | 01 Splash, 02 Log in, 03 Sign up, 16 Edit profile | Yes | All four opened and compared |
| Feed | 04 Home feed, 05 Reaction picker, 06 Comments, 07 Create post | Yes | All four opened and compared |
| Stories and calls | 08 Photo picker, 09 Camera, 10 Story editor, 11 Story viewer, 12 Your story, 22 Voice call | Yes | Camera, Story editor, Story viewer, Your story opened and compared. Photo picker is not linked from any screen and Voice call closes at once without an `AGORA_APP_ID`, so those two were not viewed |
| People | 13 Search, 14 Friends, 15 Profile, 17 Other profile | Yes | All four opened and compared |
| Hub | 18 Notifications, 19 Menu, 20 Chats, 21 Chat, 23 Marketplace | Yes | All five opened |

The app builds, and no screen that was opened crashed. Home feed, Friends, Profile and Chat were compared with the PDF by screenshot.

### Deviations from "LinearLayout only"

- Photo picker, photo grid item, Camera, Story editor, Story viewer, Your story and Voice call each use one FrameLayout as an overlay container, because controls sit on top of a full-screen image or video. Everything inside is LinearLayouts.
- The Voice call screen keeps two FrameLayouts that the Agora SDK needs for video.
- All other screens use no FrameLayout; overlaps (badges, avatar over cover, "+N" on photos) use negative margins.
- No ConstraintLayout, RelativeLayout or CardView remains anywhere.

### Known differences from the design

- Fonts: the design's display typeface is approximated with the system `sans-serif-black`.
- Sample content is static dummy content: badge counts, "86 comments · 12 shares", notification rows, marketplace listings, menu tiles, filter chips, Groups, recent searches, profile details, "Seen by 24", camera mode tabs.
- Home feed: "is at Clifton Beach" is omitted (no location data); the reactions text shows "N reactions".
- Reaction picker: plain white pill with six icons; the enlarged icon with tooltip and dimmed backdrop are not built.
- Comments: thread connector lines and "View 6 more replies" are not built.
- Chat: the typing indicator and seen-avatar dot are omitted; per-message times are hidden as in the design; "TODAY 4:49 PM" is a static label.
- Chat messages now sit in a scrolling column under the contact header, so very long conversations lay out every row.
- Chats: the group-chat row with two stacked avatars is not reproduced.
- Search rows show "N mutual friends" rather than "Friend · Lives in Karachi"; existing friends keep a "Friends" pill so unfriend still works.
- Story editor: "Saturday plans" is the hint of the label field, not real text.
- Avatar colours are fixed per screen in most lists (per person in the chat list).

## 3. Assignment 03 requirements (done, committed)

How each requirement in `SMD_Assignment_03.pdf` is met:

| Requirement | Status | Where |
| --- | --- | --- |
| Splash 5 s, then Home if a token is stored (even offline), else Log in | Done | `SplashActivity.kt` |
| Sign up, log in, log out through own endpoints; passwords hashed on the server | Done | `auth/*.php` (`password_hash`) |
| Session token stored securely and sent with every request | Done | `Session.kt` encrypts it with an AES-GCM key held in the Android Keystore |
| First-time users go to Edit profile to pick a unique username and bio | Done | `users/update.php` checks uniqueness |
| Password strength and Recent login card | Done | `CreateAccountActivity.kt`, `LoginActivity.kt` |
| Log out invalidates the token, marks offline, clears SQLite, clears the back stack | Done | `auth/logout.php`, `Session.clear()` |
| Stories: image or video, text label, Your story / Close friends, upload endpoint | Done | `media/upload.php`, `stories/create.php` |
| Stories disappear after 24 hours on the server and in SQLite | Done | `stories/feed.php`, `LocalDb.loadResponse` |
| Posts with one or more images through a multipart upload endpoint | Done | `posts/create.php` (multipart), `CreatePostActivity.kt` |
| Six reactions, one per user, changeable or removable | Done | `posts/react.php` |
| Comments with replies, likes and image comments | Done | `comments/*.php` |
| Feed of own and friends' posts, newest first, cached in SQLite | Done | `posts/feed.php`, `posts` table in `LocalDb.kt` |
| Chat: text, images, videos and files; stored in MySQL; cached in SQLite | Done | `media/upload.php`, `messages/send.php`, `messages` table |
| Edit/delete own message within 5 minutes, enforced by the server; "Edited" label | Done | `messages/edit.php`, `messages/delete.php` |
| New messages appear without leaving the screen | Done | `LiveQuery` polls every 2.5 s |
| Voice and video calls with Agora; call logs stored through web services | Done | `calls/*.php`, `call_logs` table, `calls/logs.php` |
| Friend requests from Search, Other profile, Friends and Notifications | Done | `friends/action.php`, `NotificationRequestAdapter.kt` |
| Add friend → Requested → Friends button state | Done | `Friendship.kt` |
| Edit profile picture, cover, name, username, bio | Done | `users/update.php` |
| FCM for push only; server stores tokens and sends | Done | `users/heartbeat.php`, `send_push` in `core.php` |
| Push for new messages, friend requests, screenshot alerts | Done | needs a Firebase service account key on the server (see README) |
| Search by username or name as the user types | Done | `users/search.php` |
| Filter by Friends / Requests / Everyone | Done | `filter` parameter, chips in `SearchActivity.kt` |
| Online/offline status in real time | Done | heartbeat, 45 s window |
| Screenshot notification and system message | Done | `ChatActivity.kt`, `messages/send.php` |
| SQLite stores messages, posts, stories, users, friends; screens read SQLite first | Done | `LocalDb.kt`, `LiveQuery.showLocal` |
| Offline actions queued in `pending_actions` and shown with a pending state | Done | messages ("Sending..."), posts ("Pending"), comments, reactions, friend requests, message edit/delete |
| Background sync retries queued actions in order on reconnect | Done | `KinnectApp.kt` network callback, `Api.syncPending` |
| No data loss, no duplication: client UUID ignored by the server on repeats | Done | `processed_actions` table, `replay_if_repeated` in `core.php` |
| Two Espresso tests | Done | `AuthNavigationTest.kt`, `HomeCommentsTest.kt` |

### What was tested

- Offline, on the emulator: with airplane mode on, a chat message showed at once with "Sending..." and a post reaction showed at once; MySQL had neither. After turning airplane mode off, MySQL had exactly one of each and the label disappeared.
- Repeat safety, on the server: the same `clientId` sent twice returned the same answer and changed the data once.
- Espresso: all five test methods pass on the emulator against XAMPP (`gradlew connectedDebugAndroidTest`).
- Through Apache: file upload and download, a multipart post, rejection of a `.php` upload, the Friends search filter, and a declined call appearing in `calls/logs.php`.
- In the app: feed, notifications, the search filter chips, the chat list and a file message in chat all opened without crashes.

### Not tested or limited

- Not run in the app: sending a video or file from the phone's picker, video stories, calls (no `AGORA_APP_ID`), real push delivery (no service account key), friend requests on the Notifications screen with live data, offline post/comment/friend-request/edit/delete (only offline message and reaction were run).
- Attachments (photo, video, file) in chat need a connection; only text messages queue offline.
- A post written offline shows in the feed as text only until it syncs.
- Video messages show a play tile without a thumbnail.
- The call history has an endpoint but no screen.
- Still open from the submission rules: push to a private GitHub repo, commits from both group members, keep `google-services.json` out of any public repo, and the project/package naming rule (`rollNumber1_rollNumber2`).

## Git

Nothing is pushed yet (waiting for the repository URL). Local commits, oldest first:

1. Baseline: Kinnect app with Firebase backend
1. Add PHP web services and MySQL schema for XAMPP
1. Replace Firebase Auth and Realtime Database with PHP web services
1. Request local network access, log unreachable server, add README
1. Add shared Kinnect design kit: colours, text and button styles, icons
1. Rebuild all screens to match Kinnect_UI.pdf using LinearLayouts
1. Add work log
1. Redraw logo mark with larger interlocking rings; update work log
1. Backend: ignore repeated writes by clientId and add a migration runner
1. Store the session token encrypted with an Android Keystore AES-GCM key
1. Offline support: SQLite content tables, pending_actions queue and auto sync
1. Search: filter results by Everyone, Friends or Requests
1. Notifications: show real friend requests with working Confirm and Delete
1. Calls: store call history in call_logs and add calls/logs.php
1. Espresso tests for login and commenting against the PHP backend
1. Media: multipart upload endpoint, video and file messages, video stories

## How to run

See `README.md`.
