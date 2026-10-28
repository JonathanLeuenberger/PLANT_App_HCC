package com.example.plant_app_hcc.search;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import com.example.plant_app_hcc.R;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;

/** Search-only contribution; no dependency on garden, tasks or challenges. */
public final class PlantSearchFragment extends Fragment {
  private PlantCatalog catalog;
  private Images images;
  private EditText input;
  private TextView status;
  private Button online;
  private Button more;
  private ListView list;
  private List<Species> results;
  private String query = "";
  private String message = "16 starter plants · search by common or scientific name";
  private int request, firstPosition, firstOffset;
  private boolean loading;
  private boolean hasMore;
  private int resultPage = 1;

  @Override
  public void onCreate(Bundle saved) {
    super.onCreate(saved);
    if (saved == null) return;
    query = saved.getString("query", "");
    message = saved.getString("message", message);
    firstPosition = saved.getInt("position");
    firstOffset = saved.getInt("offset");
    resultPage = saved.getInt("page", 1);
    hasMore = saved.getBoolean("hasMore");
    try {
      JSONArray rows = new JSONArray(saved.getString("results", "[]"));
      results = new ArrayList<>();
      for (int i = 0; i < rows.length(); i++) results.add(Species.fromJson(rows.getJSONObject(i)));
    } catch (org.json.JSONException ignored) {
      results = null;
    }
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle saved) {
    Context c = requireContext();
    catalog = new PlantCatalog(c);
    images = new Images(c);
    LinearLayout root = Ui.column(c);
    root.setBackgroundColor(Ui.BG);
    root.setPadding(Ui.dp(c, 20), Ui.dp(c, 12), Ui.dp(c, 20), 0);
    root.setFocusableInTouchMode(true);
    root.addView(Ui.text(c, "Find a plant", 28, Ui.INK, true));
    root.addView(
        Ui.text(c, "Explore the starter library or search Trefle plants.", 14, Ui.MUTED, false));
    Ui.gap(root, 12);
    LinearLayout searchRow = Ui.row(c);
    input = Ui.input(c, "Plant name, e.g. aloe vera", query, false);
    input.setId(R.id.plant_search_input);
    input.setFilters(new InputFilter[] {new InputFilter.LengthFilter(120)});
    input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
    input.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
    searchRow.addView(input, Ui.weighted(c, 8));
    Button clear =
        Ui.small(
            c,
            "Clear",
            () -> {
              input.setText("");
              hideKeyboard();
              root.requestFocus();
            });
    clear.setId(R.id.plant_search_clear);
    clear.setContentDescription("Clear plant search");
    searchRow.addView(clear);
    root.addView(searchRow);
    online = Ui.button(c, "Search plants online", true, this::searchOnline);
    online.setId(R.id.plant_search_online);
    root.addView(online);
    Ui.gap(root, 10);
    status = Ui.text(c, message, 13, Ui.MUTED, false);
    status.setId(R.id.plant_search_status);
    status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    root.addView(status);
    Ui.gap(root, 8);
    FrameLayout resultArea = new FrameLayout(c);
    list = new ListView(c);
    list.setId(R.id.plant_search_results);
    list.setDivider(null);
    list.setDividerHeight(Ui.dp(c, 8));
    list.setClipToPadding(false);
    list.setPadding(0, 0, 0, Ui.dp(c, 16));
    resultArea.addView(list, new FrameLayout.LayoutParams(-1, -1));
    TextView empty =
        Ui.text(c, "No matching plants. Try another name or search online.", 16, Ui.MUTED, false);
    empty.setGravity(android.view.Gravity.CENTER);
    resultArea.addView(empty, new FrameLayout.LayoutParams(-1, -1));
    list.setEmptyView(empty);
    root.addView(resultArea, new LinearLayout.LayoutParams(-1, 0, 1));
    more = Ui.button(c, "More plants", false, () -> searchPage(resultPage + 1));
    more.setId(R.id.plant_search_more);
    root.addView(more);
    if (results == null) results = catalog.localSearch(query);
    render();
    list.setSelectionFromTop(firstPosition, firstOffset);
    list.setOnItemClickListener(
        (p, view, position, id) -> {
          hideKeyboard();
          rememberScroll();
          Bundle args = new Bundle();
          args.putString("plant", results.get(position).toJson().toString());
          NavHostFragment.findNavController(this).navigate(R.id.PlantDetailsFragment, args);
        });
    input.addTextChangedListener(
        new TextWatcher() {
          @Override
          public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

          @Override
          public void afterTextChanged(Editable s) {}

          @Override
          public void onTextChanged(CharSequence s, int start, int before, int count) {
            // Android restores EditText after recreating a fragment view. Keep its saved results.
            if (query.equals(s.toString())) return;
            query = s.toString();
            request++; // Discard responses for an older query or a destroyed view.
            hasMore = false;
            resultPage = 1;
            setLoading(false);
            results = catalog.localSearch(query);
            message = results.size() + " starter plants · available offline";
            firstPosition = 0;
            firstOffset = 0;
            render();
          }
        });
    input.setOnEditorActionListener(
        (v, action, event) -> {
          if (action != EditorInfo.IME_ACTION_SEARCH) return false;
          searchOnline();
          return true;
        });
    root.requestFocus();
    return root;
  }

  private void searchOnline() {
    searchPage(1);
  }

  private void searchPage(int page) {
    String text = input.getText().toString().trim();
    if (text.isEmpty()) {
      input.setError("Enter a plant name");
      return;
    }
    if (loading) return;
    hideKeyboard();
    final int token = ++request;
    setLoading(true);
    status.setText("Searching Trefle… The free server may take about a minute to wake up.");
    catalog.searchTrefle(
        text,
        page,
        (found, nextPage, error) -> {
          if (getView() == null || token != request) return;
          setLoading(false);
          if (error == null) {
            if (page == 1) results = found;
            else results.addAll(found);
            resultPage = page;
            hasMore = nextPage;
          }
          message =
              error == null ? results.size() + " plants · Data: Trefle.io (CC BY 4.0)" : error;
          render();
          if (page == 1) list.setSelection(0);
        });
  }

  private void setLoading(boolean value) {
    loading = value;
    online.setEnabled(!value);
    online.setText(value ? "Searching…" : "Search plants online");
    more.setEnabled(!value);
  }

  private void render() {
    status.setText(message);
    more.setVisibility(hasMore ? View.VISIBLE : View.GONE);
    int position = list.getFirstVisiblePosition();
    int offset = list.getChildAt(0) == null ? 0 : list.getChildAt(0).getTop();
    list.setAdapter(
        new BaseAdapter() {
          @Override
          public int getCount() {
            return results.size();
          }

          @Override
          public Species getItem(int position) {
            return results.get(position);
          }

          @Override
          public long getItemId(int position) {
            return position;
          }

          @Override
          public View getView(int position, View recycled, ViewGroup parent) {
            Context c = parent.getContext();
            LinearLayout row;
            ImageView photo;
            TextView name, scientific;
            if (recycled == null) {
              row = Ui.row(c);
              row.setBackground(Ui.shape(Ui.WHITE, Ui.dp(c, 14)));
              row.setPadding(Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 12));
              photo = new ImageView(c);
              row.addView(photo, new LinearLayout.LayoutParams(Ui.dp(c, 64), Ui.dp(c, 64)));
              LinearLayout labels = Ui.column(c);
              labels.setPadding(Ui.dp(c, 12), 0, 0, 0);
              name = Ui.text(c, "", 17, Ui.INK, true);
              scientific = Ui.text(c, "", 13, Ui.MUTED, false);
              labels.addView(name);
              labels.addView(scientific);
              row.addView(labels, Ui.weighted(c, 0));
              row.setTag(new Object[] {photo, name, scientific});
            } else {
              row = (LinearLayout) recycled;
              Object[] views = (Object[]) row.getTag();
              photo = (ImageView) views[0];
              name = (TextView) views[1];
              scientific = (TextView) views[2];
            }
            Species plant = getItem(position);
            name.setText(plant.name);
            scientific.setText(plant.scientific);
            photo.setContentDescription(plant.name + " photo");
            images.load(photo, plant.image);
            return row;
          }
        });
    list.setSelectionFromTop(position, offset);
  }

  private void hideKeyboard() {
    InputMethodManager keyboard =
        (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
    keyboard.hideSoftInputFromWindow(input.getWindowToken(), 0);
    input.clearFocus();
  }

  private void rememberScroll() {
    if (list == null) return;
    firstPosition = list.getFirstVisiblePosition();
    firstOffset = list.getChildAt(0) == null ? 0 : list.getChildAt(0).getTop();
  }

  @Override
  public void onSaveInstanceState(Bundle out) {
    super.onSaveInstanceState(out);
    rememberScroll();
    out.putString("query", query);
    out.putString(
        "message", loading ? "Search interrupted. Tap Search plants online to retry." : message);
    out.putInt("position", firstPosition);
    out.putInt("offset", firstOffset);
    out.putInt("page", resultPage);
    out.putBoolean("hasMore", hasMore);
    JSONArray rows = new JSONArray();
    if (results != null) for (Species plant : results) rows.put(plant.toJson());
    out.putString("results", rows.toString());
  }

  @Override
  public void onDestroyView() {
    rememberScroll();
    request++;
    if (loading) message = "Search interrupted. Tap Search plants online to retry.";
    loading = false;
    catalog.close();
    images.close();
    input = null;
    status = null;
    online = null;
    more = null;
    list = null;
    super.onDestroyView();
  }
}
