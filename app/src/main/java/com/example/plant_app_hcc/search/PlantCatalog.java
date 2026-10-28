package com.example.plant_app_hcc.search;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;

/** Bundled profiles work offline; explicit searches expand the library through Wikipedia. */
public class PlantCatalog {
  public interface SearchCallback {
    void result(List<Species> plants, String error);
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
    String q = query.toLowerCase(Locale.ROOT).trim();
    List<Species> found = new ArrayList<>();
    for (Species s : all)
      if ((s.name + " " + s.scientific + " " + s.aliases).toLowerCase(Locale.ROOT).contains(q))
        found.add(s);
    return found;
  }

  public Species byId(String id) {
    for (Species s : all) if (s.id.equals(id)) return s;
    return null;
  }

  public void searchWikipedia(String query, SearchCallback callback) {
    network.execute(
        () -> {
          List<Species> result = new ArrayList<>();
          String error = null;
          try {
            String endpoint =
                "https://en.wikipedia.org/w/api.php?action=query&format=json&formatversion=2"
                    + "&generator=search&gsrnamespace=0&gsrlimit=10&gsrsearch="
                    + encode(query + " plant")
                    + "&prop=pageimages%7Cextracts%7Cinfo&inprop=url&piprop=thumbnail%7Cname&pilicense=free"
                    + "&pithumbsize=600&exintro=1&explaintext=1&exlimit=max&exchars=1600";
            JSONObject response = getJson(endpoint);
            if (response.has("error"))
              throw new IllegalStateException("Wikipedia returned an error");
            JSONObject queryResult = response.optJSONObject("query");
            JSONArray pages = queryResult == null ? null : queryResult.optJSONArray("pages");
            if (pages != null) {
              List<JSONObject> ordered = new ArrayList<>();
              for (int i = 0; i < pages.length(); i++) ordered.add(pages.getJSONObject(i));
              ordered.sort((a, b) -> Integer.compare(a.optInt("index"), b.optInt("index")));
              // Fetch credits in one request so ten results don't require ten round trips.
              Map<String, JSONObject> imageCredits = new HashMap<>();
              List<String> filenames = new ArrayList<>();
              for (JSONObject page : ordered) {
                String filename = page.optString("pageimage");
                if (!filename.isEmpty()) filenames.add("File:" + filename);
              }
              if (!filenames.isEmpty()) {
                try {
                  JSONObject credits =
                      getJson(
                          "https://en.wikipedia.org/w/api.php?action=query&format=json&formatversion=2&prop=imageinfo&iiprop=extmetadata%7Curl&titles="
                              + encode(android.text.TextUtils.join("|", filenames)));
                  JSONArray creditPages = credits.getJSONObject("query").getJSONArray("pages");
                  for (int i = 0; i < creditPages.length(); i++) {
                    JSONObject page = creditPages.getJSONObject(i);
                    JSONArray info = page.optJSONArray("imageinfo");
                    if (info != null && info.length() > 0)
                      imageCredits.put(
                          page.optString("title").replace('_', ' '), info.getJSONObject(0));
                  }
                } catch (Exception ignored) {
                  /* Link each original file page if metadata is unavailable. */
                }
              }
              for (JSONObject page : ordered) {
                String title = page.optString("title");
                Species known = null;
                for (Species s : all)
                  if (s.wikiUrl.endsWith(encode(title.replace(' ', '_')).replace("+", "%20"))) {
                    known = s;
                    break;
                  }
                if (known != null) {
                  result.add(known);
                  continue;
                }
                Species s = new Species();
                s.id = "wiki:" + page.optLong("pageid");
                s.name = title;
                s.scientific = "Wikipedia plant article";
                s.description = page.optString("extract");
                s.wikiUrl =
                    page.optString(
                        "fullurl",
                        "https://en.wikipedia.org/wiki/" + encode(title.replace(' ', '_')));
                JSONObject thumbnail = page.optJSONObject("thumbnail");
                if (thumbnail != null) s.image = thumbnail.optString("source");
                String filename = page.optString("pageimage");
                if (!filename.isEmpty()) {
                  s.photoSource = "https://commons.wikimedia.org/wiki/File:" + encode(filename);
                  s.photoCredit =
                      "Wikimedia contributors — see image source for author and license";
                  s.photoLicense = "See image source";
                  JSONObject imageInfo = imageCredits.get(("File:" + filename).replace('_', ' '));
                  if (imageInfo != null) {
                    JSONObject metadata = imageInfo.optJSONObject("extmetadata");
                    if (metadata != null) {
                      String author = value(metadata, "Artist"),
                          license = value(metadata, "LicenseShortName");
                      if (!author.isEmpty()) s.photoCredit = author;
                      if (!license.isEmpty()) s.photoLicense = license;
                      s.photoLicenseUrl = value(metadata, "LicenseUrl");
                      s.photoSource = imageInfo.optString("descriptionurl", s.photoSource);
                    }
                  }
                }
                result.add(s);
              }
            }
          } catch (Exception e) {
            error =
                "Online search is unavailable. The offline still work."
                    + " Check your connection and try again.";
          }
          String finalError = error;
          main.post(() -> callback.result(result, finalError));
        });
  }

  private static String value(JSONObject j, String key) {
    JSONObject v = j.optJSONObject(key);
    return v == null
        ? ""
        : Html.fromHtml(v.optString("value"), Html.FROM_HTML_MODE_LEGACY).toString().trim();
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
