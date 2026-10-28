# Shared Trefle search server

The Android app uses **https://plant-app-hcc.onrender.com** by default. Teammates can pull the repository and run **app** in Android Studio without a Trefle account, token or server on their computer. Only the public service URL is compiled into the APK.

## Render settings

The service runs Node.js 24.22.1 using built-in modules, with no npm dependencies or database. Use these settings for the existing Render service:

| Setting | Value |
| --- | --- |
| Repository | `JonathanLeuenberger/PLANT_App_HCC` |
| Branch | `main` |
| Runtime | Node |
| Root Directory | Leave empty (repository root) |
| Build Command | `node --check server/trefle.mjs` |
| Start Command | `node server/trefle.mjs` |
| Compute | Free |
| Health Check Path | `/health` |
| Secret environment variable | `TREFLE_TOKEN` — enter the account token privately in Render |

Render sets `PORT` and `RENDER=true`; the server then binds `0.0.0.0` and the assigned port. A non-Render host can set `HOST=0.0.0.0` explicitly. `render.yaml` records equivalent settings for a new Blueprint; it does not change settings of an existing manually created service.

After pushing code, check Render's deployment status and commit ID. Use **Manual Deploy → Deploy latest commit** if auto-deploy is unavailable. Do not use `plant search` as the start command: that is not an executable.

Never put the Trefle token in Java, Gradle, screenshots, commits or the app. Save it only as Render's protected `TREFLE_TOKEN` environment variable. The Android app sends plant names and result page numbers to this service; the service adds the account token only in an upstream authorization header. It does not log search terms or tokens or store user profiles.

## Operation and limits

- `GET /health` reports the service name and whether a token is configured, without revealing it or contacting Trefle.
- `GET /plants/search?q=aloe&page=1` returns six normalized results with botanical facts and credits.
- This is a **public, read-only class demo**, not an authenticated private service. Anyone knowing its URL can query it. Do not send private user information to it. An Origin-header restriction is not authentication.
- A shared cap of thirty searches per minute, four concurrent searches and fifty upstream API requests per minute limits use of the account quota. Cached Trefle records last ten minutes; simultaneous identical upstream requests are deduplicated. Rate limits apply to the whole class and reset on restart. An outside caller can still consume the shared allowance; production use would need real user authentication and durable quota controls.
- Trefle retry delays and safe error messages are passed to the app. No generic URL forwarding, writes or token-setting endpoint is provided.
- Render Free sleeps after fifteen minutes of inactivity and can take about a minute to wake. Android shows a loading notice and allows two minutes for a response. If it still fails, retry. The sixteen starter guides and photos remain available offline.
- No persistent disk or database is required. Losing the in-memory cache on sleep or redeploy is expected. Hosting and Trefle usage limits still apply; the code does not generate keep-alive traffic.

The server fetches species details to obtain image copyright metadata before showing photos. Missing names use scientific names. Missing photo credits or a missing reusable-photo license use a leaf placeholder. Ecological habitat indicators are not converted into indoor watering schedules.

## Optional local development

Store a local account token as one line in `~/.config/worldplant-search/trefle.token` (directory mode 700, file mode 600). A different private file can be selected with `TREFLE_TOKEN_FILE`. From the repository root:

```sh
./server/start.sh
./gradlew :app:assembleDebug -Ptrefle.localDemo=true
```

The script uses installed Node or this Mac's bundled Node runtime. Local runs bind only `127.0.0.1:8765`. The explicit Gradle property selects `http://10.0.2.2:8765` for a debug emulator; it never changes release builds. HTTP is permitted only for this emulator host in debug builds. Normal Android Studio runs use the shared HTTPS server.

To use a different HTTPS service, set `trefle.baseUrl=https://YOUR-SERVER.example` in ignored `local.properties`, then rebuild. This is a public URL, not an API token.

## Tests and offline refresh

```sh
node --test server/trefle.test.mjs
node server/refresh-starters.mjs
```

The refresh command needs a token and updates sixteen bundled botanical profiles and source metadata. It preserves the separately credited photos and brief NC State care notes. A broad genus without an exact species match keeps an original care overview rather than using a different species.

References: [Render service setup](https://render.com/docs/web-services), [Render Free limits](https://render.com/docs/free), Trefle [search](https://docs.trefle.io/docs/guides/searching/), [fields](https://docs.trefle.io/docs/advanced/plants-fields/), [credentials](https://docs.trefle.io/docs/advanced/client-side-apps/), [attribution](https://trefle.io/terms).
