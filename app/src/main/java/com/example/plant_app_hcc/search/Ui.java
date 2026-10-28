package com.example.plant_app_hcc.search;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

/** Shared visual language: quiet surfaces, generous touch targets, readable type. */
public final class Ui {
  public static final int BG = Color.rgb(246, 247, 242), WHITE = Color.WHITE;
  public static final int GREEN = Color.rgb(47, 99, 75), INK = Color.rgb(31, 47, 39);
  public static final int MUTED = Color.rgb(104, 116, 105), PALE = Color.rgb(231, 238, 223);
  public static final int LINE = Color.rgb(220, 226, 215), RED = Color.rgb(158, 63, 43);

  private Ui() {}

  public static int dp(Context c, float n) {
    return Math.round(n * c.getResources().getDisplayMetrics().density);
  }

  public static GradientDrawable shape(int color, int radius) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(radius);
    return d;
  }

  public static TextView text(Context c, String label, int size, int color, boolean bold) {
    TextView t = new TextView(c);
    t.setText(label);
    t.setTextSize(size);
    t.setTextColor(color);
    t.setFontFeatureSettings("kern");
    if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    t.setLineSpacing(dp(c, 2), 1);
    return t;
  }

  public static LinearLayout column(Context c) {
    LinearLayout l = new LinearLayout(c);
    l.setOrientation(LinearLayout.VERTICAL);
    return l;
  }

  public static LinearLayout row(Context c) {
    LinearLayout l = new LinearLayout(c);
    l.setOrientation(LinearLayout.HORIZONTAL);
    l.setGravity(Gravity.CENTER_VERTICAL);
    return l;
  }

  public static LinearLayout card(Context c) {
    LinearLayout l = column(c);
    l.setPadding(dp(c, 16), dp(c, 16), dp(c, 16), dp(c, 16));
    GradientDrawable bg = shape(WHITE, dp(c, 20));
    bg.setStroke(dp(c, 1), LINE);
    l.setBackground(bg);
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
    p.bottomMargin = dp(c, 12);
    l.setLayoutParams(p);
    return l;
  }

  public static void gap(LinearLayout l, int height) {
    View v = new View(l.getContext());
    l.addView(v, new LinearLayout.LayoutParams(1, dp(l.getContext(), height)));
  }

  public static Button button(Context c, String label, boolean filled, Runnable action) {
    Button b = new Button(c);
    b.setText(label);
    b.setTextSize(14);
    b.setAllCaps(false);
    b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    b.setTextColor(filled ? WHITE : GREEN);
    b.setBackground(shape(filled ? GREEN : PALE, dp(c, 13)));
    b.setMinHeight(dp(c, 48));
    b.setPadding(dp(c, 14), dp(c, 8), dp(c, 14), dp(c, 8));
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(c, 48));
    p.topMargin = dp(c, 8);
    b.setLayoutParams(p);
    b.setOnClickListener(v -> action.run());
    return b;
  }

  public static Button small(Context c, String label, Runnable action) {
    Button b = button(c, label, false, action);
    b.setTextSize(12);
    b.setLayoutParams(new LinearLayout.LayoutParams(-2, dp(c, 48)));
    return b;
  }

  public static EditText input(Context c, String hint, String initial, boolean multiline) {
    EditText e = new EditText(c);
    e.setHint(hint);
    e.setText(initial);
    e.setTextSize(16);
    e.setTextColor(INK);
    e.setHintTextColor(MUTED);
    e.setPadding(dp(c, 12), dp(c, 10), dp(c, 12), dp(c, 10));
    e.setSingleLine(!multiline);
    e.setBackground(shape(BG, dp(c, 12)));
    e.setContentDescription(hint);
    e.setLayoutParams(new LinearLayout.LayoutParams(-1, multiline ? dp(c, 90) : dp(c, 52)));
    return e;
  }

  public static void note(LinearLayout l, String title, String message) {
    LinearLayout box = card(l.getContext());
    box.setBackground(shape(PALE, dp(l.getContext(), 18)));
    box.addView(text(l.getContext(), title, 15, GREEN, true));
    gap(box, 5);
    box.addView(text(l.getContext(), message, 14, INK, false));
    l.addView(box);
  }

  public static LinearLayout.LayoutParams weighted(Context c, int rightMargin) {
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
    p.rightMargin = dp(c, rightMargin);
    return p;
  }

  public static ScrollView scroll(Context c, LinearLayout content) {
    ScrollView s = new ScrollView(c);
    s.setFillViewport(false);
    s.addView(content);
    return s;
  }
}
