package com.example.plant_app_hcc.search;

import static org.junit.Assert.*;

import org.junit.Test;

public class SearchTextTest {
  @Test
  public void namesIgnoreCaseAndWhitespace() {
    assertTrue(SearchText.matches("  ALOE VERA ", "Aloe vera", "Aloe barbadensis"));
    assertTrue(SearchText.matches("aloevera", "Aloe vera"));
    assertTrue(SearchText.matches("snake-plant", "Snake plant"));
  }

  @Test
  public void matchesScientificNameOrAlias() {
    assertTrue(
        SearchText.matches("epipremnum", "Golden pothos", "Epipremnum aureum", "Devil's ivy"));
    assertTrue(
        SearchText.matches("devils ivy", "Golden pothos", "Epipremnum aureum", "Devil's ivy"));
    assertFalse(SearchText.matches("rose", "Golden pothos", "Epipremnum aureum"));
  }

  @Test
  public void blankQueryShowsTheStarterCollection() {
    assertTrue(SearchText.matches("   ", "Aloe vera"));
  }
}
