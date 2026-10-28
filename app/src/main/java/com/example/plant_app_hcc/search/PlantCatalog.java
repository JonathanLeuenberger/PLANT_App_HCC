package com.example.plant_app_hcc.search;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;

/** Offline guides plus Trefle search through our private-token server. */
public class PlantCatalog {
  public interface SearchCallback {
    void result(List<Species> plants, boolean hasMore, String error);
  }

  public final List<Species> all = new ArrayList<>();
  private final ExecutorService network = Executors.newSingleThreadExecutor();
  private final Handler main = new Handler(Looper.getMainLooper());

  public PlantCatalog(Context context) {
    try (InputStream in = context.getAssets().open("plantsearch/catalog.json")) {
      JSONArray data = new JSONArray(readText(in));
      for (int i = 0; i < data.length(); i++) all.add(Species.fromJson(data.getJSONObject(i)));
    } catch (Exception e) {
      throw new IllegalStateException("The bundled plant catalog could not be loaded", e);
    }
  }

  public List<Species> localSearch(String query) {
    List<Species> found = new ArrayList<>();
    for (Species s : all)
      if (SearchText.matches(query, s.name, s.scientific, s.aliases)) found.add(s);
    return found;
  }

  public Species byId(String id) {
    for (Species s : all) if (s.id.equals(id)) return s;
    return null;
  }

  public void searchTrefle(String query, int page, SearchCallback callback) {
    network.execute(
        () -> {
          List<Species> result = new ArrayList<>();
          String error = null;
          boolean hasMore = false;
          try {
            String base = com.example.plant_app_hcc.BuildConfig.TREFLE_BASE_URL;
            if (base.isEmpty())
              throw new IllegalStateException("Online plant search is not configured.");
            String endpoint = base + "/plants/search?q=" + encode(query) + "&page=" + page;
            HttpURLConnection c = openSearch(endpoint);
            try {
              int status = c.getResponseCode();
              InputStream response = status >= 400 ? c.getErrorStream() : c.getInputStream();
              if (response == null) throw new java.io.IOException("Empty server response");
              JSONObject data;
              try (InputStream in = response) {
                data = new JSONObject(readText(in));
              }
              if (status >= 400)
                throw new IllegalStateException(
                    data.optString("error", "Plant search is unavailable."));
              JSONArray rows = data.getJSONArray("data");
              for (int i = 0; i < rows.length(); i++) {
                Species plant = Species.fromJson(rows.getJSONObject(i));
                // Our brief houseplant care guidance remains separately credited to NC State.
                for (Species known : all) {
                  if (!SearchText.normalize(plant.scientific)
                      .equals(SearchText.normalize(known.scientific))) continue;
                  plant.water = known.water;
                  plant.light = known.light;
                  plant.careUrl = known.careUrl;
                  plant.curated = known.curated;
                  if (plant.image.isEmpty()) {
                    plant.image = known.image;
                    plant.photoCredit = known.photoCredit;
                    plant.photoLicense = known.photoLicense;
                    plant.photoSource = known.photoSource;
                  }
                  break;
                }
                result.add(plant);
              }
              JSONObject meta = data.optJSONObject("meta");
              hasMore = meta != null && meta.optBoolean("hasMore");
            } finally {
              c.disconnect();
            }
          } catch (IllegalStateException e) {
            error = e.getMessage();
          } catch (Exception e) {
            error =
                "Online search is unavailable. Please try again. The starter library still works.";
          }
          final String failure = error;
          final boolean more = hasMore;
          main.post(() -> callback.result(result, more, failure));
        });
  }

  private static HttpURLConnection openSearch(String address) throws Exception {
    URL url = new URL(address);
    boolean localDebug =
        com.example.plant_app_hcc.BuildConfig.DEBUG
            && "http".equals(url.getProtocol())
            && "10.0.2.2".equals(url.getHost());
    if (!"https".equals(url.getProtocol()) && !localDebug)
      throw new IllegalArgumentException("HTTPS required");
    HttpURLConnection c = (HttpURLConnection) url.openConnection();
    c.setConnectTimeout(8000);
    c.setReadTimeout(45000);
    c.setInstanceFollowRedirects(false);
    c.setRequestProperty("Accept", "application/json");
    return c;
  }

  public static String encode(String text) {
    return encodeUtf8(text);
  }

  private static String encodeUtf8(String text) {
    try {
      return URLEncoder.encode(text, "UTF-8").replace("+", "%20");
    } catch (java.io.UnsupportedEncodingException e) {
      throw new AssertionError(e);
    }
  }

  public static JSONObject getJson(String address) throws Exception {
    HttpURLConnection connection = open(address);
    try (InputStream in = connection.getInputStream()) {
      return new JSONObject(readText(in));
    } finally {
      connection.disconnect();
    }
  }

  public static HttpURLConnection open(String address) throws Exception {
    if (!address.startsWith("https://")) throw new IllegalArgumentException("HTTPS is required");
    HttpURLConnection c = (HttpURLConnection) new URL(address).openConnection();
    c.setConnectTimeout(8000);
    c.setReadTimeout(8000);
    c.setRequestProperty("User-Agent", "WorldPlant/1.0 (Android educational prototype)");
    return c;
  }

  public static String readText(InputStream in) throws java.io.IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buffer = new byte[8192];
    int count;
    while ((count = in.read(buffer)) != -1) {
      out.write(buffer, 0, count);
      if (out.size() > 4_000_000) throw new java.io.IOException("Response too large");
    }
    return out.toString("UTF-8");
  }

  public void close() {
    network.shutdownNow();
  }
}
