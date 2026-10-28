package com.example.plant_app_hcc.search;

import java.util.Locale;

/** Common/scientific name matching ignores case, extra spaces and punctuation. */
public final class SearchText {
  private SearchText() {}

  public static String normalize(String text) {
    return text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
  }

  public static boolean matches(String query, String... names) {
    String key = normalize(query);
    for (String name : names) if (normalize(name).contains(key)) return true;
    return false;
  }
}
