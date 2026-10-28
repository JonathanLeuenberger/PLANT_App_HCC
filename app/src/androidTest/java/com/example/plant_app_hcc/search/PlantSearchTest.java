package com.example.plant_app_hcc.search;

import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import android.content.Context;
import android.content.Intent;
import android.widget.ListView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.plant_app_hcc.MainActivity;
import com.example.plant_app_hcc.R;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class PlantSearchTest {
  @Test
  public void offlineCatalogHasImagesCreditsAndAliases() throws Exception {
    Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();
    PlantCatalog catalog = new PlantCatalog(c);
    try {
      assertEquals(16, catalog.localSearch("").size());
      assertEquals("Aloe vera", catalog.localSearch("aloe vera").get(0).name);
      assertEquals(1, catalog.localSearch("EPIPREMNUM AUREUM").size());
      assertTrue(catalog.localSearch("zzzzunmatched").isEmpty());
      for (Species plant : catalog.all) {
        assertFalse(plant.photoCredit.isEmpty());
        assertTrue(plant.photoSource.startsWith("https://"));
        assertFalse(plant.photoLicense.isEmpty());
        try (java.io.InputStream in = c.getAssets().open(plant.image.substring(6))) {
          assertNotNull(android.graphics.BitmapFactory.decodeStream(in));
        }
        assertEquals(plant.name, Species.fromJson(plant.toJson()).name);
      }
    } finally {
      catalog.close();
    }
  }

  @Test
  public void searchDetailsBackClearAndRecreation() {
    Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();
    try (ActivityScenario<MainActivity> scenario =
        ActivityScenario.launch(new Intent(c, MainActivity.class))) {
      // Test-only window flags let an unattended emulator wake for the screen checks.
      scenario.onActivity(
          activity ->
              activity
                  .getWindow()
                  .addFlags(
                      android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                          | android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                          | android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                          | android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD));
      onView(withId(R.id.plant_search_input)).perform(replaceText("aloe"), closeSoftKeyboard());
      onView(withId(R.id.plant_search_results))
          .check((view, error) -> assertEquals(1, ((ListView) view).getAdapter().getCount()));
      onData(anything())
          .inAdapterView(withId(R.id.plant_search_results))
          .atPosition(0)
          .perform(click());
      onView(withId(R.id.plant_details_title)).check(matches(withText("Aloe vera")));
      pressBack();
      onView(withId(R.id.plant_search_input)).check(matches(withText("aloe")));
      scenario.recreate();
      onView(withId(R.id.plant_search_input)).check(matches(withText("aloe")));
      onView(withId(R.id.plant_search_clear)).perform(click());
      onView(withId(R.id.plant_search_input)).check(matches(withText("")));
      onView(withId(R.id.plant_search_results))
          .check((view, error) -> assertEquals(16, ((ListView) view).getAdapter().getCount()));
      onView(withId(R.id.plant_search_input))
          .perform(replaceText("zzzzunmatched"), closeSoftKeyboard());
      onView(withText("No matching plants. Try another name or search online."))
          .check(matches(isDisplayed()));
    }
  }

  /** Integration smoke test; requires internet access to the real Wikipedia service. */
  @Test
  public void liveWikipediaSearch() throws Exception {
    Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();
    PlantCatalog catalog = new PlantCatalog(c);
    CountDownLatch ready = new CountDownLatch(1);
    AtomicReference<List<Species>> found = new AtomicReference<>();
    AtomicReference<String> error = new AtomicReference<>();
    try {
      catalog.searchWikipedia(
          "Begonia",
          (plants, failure) -> {
            found.set(plants);
            error.set(failure);
            ready.countDown();
          });
      assertTrue("Wikipedia response timed out", ready.await(35, TimeUnit.SECONDS));
      assertNull(error.get());
      assertFalse(found.get().isEmpty());
      assertTrue(
          found.get().stream()
              .anyMatch(p -> p.name.toLowerCase(java.util.Locale.ROOT).contains("begonia")));
      assertTrue(
          found.get().stream()
              .anyMatch(p -> p.image.startsWith("https://") && !p.photoSource.isEmpty()));
    } finally {
      catalog.close();
    }
  }
}
