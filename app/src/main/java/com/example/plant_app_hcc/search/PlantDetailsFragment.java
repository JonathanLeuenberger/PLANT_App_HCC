package com.example.plant_app_hcc.search;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.fragment.app.Fragment;
import com.example.plant_app_hcc.R;
import org.json.JSONObject;

/** Read-only plant information with its photo at the upper right. */
public final class PlantDetailsFragment extends Fragment {
  private Images images;

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle saved) {
    android.content.Context c = requireContext();
    LinearLayout body = Ui.column(c);
    body.setPadding(Ui.dp(c, 20), Ui.dp(c, 16), Ui.dp(c, 20), Ui.dp(c, 24));
    body.setBackgroundColor(Ui.BG);
    Species plant;
    try {
      plant = Species.fromJson(new JSONObject(requireArguments().getString("plant", "")));
    } catch (org.json.JSONException e) {
      Ui.note(body, "Plant unavailable", "Go back to search and select a plant.");
      return Ui.scroll(c, body);
    }
    images = new Images(c);
    LinearLayout portrait = Ui.row(c), identity = Ui.column(c);
    identity.addView(Ui.text(c, "PLANT PROFILE", 12, Ui.GREEN, true));
    Ui.gap(identity, 8);
    TextView title = Ui.text(c, plant.name, 23, Ui.INK, true);
    title.setId(R.id.plant_details_title);
    identity.addView(title);
    identity.addView(Ui.text(c, plant.scientific, 14, Ui.MUTED, false));
    portrait.addView(identity, Ui.weighted(c, 12));
    ImageView photo = new ImageView(c);
    photo.setContentDescription(plant.name + " photo");
    portrait.addView(photo, new LinearLayout.LayoutParams(Ui.dp(c, 120), Ui.dp(c, 140)));
    images.load(photo, plant.image);
    body.addView(portrait);
    Ui.gap(body, 16);
    Ui.note(
        body,
        "About this plant",
        plant.description.isEmpty()
            ? "No summary is available. Open the original article below."
            : plant.description);
    if (plant.curated) {
      Ui.note(body, "Water / soil", plant.water);
      Ui.note(body, "Sunlight", plant.light);
    } else {
      Ui.note(
          body,
          "Care information",
          "Indoor watering and light guidance is not available for this record. Check a"
              + " species-specific care source before planning care.");
    }
    if (!plant.sourceUrl.isEmpty()) {
      body.addView(
          Ui.button(
              c, "Plant record · " + plant.sourceName, true, () -> openLink(plant.sourceUrl)));
      Ui.gap(body, 12);
      body.addView(Ui.text(c, plant.dataCredits, 12, Ui.MUTED, false));
      if (plant.sourceName.equals("Trefle"))
        body.addView(
            Ui.button(
                c,
                "Data license · CC BY 4.0",
                false,
                () -> openLink("https://creativecommons.org/licenses/by/4.0/")));
    }
    if (!plant.careUrl.isEmpty())
      body.addView(
          Ui.button(c, "Care source · NC State Extension", false, () -> openLink(plant.careUrl)));
    if (!plant.photoSource.isEmpty()) {
      Ui.gap(body, 12);
      body.addView(
          Ui.text(
              c, "Photo: " + plant.photoCredit + " · " + plant.photoLicense, 12, Ui.MUTED, false));
      body.addView(
          Ui.button(c, "Photo source and license", false, () -> openLink(plant.photoSource)));
    }
    return Ui.scroll(c, body);
  }

  private void openLink(String address) {
    Uri uri = Uri.parse(address);
    if (!"https".equals(uri.getScheme())) return;
    try {
      startActivity(new Intent(Intent.ACTION_VIEW, uri));
    } catch (ActivityNotFoundException e) {
      Toast.makeText(
              requireContext(), "No web browser is installed on this device.", Toast.LENGTH_SHORT)
          .show();
    }
  }

  @Override
  public void onDestroyView() {
    if (images != null) images.close();
    images = null;
    super.onDestroyView();
  }
}
