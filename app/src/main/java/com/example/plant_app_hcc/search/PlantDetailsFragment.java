package com.example.plant_app_hcc.search;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.app.DatePickerDialog;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.example.plant_app_hcc.CareEvent;
import com.example.plant_app_hcc.CareTaskManager;
import com.example.plant_app_hcc.CareTaskType;
import com.example.plant_app_hcc.ChallengeStore;
import com.example.plant_app_hcc.GardenFlower;
import com.example.plant_app_hcc.GardenFlowers;
import com.example.plant_app_hcc.PottedPlantView;
import com.example.plant_app_hcc.PotStyle;
import com.example.plant_app_hcc.UserGarden;
import com.example.plant_app_hcc.R;
import java.text.DateFormat;
import java.util.Calendar;
import java.util.List;
import org.json.JSONObject;

/** Plant information, garden membership and this individual plant's saved care tasks. */
public final class PlantDetailsFragment extends Fragment {
  private Images images;
  private Species plant;
  private LinearLayout gardenActions, taskSection;
  private AlertDialog actionDialog;
  private DatePickerDialog dateDialog;

  @Override
  public void onViewCreated(View view, Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    if (plant != null) ChallengeStore.recordPlantViewed(requireContext(), plant.id);
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle saved) {
    android.content.Context c = requireContext();
    LinearLayout body = Ui.column(c);
    body.setPadding(Ui.dp(c, 20), Ui.dp(c, 16), Ui.dp(c, 20), Ui.dp(c, 24));
    body.setBackgroundColor(Ui.BG);
    try {
      String json = saved == null ? requireArguments().getString("plant", "")
          : saved.getString("profilePlant", requireArguments().getString("plant", ""));
      plant = Species.fromJson(new JSONObject(json));
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
    if (!plant.location.isEmpty())
      body.addView(Ui.text(c, getString(R.string.garden_location, plant.location), 14, Ui.MUTED, false));
    gardenActions = Ui.column(c);
    taskSection = Ui.column(c);
    body.addView(gardenActions);
    body.addView(taskSection);
    refreshGardenTools();
    Ui.gap(body, 16);
    Ui.note(
        body,
        "About this plant",
        plant.description.isEmpty()
            ? "No plant information is available for this entry."
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

  private void refreshGardenTools() {
    if (gardenActions == null || taskSection == null) return;
    android.content.Context c = requireContext();
    gardenActions.removeAllViews();
    taskSection.removeAllViews();
    Species savedPlant = UserGarden.find(c, plant.id);
    if (savedPlant == null) {
      Button add = Ui.button(c, getString(R.string.garden_add), true, () -> {
        if (UserGarden.find(c, plant.id) == null) {
          plant = UserGarden.add(c, plant);
          // Keep the new instance ID across view recreation and Back navigation.
          requireArguments().putString("plant", plant.toJson().toString());
          requireArguments().putString("gardenPlantId", plant.id);
          Toast.makeText(c, getString(R.string.garden_added, plant.name), Toast.LENGTH_SHORT).show();
        }
        refreshGardenTools();
      });
      add.setId(R.id.garden_add);
      gardenActions.addView(add);
      return;
    }
    plant = savedPlant;
    Button home = Ui.button(c, getString(R.string.garden_view), false, this::goToGarden);
    home.setId(R.id.garden_view);
    gardenActions.addView(home);
    PotStyle pot = PotStyle.fromId(plant.potStyleId);
    LinearLayout appearance = Ui.row(c);
    PottedPlantView preview = new PottedPlantView(c);
    preview.show(plant.flowerId, pot, false);
    appearance.addView(preview, new LinearLayout.LayoutParams(Ui.dp(c, 82), Ui.dp(c, 88)));
    LinearLayout choices = Ui.column(c);
    Button changePot = Ui.button(c, getString(R.string.garden_current_pot, getString(pot.getTitle())),
        false, this::choosePot);
    changePot.setId(R.id.garden_change_pot);
    changePot.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
    choices.addView(changePot);
    Button changeColor = Ui.button(c, getString(R.string.garden_change_flower_color),
        false, this::chooseFlowerColor);
    choices.addView(changeColor);
    appearance.addView(choices, Ui.weighted(c, 0));
    gardenActions.addView(appearance);
    Ui.gap(taskSection, 16);
    taskSection.addView(Ui.text(c, getString(R.string.garden_tasks_for, plant.name), 20, Ui.INK, true));
    List<CareEvent> tasks = CareTaskManager.getTasksForPlant(c, plant.id);
    if (tasks.isEmpty()) {
      taskSection.addView(Ui.text(c, getString(R.string.garden_no_tasks), 15, Ui.MUTED, false));
    }
    for (CareEvent task : tasks) {
      LinearLayout row = Ui.card(c);
      CheckBox check = new CheckBox(c);
      check.setText(task.getTitle());
      check.setTextSize(16);
      check.setTextColor(Ui.INK);
      check.setMinHeight(Ui.dp(c, 48));
      check.setChecked(task.isCompleted());
      TextView due = Ui.text(c, taskStatus(task), 13, Ui.MUTED, false);
      check.setOnCheckedChangeListener((button, checked) -> {
        CareTaskManager.setCompleted(c, task.getId(), checked);
        task.setCompleted(checked);
        due.setText(taskStatus(task));
      });
      row.addView(check);
      row.addView(due);
      taskSection.addView(row);
    }
    Button addTask = Ui.button(c, getString(R.string.garden_add_task), true, this::addTask);
    addTask.setId(R.id.garden_add_task);
    taskSection.addView(addTask);
    Ui.gap(taskSection, 12);
    Button remove = Ui.button(c, getString(R.string.garden_remove), false, this::confirmRemove);
    remove.setId(R.id.garden_remove);
    remove.setTextColor(Ui.RED);
    taskSection.addView(remove);
  }

  private String taskStatus(CareEvent task) {
    String date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(new java.util.Date(task.getDateInMillis()));
    return getString(task.isCompleted() ? R.string.garden_task_completed : R.string.garden_due_on, date);
  }

  private void choosePot() {
    android.content.Context c = requireContext();
    List<PotStyle> pots = new ChallengeStore(c).ownedPots();
    CharSequence[] names = new CharSequence[pots.size()];
    for (int i = 0; i < pots.size(); i++) names[i] = getString(pots.get(i).getTitle());
    actionDialog = new MaterialAlertDialogBuilder(c)
        .setTitle(R.string.garden_change_pot)
        .setSingleChoiceItems(names, pots.indexOf(PotStyle.fromId(plant.potStyleId)), (dialog, which) -> {
          UserGarden.setPot(c, plant.id, pots.get(which));
          dialog.dismiss();
          refreshGardenTools();
        })
        .setNegativeButton(android.R.string.cancel, null)
        .show();
  }

  private void chooseFlowerColor() {
    android.content.Context c = requireContext();
    List<GardenFlower> flowers = GardenFlowers.colorsFor(plant.flowerId);
    CharSequence[] names = new CharSequence[flowers.size()];
    int selected = -1;
    for (int i = 0; i < flowers.size(); i++) {
      GardenFlower flower = flowers.get(i);
      names[i] = getString(flower.getColor().getLabel());
      if (flower.getId().equals(plant.flowerId)) selected = i;
    }
    actionDialog = new MaterialAlertDialogBuilder(c)
        .setTitle(R.string.garden_change_flower_color)
        .setSingleChoiceItems(names, selected, (dialog, which) -> {
          UserGarden.setFlower(c, plant.id, flowers.get(which).getId());
          dialog.dismiss();
          refreshGardenTools();
        })
        .setNegativeButton(android.R.string.cancel, null)
        .show();
  }

  private void addTask() {
    android.content.Context c = requireContext();
    LinearLayout form = Ui.column(c);
    form.setPadding(Ui.dp(c, 20), Ui.dp(c, 8), Ui.dp(c, 20), Ui.dp(c, 8));
    EditText title = Ui.input(c, getString(R.string.garden_task_hint), "", false);
    title.setId(R.id.garden_task_title);
    title.setFilters(new InputFilter[] {new InputFilter.LengthFilter(120)});
    form.addView(title);
    CareTaskType[] types = CareTaskType.values();
    String[] typeLabels = new String[types.length + 1];
    typeLabels[0] = getString(R.string.care_task_detect_type);
    for (int i = 0; i < types.length; i++) typeLabels[i + 1] = getString(types[i].getLabel());
    Spinner typePicker = new Spinner(c);
    typePicker.setId(View.generateViewId());
    typePicker.setMinimumHeight(Ui.dp(c, 48));
    ArrayAdapter<String> typesAdapter = new ArrayAdapter<>(c, android.R.layout.simple_spinner_item, typeLabels);
    typesAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    typePicker.setAdapter(typesAdapter);
    TextView typeLabel = Ui.text(c, getString(R.string.care_task_type), 14, Ui.INK, true);
    typeLabel.setLabelFor(typePicker.getId());
    Ui.gap(form, 10);
    form.addView(typeLabel);
    form.addView(typePicker);
    Calendar due = Calendar.getInstance();
    Button date = Ui.button(c, getString(R.string.garden_due_on,
        DateFormat.getDateInstance(DateFormat.MEDIUM).format(due.getTime())), false, () -> {});
    date.setId(R.id.garden_task_date);
    date.setOnClickListener(v -> {
      dateDialog = new DatePickerDialog(c, (picker, year, month, day) -> {
        due.set(year, month, day);
        date.setText(getString(R.string.garden_due_on,
            DateFormat.getDateInstance(DateFormat.MEDIUM).format(due.getTime())));
      }, due.get(Calendar.YEAR), due.get(Calendar.MONTH), due.get(Calendar.DAY_OF_MONTH));
      dateDialog.show();
    });
    form.addView(date);
    actionDialog = new MaterialAlertDialogBuilder(c)
        .setTitle(R.string.garden_add_task).setView(Ui.scroll(c, form))
        .setNegativeButton(android.R.string.cancel, null)
        .setPositiveButton(R.string.garden_save_task, null).create();
    actionDialog.setOnShowListener(dialog -> actionDialog.getButton(AlertDialog.BUTTON_POSITIVE)
        .setOnClickListener(button -> {
          String taskName = title.getText().toString().trim();
          if (taskName.isEmpty()) {
            title.setError(getString(R.string.garden_task_required));
            return;
          }
          int selection = typePicker.getSelectedItemPosition();
          CareTaskType type = selection > 0 ? types[selection - 1] : CareTaskType.fromTitle(taskName);
          CareEvent task = CareTaskManager.addTask(c, plant.id, taskName, due.getTimeInMillis(), type);
          if (task == null) return;
          actionDialog.dismiss();
          refreshGardenTools();
        }));
    actionDialog.show();
  }

  private void confirmRemove() {
    actionDialog = new MaterialAlertDialogBuilder(requireContext())
        .setTitle(R.string.garden_remove)
        .setMessage(getString(R.string.garden_remove_message, plant.name))
        .setNegativeButton(android.R.string.cancel, null)
        .setPositiveButton(R.string.garden_remove_confirm, (dialog, which) -> {
          UserGarden.remove(requireContext(), plant.id);
          goToGarden();
        }).show();
  }

  private void goToGarden() {
    NavHostFragment.findNavController(this).popBackStack(R.id.FirstFragment, false);
  }

  @Override
  public void onSaveInstanceState(Bundle out) {
    if (plant != null) out.putString("profilePlant", plant.toJson().toString());
    super.onSaveInstanceState(out);
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
    if (actionDialog != null) actionDialog.dismiss();
    if (dateDialog != null) dateDialog.dismiss();
    actionDialog = null;
    dateDialog = null;
    gardenActions = null;
    taskSection = null;
    if (images != null) images.close();
    images = null;
    super.onDestroyView();
  }
}
