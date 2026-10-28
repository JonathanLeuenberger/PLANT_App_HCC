# Search feature verification

Verified on October 28, 2026, including the switch from Wikipedia search to Trefle.

- Debug build and Android lint pass with zero lint errors. Existing dependency/template and English-only interface warnings remain.
- Four JVM unit tests pass (three search matching tests and the existing template test).
- Four Android 11/API 30 device tests pass: offline catalog/assets/credits; search → details → back → recreation → clear → empty results; a live Trefle query through the private server; existing template context check.
- Six Node server tests pass: missing names/fields; photo license/credit fallback; caching and reflected-token redaction; invalid credentials and rate limits; pagination across upstream page boundaries; safe upstream error responses.
- A live aloe query returned six plant records, including Aloe vera, with scientific names and available credited photos. Fourteen of sixteen bundled profiles were refreshed from Trefle; the two broad genus entries retain original project care overviews.
- Real-token scans found zero occurrences in repository files. The token stays outside the repository and is not compiled into the APK.
- Photo downloads and decoded sizes are bounded for full-resolution Trefle images.
- Manual emulator check passed: type aloe, search online, open Barbados aloe with its upper-right photo, then return to the same results.
- `git diff --check` passes.

Online search requires internet and the private server running on the computer hosting the emulator. This is a local demonstration setup, not a publicly hosted service. Physical-phone and release deployment need an HTTPS service as explained in `server/README.md`. Missing indoor care fields are not inferred from ecological habitat indicators. No garden, task, calendar or challenge implementation is included.
