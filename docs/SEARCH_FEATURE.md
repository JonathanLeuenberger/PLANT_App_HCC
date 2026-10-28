# Plant search contribution

Java plant search and read-only plant information, independent of garden, tasks, calendar or challenges. Existing Kotlin screens can navigate to the Java fragments.

## Run and demonstrate

1. Start the private Trefle server with `./server/start.sh` from the repository root. See [server setup](../server/README.md). This Mac's account token is already stored privately outside the repository.
2. Open the repository folder in Android Studio and run **app** on an emulator. Search is the current opening screen.
3. Type **aloe** to filter sixteen offline starter guides. Tap **Aloe vera** for its upper-right photo, botanical details, care overview and sources.
4. Go back and select **Search plants online**. Trefle returns species records with common/scientific names and credited photos where available. Tap a result for its profile; **More plants** loads another page.
5. **Clear** restores the starter collection. The keyboard closes when searching, clearing or opening a plant. Returning from details and recreating the activity preserve completed search results.

Online queries use Trefle. Fourteen offline botanical summaries were also refreshed from Trefle. The broad Peace Lily (`Spathiphyllum`) and Moth Orchid (`Phalaenopsis`) entries use original care overviews because no exact species could be assigned. The bundled photos remain credited Wikimedia images, and brief care notes remain separately informed by NC State Extension.

The starter guides/photos work offline. Expanded search requires internet and the private server. Missing names use scientific names and missing or unlicensed photos use a leaf placeholder. Trefle habitat indicators do not imply an indoor watering schedule. Photo identification and plant saving are outside this contribution.

## Files

Java classes in `app/src/main/java/com/example/plant_app_hcc/search/`:

- `PlantSearchFragment`: query, Clear, local filtering, Trefle search, pagination, results and saved state.
- `PlantDetailsFragment`: read-only profile, upper-right photo and source credits.
- `PlantCatalog`: bundled JSON and background calls to the private Trefle server; known starter species retain separately sourced care notes.
- `SearchText`: matches common/scientific names and aliases across case, spacing and punctuation.
- `Species`: result data and JSON serialization, including provider and credits.
- `Images`: background loading, bounded caches and placeholder images.
- `Ui`: shared native view helpers.

Bundled assets are under `app/src/main/assets/plantsearch/`. Server code under `server/` needs no third-party Node packages. The token is not in the app source, APK or repository.

## Team integration

`nav_graph.xml` declares `PlantSearchFragment` and `PlantDetailsFragment`. `MainActivity.kt` hides the starter template's email button on these screens. The manifest declares internet access. Other template screens are retained.

When My Garden is ready, set it as the navigation graph's `startDestination` and connect its Search button with:

```kotlin
findNavController().navigate(R.id.PlantSearchFragment)
```

Or from Java:

```java
NavHostFragment.findNavController(this).navigate(R.id.PlantSearchFragment);
```

Debug builds use `http://10.0.2.2:8765` unless an HTTPS `trefle.baseUrl` is set in ignored `local.properties`. HTTP is allowed only for the emulator host in debug builds. Release builds require an explicitly configured HTTPS service. See [server deployment notes](../server/README.md) before distributing to physical phones.

## Verification

```sh
node --test server/trefle.test.mjs
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

Device tests require a running server and valid token. They cover offline assets/credits, search → details → back → recreation → clear → empty results and a real Trefle query. Server tests cover pagination, missing fields, attribution, caching, invalid credentials and rate limits.

See [content credits](SEARCH_THIRD_PARTY.md) and [bundled source metadata](SEARCH_PLANT_SOURCES.json).
