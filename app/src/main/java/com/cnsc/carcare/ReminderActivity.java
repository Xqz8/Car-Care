package com.cnsc.carcare;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class ReminderActivity extends AppCompatActivity {
    DatabaseHelper db;
    SessionManager session;
    LinearLayout layoutReminders;
    LinearLayout layoutPMS;
    EditText etReminderType, etReminderNote, etDueDate, etDueOdometer;

    // Filter state: "all", "pms", "custom"
    private String currentFilter = "all";
    private TextView chipAll, chipPMS, chipCustom;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        overridePendingTransition(0, 0);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reminder);

        db = new DatabaseHelper(this);
        session = new SessionManager(this);

        layoutReminders = findViewById(R.id.layoutReminders);
        layoutPMS       = findViewById(R.id.layoutPMS);
        etReminderType  = findViewById(R.id.etReminderType);
        etReminderNote  = findViewById(R.id.etReminderNote);
        etDueDate       = findViewById(R.id.etDueDate);
        etDueOdometer   = findViewById(R.id.etDueOdometer);

        // Filter chips
        chipAll    = findViewById(R.id.filterAll);
        chipPMS    = findViewById(R.id.filterPMS);
        chipCustom = findViewById(R.id.filterCustom);

        chipAll.setOnClickListener(v    -> setFilter("all"));
        chipPMS.setOnClickListener(v    -> setFilter("pms"));
        chipCustom.setOnClickListener(v -> setFilter("custom"));

        int vehicleId  = session.getActiveVehicleId();
        int currentOdo = db.getVehicleOdometer(vehicleId);
        etDueOdometer.setHint("Current: " + currentOdo + " km (must be ≥ this)");

        etDueDate.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            new DatePickerDialog(this, (view, y, m, d) ->
                    etDueDate.setText(y + "-" + String.format("%02d", m + 1)
                            + "-" + String.format("%02d", d)),
                    c.get(Calendar.YEAR), c.get(Calendar.MONTH),
                    c.get(Calendar.DAY_OF_MONTH)).show();
        });

        findViewById(R.id.btnAddReminder).setOnClickListener(v -> {
            String type     = etReminderType.getText().toString().trim();
            String note     = etReminderNote.getText().toString().trim();
            String date     = etDueDate.getText().toString().trim();
            String odoInput = etDueOdometer.getText().toString().trim();

            if (type.isEmpty()) {
                Toast.makeText(this, "Please enter a task type",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            int activeVehicleId = session.getActiveVehicleId();
            if (activeVehicleId == -1) {
                Toast.makeText(this, "Select a vehicle in the Garage first!",
                        Toast.LENGTH_LONG).show();
                return;
            }

            int odo = 0;
            if (!odoInput.isEmpty()) {
                try {
                    odo = Integer.parseInt(odoInput);
                } catch (NumberFormatException e) {
                    etDueOdometer.setError("Invalid number");
                    etDueOdometer.requestFocus();
                    return;
                }
                int latestOdo = db.getVehicleOdometer(activeVehicleId);
                if (odo < latestOdo) {
                    etDueOdometer.setError("Must be ≥ current reading of "
                            + latestOdo + " km");
                    etDueOdometer.requestFocus();
                    Toast.makeText(this,
                            "Due odometer must be ≥ " + latestOdo + " km",
                            Toast.LENGTH_LONG).show();
                    return;
                }
            }

            boolean success = db.addReminder(activeVehicleId, type, note, date, odo);
            if (success) {
                Toast.makeText(this, "Maintenance Task Added!", Toast.LENGTH_SHORT).show();
                etReminderType.setText("");
                etReminderNote.setText("");
                etDueDate.setText("");
                etDueOdometer.setText("");
                int updatedOdo = db.getVehicleOdometer(activeVehicleId);
                etDueOdometer.setHint("Current: " + updatedOdo + " km (must be ≥ this)");
                loadAllTasks();
            } else {
                Toast.makeText(this, "Database Error: Could not save.",
                        Toast.LENGTH_SHORT).show();
            }
        });

        setupNavigation();
        loadAllTasks();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadAllTasks();
    }

    private void setFilter(String filter) {
        currentFilter = filter;
        // Active chip = blue filled, inactive = outline
        int blue     = getResources().getColor(R.color.blue_dark);
        int white    = android.graphics.Color.WHITE;
        chipAll.setBackgroundResource(filter.equals("all")    ? R.drawable.rounded_button_blue : R.drawable.rounded_button_outline);
        chipPMS.setBackgroundResource(filter.equals("pms")    ? R.drawable.rounded_button_blue : R.drawable.rounded_button_outline);
        chipCustom.setBackgroundResource(filter.equals("custom") ? R.drawable.rounded_button_blue : R.drawable.rounded_button_outline);
        chipAll.setTextColor(filter.equals("all")    ? white : blue);
        chipPMS.setTextColor(filter.equals("pms")    ? white : blue);
        chipCustom.setTextColor(filter.equals("custom") ? white : blue);
        loadAllTasks();
    }

    private void loadAllTasks() {
        if (layoutPMS == null) return;
        layoutPMS.removeAllViews();

        int vehicleId = session.getActiveVehicleId();
        if (vehicleId == -1) {
            TextView tv = new TextView(this);
            tv.setText("No active vehicle selected.");
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(0, 50, 0, 0);
            layoutPMS.addView(tv);
            return;
        }

        boolean showPMS    = currentFilter.equals("all") || currentFilter.equals("pms");
        boolean showCustom = currentFilter.equals("all") || currentFilter.equals("custom");

        if (showPMS)    loadPMSChecklist();
        if (showCustom) loadCustomTasks();

        if (layoutPMS.getChildCount() == 0) {
            TextView tv = new TextView(this);
            tv.setText("No tasks found.");
            tv.setGravity(Gravity.CENTER);
            tv.setTextColor(android.graphics.Color.GRAY);
            tv.setPadding(0, 30, 0, 0);
            layoutPMS.addView(tv);
        }
    }

    // ─────────────────────────────────────────────
    //  PMS CHECKLIST
    // ─────────────────────────────────────────────

    private void loadPMSChecklist() {
        if (layoutPMS == null) return;

        int vehicleId = session.getActiveVehicleId();
        if (vehicleId == -1) return;

        int currentOdo = db.getVehicleOdometer(vehicleId);
        Cursor cursor  = db.getPMSTasksByVehicle(vehicleId);

        if (cursor == null || cursor.getCount() == 0) {
            TextView tv = new TextView(this);
            tv.setText("No PMS tasks found. Add a vehicle to generate tasks.");
            tv.setTextColor(android.graphics.Color.GRAY);
            tv.setGravity(Gravity.CENTER);
            layoutPMS.addView(tv);
            return;
        }

        List<int[]>    overdueIds       = new ArrayList<>();
        List<String[]> overdueData      = new ArrayList<>();
        List<int[]>    upcomingIds      = new ArrayList<>();
        List<String[]> upcomingData     = new ArrayList<>();
        List<int[]>    recentlyDoneIds  = new ArrayList<>();
        List<String[]> recentlyDoneData = new ArrayList<>();
        List<int[]>    notStartedIds    = new ArrayList<>();
        List<String[]> notStartedData   = new ArrayList<>();

        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                .format(new java.util.Date());

        while (cursor.moveToNext()) {
            int    taskId          = cursor.getInt(cursor.getColumnIndexOrThrow("id"));
            String taskName        = cursor.getString(cursor.getColumnIndexOrThrow("task_name"));
            int    intervalKm      = cursor.getInt(cursor.getColumnIndexOrThrow("interval_km"));
            int    intervalMonths  = cursor.getInt(cursor.getColumnIndexOrThrow("interval_months"));
            int    lastDoneKm      = cursor.getInt(cursor.getColumnIndexOrThrow("last_done_km"));
            String lastDoneDate    = cursor.getString(cursor.getColumnIndexOrThrow("last_done_date"));
            int    isUserConfirmed = cursor.getInt(cursor.getColumnIndexOrThrow("is_user_confirmed"));

            int nextDueKm = intervalKm > 0
                    ? lastDoneKm + intervalKm : Integer.MAX_VALUE;

            String nextDueDate = "";
            if (intervalMonths > 0 && lastDoneDate != null && !lastDoneDate.isEmpty()) {
                try {
                    SimpleDateFormat sdf =
                            new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                    java.util.Date d = sdf.parse(lastDoneDate);
                    Calendar cal = Calendar.getInstance();
                    cal.setTime(d);
                    cal.add(Calendar.MONTH, intervalMonths);
                    nextDueDate = sdf.format(cal.getTime());
                } catch (Exception ignored) {}
            }

            boolean kmOverdue   = intervalKm > 0 && currentOdo >= nextDueKm;
            boolean dateOverdue = false;
            if (!nextDueDate.isEmpty()) {
                try {
                    SimpleDateFormat sdf =
                            new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                    java.util.Date due = sdf.parse(nextDueDate);
                    dateOverdue = due != null && due.before(new java.util.Date());
                } catch (Exception ignored) {}
            }
            boolean isOverdue = kmOverdue || dateOverdue;

            boolean recentlyServiced = (isUserConfirmed == 1)
                    && today.equals(lastDoneDate)
                    && !isOverdue;

            int progress = 0;
            if (isUserConfirmed == 1) {
                int progressKm     = 0;
                int progressMonths = 0;
                if (intervalKm > 0) {
                    int kmSinceDone = currentOdo - lastDoneKm;
                    progressKm = Math.min(100,
                            (int) ((float) kmSinceDone / intervalKm * 100));
                }
                if (intervalMonths > 0 && lastDoneDate != null
                        && !lastDoneDate.isEmpty()) {
                    try {
                        SimpleDateFormat sdf =
                                new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                        java.util.Date done = sdf.parse(lastDoneDate);
                        long millisElapsed =
                                new java.util.Date().getTime() - done.getTime();
                        float monthsElapsed =
                                millisElapsed / (1000f * 60 * 60 * 24 * 30.44f);
                        progressMonths = Math.min(100,
                                (int) (monthsElapsed / intervalMonths * 100));
                    } catch (Exception ignored) {}
                }
                progress = Math.max(progressKm, progressMonths);
            }

            String[] data = {
                    taskName,                        // 0
                    String.valueOf(intervalKm),      // 1
                    String.valueOf(intervalMonths),  // 2
                    String.valueOf(lastDoneKm),      // 3
                    lastDoneDate != null ? lastDoneDate : "", // 4
                    nextDueDate,                     // 5
                    String.valueOf(nextDueKm),       // 6
                    String.valueOf(progress),        // 7
                    String.valueOf(kmOverdue),       // 8
                    String.valueOf(dateOverdue)      // 9
            };

            if (isUserConfirmed == 0) {
                notStartedIds.add(new int[]{taskId, vehicleId});
                notStartedData.add(data);
            } else if (recentlyServiced) {
                recentlyDoneIds.add(new int[]{taskId, vehicleId});
                recentlyDoneData.add(data);
            } else if (isOverdue) {
                overdueIds.add(new int[]{taskId, vehicleId});
                overdueData.add(data);
            } else {
                upcomingIds.add(new int[]{taskId, vehicleId});
                upcomingData.add(data);
            }
        }
        cursor.close();

        if (!overdueData.isEmpty()) {
            layoutPMS.addView(makeSectionHeader("Overdue", 0xFFB71C1C));
            for (int i = 0; i < overdueData.size(); i++)
                layoutPMS.addView(buildPMSCard(
                        overdueIds.get(i), overdueData.get(i), currentOdo, true));
        }
        if (!upcomingData.isEmpty()) {
            layoutPMS.addView(makeSectionHeader("Upcoming",
                    getResources().getColor(R.color.blue_dark)));
            for (int i = 0; i < upcomingData.size(); i++)
                layoutPMS.addView(buildPMSCard(
                        upcomingIds.get(i), upcomingData.get(i), currentOdo, false));
        }
        if (!recentlyDoneData.isEmpty()) {
            layoutPMS.addView(makeSectionHeader("✅ Recently Serviced", 0xFF2E7D32));
            for (int i = 0; i < recentlyDoneData.size(); i++)
                layoutPMS.addView(buildDoneCard(
                        recentlyDoneIds.get(i), recentlyDoneData.get(i)));
        }
        if (!notStartedData.isEmpty()) {
            layoutPMS.addView(makeSectionHeader("Needs First Service",
                    android.graphics.Color.parseColor("#757575")));
            for (int i = 0; i < notStartedData.size(); i++)
                layoutPMS.addView(buildNotStartedCard(
                        notStartedIds.get(i), notStartedData.get(i), currentOdo));
        }
    }

    // ─────────────────────────────────────────────
    //  SECTION HEADER
    // ─────────────────────────────────────────────

    private TextView makeSectionHeader(String title, int color) {
        TextView tv = new TextView(this);
        tv.setText(title);
        tv.setTextSize(16f);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        tv.setTextColor(color);
        tv.setPadding(0, 24, 0, 16);
        return tv;
    }

    // ─────────────────────────────────────────────
    //  PMS CARD — Overdue / Upcoming
    // ─────────────────────────────────────────────

    private CardView buildPMSCard(int[] ids, String[] data,
                                  int currentOdo, boolean isOverdue) {
        int    taskId         = ids[0];
        int    finalVehicleId = ids[1];
        String taskName       = data[0];
        int    intervalKm     = Integer.parseInt(data[1]);
        int    lastDoneKm     = Integer.parseInt(data[3]);
        String lastDoneDate   = data[4];
        String nextDueDate    = data[5];
        int    nextDueKm      = Integer.parseInt(data[6]);
        int    progress       = Integer.parseInt(data[7]);

        CardView card = new CardView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, 20);
        card.setLayoutParams(params);
        card.setRadius(20f);
        card.setCardElevation(isOverdue ? 8f : 3f);
        card.setCardBackgroundColor(isOverdue ? 0xFFFFF3E0 : 0xFFFFFFFF);

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(36, 28, 36, 28);

        // ── Header row: name + km info ──
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView tvName = new TextView(this);
        tvName.setText((isOverdue ? "⚠️ " : "") + taskName);
        tvName.setTextSize(14f);
        tvName.setTypeface(null, android.graphics.Typeface.BOLD);
        tvName.setTextColor(getResources().getColor(R.color.blue_dark));
        LinearLayout.LayoutParams nameP = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tvName.setLayoutParams(nameP);

        TextView tvMiles = new TextView(this);
        if (isOverdue && intervalKm > 0) {
            int overdueBy = currentOdo - nextDueKm;
            tvMiles.setText("km overdue: " + overdueBy);
            tvMiles.setTextColor(0xFFE53935);
        } else if (intervalKm > 0) {
            int left = nextDueKm - currentOdo;
            tvMiles.setText("km left: " + left);
            tvMiles.setTextColor(android.graphics.Color.GRAY);
        }
        tvMiles.setTextSize(11f);

        headerRow.addView(tvName);
        headerRow.addView(tvMiles);

        // ── Progress bar ──
        android.widget.ProgressBar progressBar = new android.widget.ProgressBar(
                this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgress(Math.min(progress, 100));
        LinearLayout.LayoutParams pbParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 18);
        pbParams.setMargins(0, 10, 0, 12);
        progressBar.setLayoutParams(pbParams);
        progressBar.getProgressDrawable().setColorFilter(
                new android.graphics.PorterDuffColorFilter(
                        isOverdue ? 0xFFE53935 : 0xFF1565C0,
                        android.graphics.PorterDuff.Mode.SRC_IN));

        // ── Last done info ──
        TextView tvLast = new TextView(this);
        String lastInfo = "Last done: " + formatUserDate(lastDoneDate);
        if (lastDoneKm > 0) lastInfo += " at " + lastDoneKm + " km";
        tvLast.setText(lastInfo);
        tvLast.setTextSize(11f);
        tvLast.setTextColor(android.graphics.Color.LTGRAY);
        tvLast.setPadding(0, 0, 0, 6);

        // ── Next due info ──
        TextView tvNextDue = new TextView(this);
        StringBuilder nextDueStr = new StringBuilder("📅 Next due: ");
        if (!nextDueDate.isEmpty()) {
            try {
                SimpleDateFormat inFmt  =
                        new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                SimpleDateFormat outFmt =
                        new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
                java.util.Date d = inFmt.parse(nextDueDate);
                nextDueStr.append(outFmt.format(d));
            } catch (Exception ignored) {
                nextDueStr.append(nextDueDate);
            }
        }
        if (intervalKm > 0) {
            if (!nextDueDate.isEmpty()) nextDueStr.append(" or at ");
            nextDueStr.append(nextDueKm).append(" km");
        }
        tvNextDue.setText(nextDueStr.toString());
        tvNextDue.setTextSize(12f);
        tvNextDue.setTextColor(isOverdue
                ? 0xFFE53935
                : getResources().getColor(R.color.blue_primary));
        tvNextDue.setTypeface(null, android.graphics.Typeface.BOLD);
        tvNextDue.setPadding(0, 0, 0, 14);

        // ── Button row ──
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);

        Button btnEdit = new Button(this);
        btnEdit.setText("✏️ Edit");
        btnEdit.setAllCaps(false);
        btnEdit.setTextSize(13f);
        btnEdit.setBackgroundResource(R.drawable.rounded_button_outline);
        btnEdit.setTextColor(getResources().getColor(R.color.blue_dark));
        LinearLayout.LayoutParams editParams =
                new LinearLayout.LayoutParams(0, 120, 1f);
        editParams.setMargins(0, 0, 12, 0);
        btnEdit.setLayoutParams(editParams);
        btnEdit.setOnClickListener(v ->
                showEditDialog(taskId, taskName, lastDoneKm, lastDoneDate));

        Button btnDone = new Button(this);
        btnDone.setText("Mark as Done");
        btnDone.setAllCaps(false);
        btnDone.setTextSize(13f);
        btnDone.setBackgroundResource(R.drawable.rounded_button_blue);
        btnDone.setTextColor(android.graphics.Color.WHITE);
        btnDone.setLayoutParams(new LinearLayout.LayoutParams(0, 120, 2f));
        btnDone.setOnClickListener(v -> {
            int latestOdo = db.getVehicleOdometer(finalVehicleId);
            String todayStr = new java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())
                    .format(new java.util.Date());
            new AlertDialog.Builder(this)
                    .setTitle("Mark as Done?")
                    .setMessage("Mark \"" + taskName + "\" as done today (" + todayStr
                            + ") at " + latestOdo + " km?")
                    .setPositiveButton("Confirm", (dialog, which) -> {
                        boolean updated = db.markPMSTaskDoneConfirmed(taskId, latestOdo);
                        if (updated) {
                            Toast.makeText(this, taskName + " marked done ✅",
                                    Toast.LENGTH_SHORT).show();
                            new android.os.Handler(android.os.Looper.getMainLooper())
                                    .postDelayed(this::loadAllTasks, 100);
                        } else {
                            Toast.makeText(this, "Failed to update task",
                                    Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        btnRow.addView(btnEdit);
        btnRow.addView(btnDone);

        inner.addView(headerRow);
        inner.addView(progressBar);
        inner.addView(tvLast);
        inner.addView(tvNextDue);
        inner.addView(btnRow);
        card.addView(inner);
        return card;
    }

    // ─────────────────────────────────────────────
    //  NOT YET STARTED CARD
    // ─────────────────────────────────────────────

    private CardView buildNotStartedCard(int[] ids, String[] data, int currentOdo) {
        int    taskId         = ids[0];
        int    finalVehicleId = ids[1];
        String taskName       = data[0];
        int    intervalKm     = Integer.parseInt(data[1]);
        int    intervalMonths = Integer.parseInt(data[2]);

        CardView card = new CardView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, 16);
        card.setLayoutParams(params);
        card.setRadius(20f);
        card.setCardElevation(2f);
        card.setCardBackgroundColor(0xFFF5F5F5);

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(36, 28, 36, 28);

        TextView tvName = new TextView(this);
        tvName.setText(taskName);
        tvName.setTextSize(14f);
        tvName.setTypeface(null, android.graphics.Typeface.BOLD);
        tvName.setTextColor(android.graphics.Color.parseColor("#424242"));

        StringBuilder intervalSb = new StringBuilder("Every ");
        if (intervalKm > 0) intervalSb.append(intervalKm).append(" km");
        if (intervalKm > 0 && intervalMonths > 0) intervalSb.append(" or ");
        if (intervalMonths > 0)
            intervalSb.append(intervalMonths)
                    .append(intervalMonths == 1 ? " month" : " months");

        TextView tvInterval = new TextView(this);
        tvInterval.setText(intervalSb.toString());
        tvInterval.setTextSize(11f);
        tvInterval.setTextColor(android.graphics.Color.GRAY);
        tvInterval.setPadding(0, 6, 0, 16);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);

        Button btnEdit = new Button(this);
        btnEdit.setText("✏️ Set Last Done");
        btnEdit.setAllCaps(false);
        btnEdit.setTextSize(12f);
        btnEdit.setBackgroundResource(R.drawable.rounded_button_outline);
        btnEdit.setTextColor(getResources().getColor(R.color.blue_dark));
        LinearLayout.LayoutParams editParams =
                new LinearLayout.LayoutParams(0, 120, 1f);
        editParams.setMargins(0, 0, 12, 0);
        btnEdit.setLayoutParams(editParams);
        btnEdit.setOnClickListener(v ->
                showEditDialog(taskId, taskName, 0, ""));

        Button btnDone = new Button(this);
        btnDone.setText("Mark as Done");
        btnDone.setAllCaps(false);
        btnDone.setTextSize(13f);
        btnDone.setBackgroundResource(R.drawable.rounded_button_blue);
        btnDone.setTextColor(android.graphics.Color.WHITE);
        btnDone.setLayoutParams(new LinearLayout.LayoutParams(0, 120, 2f));
        btnDone.setOnClickListener(v -> {
            int latestOdo = db.getVehicleOdometer(finalVehicleId);
            String todayStr = new java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())
                    .format(new java.util.Date());
            new AlertDialog.Builder(this)
                    .setTitle("Mark as Done?")
                    .setMessage("Mark \"" + taskName + "\" as done today (" + todayStr
                            + ") at " + latestOdo + " km?")
                    .setPositiveButton("Confirm", (dialog, which) -> {
                        boolean updated = db.markPMSTaskDoneConfirmed(taskId, latestOdo);
                        if (updated) {
                            Toast.makeText(this, taskName + " marked done ✅",
                                    Toast.LENGTH_SHORT).show();
                            new android.os.Handler(android.os.Looper.getMainLooper())
                                    .postDelayed(this::loadAllTasks, 100);
                        } else {
                            Toast.makeText(this, "Failed to update task",
                                    Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        btnRow.addView(btnEdit);
        btnRow.addView(btnDone);

        inner.addView(tvName);
        inner.addView(tvInterval);
        inner.addView(btnRow);
        card.addView(inner);
        return card;
    }

    // ─────────────────────────────────────────────
    //  DONE CARD — Recently Serviced
    // ─────────────────────────────────────────────

    private CardView buildDoneCard(int[] ids, String[] data) {
        int    taskId       = ids[0];
        String taskName     = data[0];
        String lastDoneDate = data[4];
        int    lastDoneKm   = Integer.parseInt(data[3]);

        CardView card = new CardView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, 16);
        card.setLayoutParams(params);
        card.setRadius(20f);
        card.setCardElevation(2f);
        card.setCardBackgroundColor(0xFFE8F5E9);

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.HORIZONTAL);
        inner.setPadding(36, 24, 36, 24);
        inner.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvName = new TextView(this);
        tvName.setText("✅ " + taskName);
        tvName.setTextSize(14f);
        tvName.setTypeface(null, android.graphics.Typeface.BOLD);
        tvName.setTextColor(0xFF2E7D32);

        TextView tvDate = new TextView(this);
        String doneInfo = "Serviced: " + formatUserDate(lastDoneDate);
        if (lastDoneKm > 0) doneInfo += " at " + lastDoneKm + " km";
        tvDate.setText(doneInfo);
        tvDate.setTextSize(11f);
        tvDate.setTextColor(android.graphics.Color.GRAY);
        tvDate.setPadding(0, 4, 0, 0);

        textCol.addView(tvName);
        textCol.addView(tvDate);

        Button btnEdit = new Button(this);
        btnEdit.setText("✏️ Edit");
        btnEdit.setAllCaps(false);
        btnEdit.setTextSize(12f);
        btnEdit.setBackgroundResource(R.drawable.rounded_button_outline);
        btnEdit.setTextColor(getResources().getColor(R.color.blue_dark));
        btnEdit.setOnClickListener(v ->
                showEditDialog(taskId, taskName, lastDoneKm, lastDoneDate));

        inner.addView(textCol);
        inner.addView(btnEdit);
        card.addView(inner);
        return card;
    }

    // ─────────────────────────────────────────────
    //  EDIT DIALOG
    // ─────────────────────────────────────────────

    private void showEditDialog(int taskId, String taskName,
                                int currentKm, String currentDate) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Edit: " + taskName);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(60, 40, 60, 20);

        TextView tvKmLabel = new TextView(this);
        tvKmLabel.setText("Last done at (km):");
        tvKmLabel.setTextSize(13f);
        tvKmLabel.setPadding(0, 0, 0, 8);

        EditText etKm = new EditText(this);
        etKm.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        etKm.setText(currentKm > 0 ? String.valueOf(currentKm) : "");
        etKm.setHint("Enter odometer reading");
        etKm.setBackground(getResources().getDrawable(R.drawable.rounded_edittext));
        etKm.setPadding(24, 16, 24, 16);
        LinearLayout.LayoutParams kmParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 120);
        kmParams.setMargins(0, 0, 0, 20);
        etKm.setLayoutParams(kmParams);

        TextView tvDateLabel = new TextView(this);
        tvDateLabel.setText("Last done date:");
        tvDateLabel.setTextSize(13f);
        tvDateLabel.setPadding(0, 0, 0, 8);

        EditText etDate = new EditText(this);
        etDate.setText(currentDate);
        etDate.setHint("Tap to pick date");
        etDate.setFocusable(false);
        etDate.setClickable(true);
        etDate.setBackground(getResources().getDrawable(R.drawable.rounded_edittext));
        etDate.setPadding(24, 16, 24, 16);
        etDate.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 120));
        etDate.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            new DatePickerDialog(this, (view, y, m, d) ->
                    etDate.setText(y + "-" + String.format("%02d", m + 1)
                            + "-" + String.format("%02d", d)),
                    c.get(Calendar.YEAR), c.get(Calendar.MONTH),
                    c.get(Calendar.DAY_OF_MONTH)).show();
        });

        layout.addView(tvKmLabel);
        layout.addView(etKm);
        layout.addView(tvDateLabel);
        layout.addView(etDate);

        builder.setView(layout);
        builder.setPositiveButton("Save", (dialog, which) -> {
            String kmStr = etKm.getText().toString().trim();
            String date  = etDate.getText().toString().trim();
            int km = 0;
            if (!kmStr.isEmpty()) {
                try { km = Integer.parseInt(kmStr); }
                catch (NumberFormatException ignored) {}
            }
            db.confirmPMSTask(taskId, km, date);
            Toast.makeText(this, taskName + " updated!", Toast.LENGTH_SHORT).show();
            new android.os.Handler(android.os.Looper.getMainLooper())
                    .postDelayed(this::loadAllTasks, 100);
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    // ─────────────────────────────────────────────
    //  CUSTOM MAINTENANCE TASKS
    // ─────────────────────────────────────────────

    private void loadCustomTasks() {
        int vehicleId = session.getActiveVehicleId();
        if (vehicleId == -1) return;

        Cursor cursor = db.getRemindersByVehicle(vehicleId);
        if (cursor == null || cursor.getCount() == 0) {
            if (cursor != null) cursor.close();
            return;
        }

        layoutPMS.addView(makeSectionHeader("📌 Custom Tasks",
                getResources().getColor(R.color.blue_dark)));

        while (cursor.moveToNext()) {
            final int id = cursor.getInt(cursor.getColumnIndexOrThrow("id"));
            String type  = cursor.getString(cursor.getColumnIndexOrThrow("type"));
            String note  = cursor.getString(cursor.getColumnIndexOrThrow("note"));
            String date  = cursor.getString(cursor.getColumnIndexOrThrow("due_date"));
            int dueOdo   = cursor.getInt(cursor.getColumnIndexOrThrow("due_odometer"));

            CardView card = new CardView(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, 0, 0, 20);
            card.setLayoutParams(params);
            card.setRadius(20f);
            card.setCardElevation(4f);
            card.setCardBackgroundColor(0xFFF0F4FF);

            LinearLayout inner = new LinearLayout(this);
            inner.setPadding(36, 32, 36, 32);
            inner.setOrientation(LinearLayout.VERTICAL);

            TextView title = new TextView(this);
            title.setText("🔔 " + type);
            title.setTextSize(15f);
            title.setTextColor(getResources().getColor(R.color.blue_dark));
            title.setTypeface(null, android.graphics.Typeface.BOLD);

            StringBuilder infoBuilder = new StringBuilder();
            if (date != null && !date.isEmpty()) {
                infoBuilder.append("Due: ").append(formatUserDate(date));
            } else {
                infoBuilder.append("Due: No due date set");
            }
            if (dueOdo > 0) {
                if (infoBuilder.length() > 0) infoBuilder.append(" • ");
                infoBuilder.append(dueOdo).append(" km");
            }
            if (note != null && !note.isEmpty())
                infoBuilder.append("\nNote: ").append(note);

            TextView info = new TextView(this);
            info.setText(infoBuilder.toString());
            info.setPadding(0, 8, 0, 20);
            info.setTextColor(android.graphics.Color.GRAY);
            info.setTextSize(12f);

            LinearLayout buttonLayout = new LinearLayout(this);
            buttonLayout.setOrientation(LinearLayout.HORIZONTAL);

            Button btnEdit = new Button(this);
            btnEdit.setText("✏️ Edit");
            btnEdit.setTextSize(12f);
            btnEdit.setAllCaps(false);
            btnEdit.setBackgroundResource(R.drawable.rounded_button_outline);
            btnEdit.setTextColor(getResources().getColor(R.color.blue_dark));
            LinearLayout.LayoutParams editBtnP = new LinearLayout.LayoutParams(0, 110, 1f);
            editBtnP.setMargins(0, 0, 10, 0);
            btnEdit.setLayoutParams(editBtnP);
            final String finalType = type;
            final String finalNote = note;
            final String finalDate = date;
            final int finalDueOdo  = dueOdo;
            btnEdit.setOnClickListener(v ->
                    showEditCustomTaskDialog(id, finalType, finalNote, finalDate, finalDueOdo));

            Button btnComplete = new Button(this);
            btnComplete.setText("Complete");
            btnComplete.setTextSize(12f);
            btnComplete.setAllCaps(false);
            btnComplete.setBackgroundResource(R.drawable.rounded_button_blue);
            btnComplete.setTextColor(android.graphics.Color.WHITE);
            LinearLayout.LayoutParams btnP = new LinearLayout.LayoutParams(0, 110, 1f);
            btnP.setMargins(0, 0, 10, 0);
            btnComplete.setLayoutParams(btnP);
            btnComplete.setOnClickListener(v -> {
                db.markReminderDone(id);
                loadAllTasks();
                Toast.makeText(this, "Task completed! ✅", Toast.LENGTH_SHORT).show();
            });

            Button btnDelete = new Button(this);
            btnDelete.setText("Delete");
            btnDelete.setTextSize(12f);
            btnDelete.setAllCaps(false);
            try { btnDelete.setBackgroundResource(R.drawable.rounded_button_gray); }
            catch (Exception e) { btnDelete.setBackgroundColor(android.graphics.Color.GRAY); }
            btnDelete.setTextColor(android.graphics.Color.WHITE);
            btnDelete.setLayoutParams(new LinearLayout.LayoutParams(0, 110, 1f));
            btnDelete.setOnClickListener(v -> {
                db.deleteReminder(id);
                loadAllTasks();
                Toast.makeText(this, "Task removed", Toast.LENGTH_SHORT).show();
            });

            buttonLayout.addView(btnEdit);
            buttonLayout.addView(btnComplete);
            buttonLayout.addView(btnDelete);
            inner.addView(title);
            inner.addView(info);
            inner.addView(buttonLayout);
            card.addView(inner);
            layoutPMS.addView(card);
        }
        cursor.close();
    }

    // ─────────────────────────────────────────────
    //  EDIT CUSTOM TASK DIALOG
    // ─────────────────────────────────────────────

    private void showEditCustomTaskDialog(int taskId, String currentType,
                                          String currentNote, String currentDate,
                                          int currentOdo) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Edit Custom Task");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(60, 40, 60, 20);

        // Task type field
        TextView tvTypeLabel = new TextView(this);
        tvTypeLabel.setText("Task Type:");
        tvTypeLabel.setTextSize(13f);
        tvTypeLabel.setPadding(0, 0, 0, 8);

        EditText etType = new EditText(this);
        etType.setText(currentType);
        etType.setHint("e.g. Oil Change");
        etType.setBackground(getResources().getDrawable(R.drawable.rounded_edittext));
        etType.setPadding(24, 16, 24, 16);
        LinearLayout.LayoutParams typeParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 120);
        typeParams.setMargins(0, 0, 0, 20);
        etType.setLayoutParams(typeParams);

        // Notes field
        TextView tvNoteLabel = new TextView(this);
        tvNoteLabel.setText("Notes (optional):");
        tvNoteLabel.setTextSize(13f);
        tvNoteLabel.setPadding(0, 0, 0, 8);

        EditText etNote = new EditText(this);
        etNote.setText(currentNote != null ? currentNote : "");
        etNote.setHint("Optional notes");
        etNote.setBackground(getResources().getDrawable(R.drawable.rounded_edittext));
        etNote.setPadding(24, 16, 24, 16);
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 120);
        noteParams.setMargins(0, 0, 0, 20);
        etNote.setLayoutParams(noteParams);

        // Due date field
        TextView tvDateLabel = new TextView(this);
        tvDateLabel.setText("Due Date:");
        tvDateLabel.setTextSize(13f);
        tvDateLabel.setPadding(0, 0, 0, 8);

        EditText etDate = new EditText(this);
        etDate.setText(currentDate != null ? currentDate : "");
        etDate.setHint("Tap to pick date");
        etDate.setFocusable(false);
        etDate.setClickable(true);
        etDate.setBackground(getResources().getDrawable(R.drawable.rounded_edittext));
        etDate.setPadding(24, 16, 24, 16);
        LinearLayout.LayoutParams dateParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 120);
        dateParams.setMargins(0, 0, 0, 20);
        etDate.setLayoutParams(dateParams);
        etDate.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            new DatePickerDialog(this, (view, y, m, d) ->
                    etDate.setText(y + "-" + String.format("%02d", m + 1)
                            + "-" + String.format("%02d", d)),
                    c.get(Calendar.YEAR), c.get(Calendar.MONTH),
                    c.get(Calendar.DAY_OF_MONTH)).show();
        });

        // Due odometer field
        TextView tvOdoLabel = new TextView(this);
        tvOdoLabel.setText("Due Odometer (km):");
        tvOdoLabel.setTextSize(13f);
        tvOdoLabel.setPadding(0, 0, 0, 8);

        EditText etOdo = new EditText(this);
        etOdo.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        etOdo.setText(currentOdo > 0 ? String.valueOf(currentOdo) : "");
        etOdo.setHint("Enter odometer reading");
        etOdo.setBackground(getResources().getDrawable(R.drawable.rounded_edittext));
        etOdo.setPadding(24, 16, 24, 16);
        etOdo.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 120));

        layout.addView(tvTypeLabel);
        layout.addView(etType);
        layout.addView(tvNoteLabel);
        layout.addView(etNote);
        layout.addView(tvDateLabel);
        layout.addView(etDate);
        layout.addView(tvOdoLabel);
        layout.addView(etOdo);

        builder.setView(layout);
        builder.setPositiveButton("Save", (dialog, which) -> {
            String type = etType.getText().toString().trim();
            if (type.isEmpty()) {
                Toast.makeText(this, "Task type cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }
            String note = etNote.getText().toString().trim();
            String date = etDate.getText().toString().trim();
            String odoStr = etOdo.getText().toString().trim();
            int odo = 0;
            if (!odoStr.isEmpty()) {
                try { odo = Integer.parseInt(odoStr); }
                catch (NumberFormatException ignored) {}
            }
            boolean updated = db.updateReminder(taskId, type, note, date, odo);
            if (updated) {
                Toast.makeText(this, "Task updated! ✅", Toast.LENGTH_SHORT).show();
                new android.os.Handler(android.os.Looper.getMainLooper())
                        .postDelayed(this::loadAllTasks, 100);
            } else {
                Toast.makeText(this, "Failed to update task", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    // ─────────────────────────────────────────────
    //  NAVIGATION
    // ─────────────────────────────────────────────

    private void setupNavigation() {
        findViewById(R.id.btnNavHome).setOnClickListener(v -> {
            startActivity(new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
            overridePendingTransition(0, 0);
        });
        findViewById(R.id.btnNavHistory).setOnClickListener(v -> {
            startActivity(new Intent(this, HistoryActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
            overridePendingTransition(0, 0);
        });
        findViewById(R.id.btnNavReminders).setOnClickListener(v -> {
            loadAllTasks();
            Toast.makeText(this, "Refreshed", Toast.LENGTH_SHORT).show();
        });
        findViewById(R.id.btnNavVehicles).setOnClickListener(v -> {
            startActivity(new Intent(this, VehicleManagementActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
            overridePendingTransition(0, 0);
        });
        findViewById(R.id.btnNavAddExpense).setOnClickListener(v -> {
            startActivity(new Intent(this, AddExpenseActivity.class));
            overridePendingTransition(0, 0);
        });
    }

    // ─────────────────────────────────────────────
    //  DATE FORMATTING HELPER
    // ─────────────────────────────────────────────
    /** Converts "yyyy-MM-dd" to "MMM d, yyyy" (e.g. "May 13, 2026"). Falls back to raw string. */
    private String formatUserDate(String rawDate) {
        if (rawDate == null || rawDate.isEmpty()) return "No due date set";
        try {
            java.text.SimpleDateFormat inFmt  = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            java.text.SimpleDateFormat outFmt = new java.text.SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
            java.util.Date d = inFmt.parse(rawDate);
            return d != null ? outFmt.format(d) : rawDate;
        } catch (Exception e) {
            return rawDate;
        }
    }
}