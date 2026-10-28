# Private Trefle connection

This small Node.js server connects the Android search feature to Trefle. It uses only Node's built-in modules; no package installation is needed. Use Node.js 20 or newer.

## Private token

Store your account token as a single line in `~/.config/worldplant-search/trefle.token`. On this Mac it is already configured. It is outside the repository and is never included in the APK or source ZIP. A different private file can be selected with `TREFLE_TOKEN_FILE`; a hosted server can use the `TREFLE_TOKEN` environment secret. Never put the token in Java, Gradle, screenshots or commits.

## Emulator demo

From the repository root, run:

```sh
./server/start.sh
```

The script finds an installed Node runtime, or this Mac's bundled Codex Node runtime. Alternatively, with Node.js 20 or newer installed:

```sh
node server/trefle.mjs
```

Keep the server running during online searches. It listens only on `127.0.0.1:8765`. The Android emulator reaches this computer through `10.0.2.2`; debug builds already use that address. No account token is sent to Android.

- `GET /health` reports whether a token is configured without revealing it.
- `GET /plants/search?q=aloe&page=1` returns six normalized results with botanical details and credits.

The server fetches detail records to obtain image copyright metadata before showing photos. It caches responses for ten minutes, deduplicates pending requests, limits upstream requests to fifty per minute and respects Trefle's retry delay. Missing names use scientific names. Missing photo credits or a missing reusable-photo license use a leaf placeholder. Ecological habitat indicators are not converted into indoor watering schedules.

## Sharing later

This local demo server works with the emulator on the same computer. A teammate can run their own local server with their own private Trefle token.

Physical phones and distributed builds require a hosted HTTPS service with appropriate access control and rate limits. Set `trefle.baseUrl=https://YOUR-SERVER.example` in ignored `local.properties` before building. Release builds have no HTTP exception or default server URL. The token belongs in the server's private environment, never in the app or `local.properties`. Public hosting is not configured by this change.

## Tests and offline refresh

```sh
node --test server/trefle.test.mjs
node server/refresh-starters.mjs
```

The refresh command needs a token and updates sixteen bundled botanical profiles and source metadata. It preserves the separately credited photos and brief NC State care notes. A broad genus without an exact species match keeps an original care overview rather than using a different species.

API references: [search](https://docs.trefle.io/docs/guides/searching/), [fields](https://docs.trefle.io/docs/advanced/plants-fields/), [credentials](https://docs.trefle.io/docs/advanced/client-side-apps/), [attribution](https://trefle.io/terms).
