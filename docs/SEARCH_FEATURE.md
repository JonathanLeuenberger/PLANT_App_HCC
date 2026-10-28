# Plant search contribution

This contribution implements only plant search and read-only plant information. It does not include the full WorldPlant garden, tasks, calendar, challenges, accounts or plant saving.

## Run and demonstrate

Open this repository folder in Android Studio, allow Gradle to sync, select an emulator and run **app**. The search screen is the start destination for this branch, because the repository currently contains only the Android starter template.

1. Type **aloe**. The starter collection filters immediately, showing a photo, common name and scientific name.
2. Tap **Aloe vera**. A read-only page displays its picture at the upper right, description, water/light notes and source credits.
3. Go back. The previous query and results remain. **Clear** restores all 16 starter profiles and dismisses the keyboard.
4. Type **Begonia** and select **Search Wikipedia online**, or use the keyboard Search key. The keyboard closes and matching Wikipedia articles appear with available thumbnails.
5. Try an unmatched name. An empty-results message explains how to continue. If online search fails, existing results remain available and the search button can be retried.

The 16 starter profiles and their photos work offline. Online results require internet and no API key. Wikipedia's full-text search may return related botanical topics as well as species; the app labels these as Wikipedia articles and does not invent scientific names or care instructions. Photo identification is outside this contribution.

## Files and responsibilities

All Java feature classes are under `app/src/main/java/com/example/plant_app_hcc/search/`:

- `PlantSearchFragment`: search field, Clear, local filtering, online request, result list, navigation and saved search state.
- `PlantDetailsFragment`: read-only plant profile, upper-right picture and source links.
- `PlantCatalog`: offline JSON and asynchronous Wikipedia API requests, with image-credit metadata.
- `SearchText`: case/spacing/punctuation-insensitive matching against common names, scientific names and aliases.
- `Species`: result data and JSON serialization.
- `Images`: background thumbnail loading with a leaf fallback and bounded memory/disk caches.
- `Ui`: small shared native view helpers; no garden or other feature logic.

Assets live under `app/src/main/assets/plantsearch/`, so they can be moved with this feature without conflicting with other teams' assets. The contribution adds no third-party app dependencies or credentials. Existing Kotlin activities and fragments remain Kotlin; Android supports Java and Kotlin in the same app.

## Team integration

`nav_graph.xml` adds `PlantSearchFragment` and `PlantDetailsFragment`. The only activity change hides the starter template's email button on these two screens. The manifest adds `INTERNET` permission. Other starter screens are retained.

When the team adds My Garden, change the navigation graph's `startDestination` to its home destination and navigate to `R.id.PlantSearchFragment` from the home Search button. No garden database or other features are required to launch search.

For Kotlin inside a Fragment:

```kotlin
findNavController().navigate(R.id.PlantSearchFragment)
```

For Java inside a Fragment:

```java
NavHostFragment.findNavController(this).navigate(R.id.PlantSearchFragment);
```

Descriptions and photos keep their separate content licenses. See [content credits](SEARCH_THIRD_PARTY.md) and [bundled source metadata](SEARCH_PLANT_SOURCES.json). API references: [MediaWiki search](https://www.mediawiki.org/wiki/API:Search) and [page thumbnails](https://www.mediawiki.org/wiki/API:Pageimages).

## Verification commands

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

`SearchTextTest` verifies name/alias matching. `PlantSearchTest` verifies bundled assets/credits and the search → details → back → recreation → clear → empty-results workflow on an emulator. The device suite includes a live Wikipedia test and needs internet. To run that test alone:

```sh
adb shell am instrument -w \
  -e class com.example.plant_app_hcc.search.PlantSearchTest#liveWikipediaSearch \
  com.example.plant_app_hcc.test/androidx.test.runner.AndroidJUnitRunner
```

Online text and thumbnail responses are bounded and fetched on background threads. Editing the query or destroying the screen invalidates old responses. Rotation preserves completed results; an interrupted request can be retried.
