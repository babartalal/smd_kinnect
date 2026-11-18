# Kinnect

Android social app (posts, stories, friends, chat, voice/video calls).

- **App:** Kotlin, in `app/`.
- **Backend:** PHP web services + MySQL, in `backend/kinnect_api/`, served by XAMPP.
- **Offline:** a SQLite database on the phone caches what you have seen and queues what you write while offline.
- **Push:** Firebase Cloud Messaging only. Firebase Auth and Realtime Database are no longer used.

## Run the backend (XAMPP)

1. Start **Apache** and **MySQL** in the XAMPP control panel.
2. Copy the web services into XAMPP by double-clicking `backend/deploy.bat`
   (it copies `backend/kinnect_api` to `C:\xampp\htdocs\kinnect_api`; run it again after changing any PHP file).
3. Open <http://localhost/kinnect_api/setup.php>. It creates the `kinnect_db` database and its tables. Open it again after pulling new changes: it applies any new files in `migrations/`.
4. Open <http://localhost/kinnect_api/>. You should see `"database":"ok"`.

Database settings (host, user, password) are in `backend/kinnect_api/config.php`. The defaults match a stock XAMPP install (`root`, no password).

## Run the app

Open the project in Android Studio and run it.

- **Emulator:** works as is. The app calls `http://10.0.2.2/kinnect_api/`, which is the emulator's address for your PC.
- **Real phone:** connect the phone to the same Wi-Fi as the PC, find the PC's IP with `ipconfig`, and add this line to `local.properties`, then rebuild:

  ```
  API_BASE_URL=http://192.168.1.10/kinnect_api/
  ```

  Windows Firewall must allow Apache on port 80.

`local.properties` also holds `AGORA_APP_ID` for calls.

## Optional features

- **Push notifications:** download a service account key from the Firebase console
  (Project settings → Service accounts → Generate new private key) and save it as
  `C:\xampp\htdocs\kinnect_api\firebase-service-account.json`. Without it everything else still works; the server just skips sending pushes.
- **Password reset emails:** a stock XAMPP does not send real email. The reset code is written to a file in `C:\xampp\mailoutput`.

## Web services

All endpoints live under `/kinnect_api/` and answer with JSON: `{"ok":true,"data":...}` or `{"ok":false,"error":"..."}`.
After login, requests carry the token in an `X-Auth-Token` header.

| Area | Endpoints |
| --- | --- |
| Accounts | `auth/signup.php`, `auth/login.php`, `auth/logout.php`, `auth/request_reset.php`, `auth/reset_password.php` |
| Users | `users/get.php`, `users/update.php`, `users/search.php` (`filter=everyone|friends|requests`), `users/heartbeat.php` |
| Friends | `friends/list.php`, `friends/requests.php`, `friends/action.php` |
| Posts | `posts/feed.php`, `posts/create.php`, `posts/react.php` |
| Comments | `comments/list.php`, `comments/create.php`, `comments/like.php` |
| Stories | `stories/feed.php`, `stories/get.php`, `stories/create.php` |
| Chat | `chats/list.php`, `messages/list.php`, `messages/send.php`, `messages/edit.php`, `messages/delete.php`, `messages/react.php` |
| Calls | `calls/start.php`, `calls/incoming.php`, `calls/status.php`, `calls/answer.php`, `calls/end.php`, `calls/logs.php` |
| Media | `media/upload.php` (multipart: images, videos, documents) |

Each PHP file starts with a comment describing its parameters.

## How the app uses them

- `Api.kt` – HTTP client for the web services (`Api.get`, `Api.post`) and `LiveQuery`, which polls an endpoint so screens update by themselves.
- `Session.kt` – the signed-in user's id and token.
- `LocalDb.kt` – the SQLite database: `users`, `friends`, `posts`, `stories`, `messages`, and the `pending_actions` queue of writes made offline.

## Firebase config file

`app/google-services.json` is not in the repository (it is public). Download it from the Firebase console (Project settings → Your apps) and put it in `app/` before building.
