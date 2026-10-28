package com.example.plant_app_hcc.search;

import org.json.JSONException;
import org.json.JSONObject;

/** A plant search result, independent of the garden. */
public final class Species {
  public String id = "", name = "", scientific = "", description = "";
  public String water = "", light = "", careUrl = "", image = "";
  public String photoCredit = "", photoLicense = "", photoLicenseUrl = "", photoSource = "";
  public String aliases = "";
  public String sourceName = "", sourceUrl = "", dataCredits = "";
  public boolean curated;

  public static Species fromJson(JSONObject j) {
    Species s = new Species();
    s.id = j.optString("id");
    s.name = j.optString("name");
    s.scientific = j.optString("scientific");
    s.description = j.optString("description");
    s.water = j.optString("water");
    s.light = j.optString("light");
    s.careUrl = j.optString("careUrl");
    s.image = j.optString("image");
    s.aliases = j.optString("aliases");
    s.sourceName = j.optString("sourceName");
    s.sourceUrl = j.optString("sourceUrl");
    s.dataCredits = j.optString("dataCredits");
    s.photoCredit = j.optString("photoCredit");
    s.photoLicense = j.optString("photoLicense");
    s.photoLicenseUrl = j.optString("photoLicenseUrl");
    s.photoSource = j.optString("photoSource");
    s.curated = j.optBoolean("curated");
    return s;
  }

  public JSONObject toJson() {
    JSONObject j = new JSONObject();
    try {
      j.put("id", id)
          .put("name", name)
          .put("scientific", scientific)
          .put("description", description)
          .put("water", water)
          .put("light", light)
          .put("careUrl", careUrl)
          .put("image", image)
          .put("photoCredit", photoCredit)
          .put("photoLicense", photoLicense)
          .put("photoLicenseUrl", photoLicenseUrl)
          .put("photoSource", photoSource)
          .put("aliases", aliases)
          .put("sourceName", sourceName)
          .put("sourceUrl", sourceUrl)
          .put("dataCredits", dataCredits)
          .put("curated", curated);
    } catch (JSONException impossible) {
      throw new IllegalStateException(impossible);
    }
    return j;
  }
}
