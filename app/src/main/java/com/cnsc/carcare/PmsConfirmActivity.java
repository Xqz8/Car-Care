package com.cnsc.carcare;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Shown right after a vehicle is registered.
 *
 * Flow:
 *  1. User sees all PMS tasks as a checklist.
 *  2. They CHECK the ones they have already had serviced.
 *  3. Only checked tasks reveal a date picker to enter the last service date.
 *  4. Pressing Confirm saves the dates for checked tasks only.
 *     Unchecked tasks keep today's date (seeded default).
 */
public class PmsConfirmActivity extends AppCompatActivity {

    DatabaseHelper db;
    SessionManager session;
    LinearLayout layoutTasks;

    // Per-task state
    private final List<Integer>  taskIds   = new ArrayList<>();
    private final List<CheckBox> checkList = new ArrayList<>();
    private final List<EditText> dateList  = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pms_confirm);

        db      = new DatabaseHelper(this);
        session = new SessionManager(this);

        layoutTasks = findViewById(R.id.layoutPmsConfirmTasks);

        int vehicleId = session.getActiveVehicleId();
        loadTasks(vehicleId);

        // Confirm — save only checked tasks
        findViewById(R.id.btnConfirmPms).setOnClickListener(v -> {
            for (int i = 0; i < taskIds.size(); i++) {
                if (checkList.get(i).isChecked()) {
                    String date = dateList.get(i).getText().toString().trim();
                    if (!date.isEmpty()) {
                        // km stays as current odometer (already seeded), only date is updated
                        db.confirmPMSTask(taskIds.get(i), db.getVehicleOdometer(vehicleId), date);
                    }
                }
            }
            Toast.makeText(this, "Service history saved!", Toast.LENGTH_SHORT).show();
            goToMain();
        });

        // Skip — go straight to main without changing anything
        findViewById(R.id.btnSkipPms).setOnClickListener(v -> goToMain());
    }

    private void loadTasks(int vehicleId) {
        layoutTasks.removeAllViews();
        taskIds.clear();
        checkList.clear();
        dateList.clear();

        Cursor cursor = db.getPMSTasksByVehicle(vehicleId);
        if (cursor == null) return;

        while (cursor.moveToNext()) {
            int    taskId   = cursor.getInt(cursor.getColumnIndexOrThrow("id"));
            String taskName = cursor.getString(cursor.getColumnIndexOrThrow("task_name"));

            taskIds.add(taskId);

            // ── Card ──
            CardView card = new CardView(this);
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            cardParams.setMargins(0, 0, 0, 20);
            card.setLayoutParams(cardParams);
            card.setRadius(20f);
            card.setCardElevation(3f);
            card.setCardBackgroundColor(0xFFFFFFFF);

            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setPadding(40, 32, 40, 32);

            // ── Row 1: Checkbox + task name ──
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);

            CheckBox cb = new CheckBox(this);
            cb.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));

            TextView tvName = new TextView(this);
            tvName.setText(taskName);
            tvName.setTextSize(14f);
            tvName.setTypeface(null, android.graphics.Typeface.BOLD);
            tvName.setTextColor(getResources().getColor(R.color.blue_dark));
            LinearLayout.LayoutParams nameP = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            nameP.setMargins(16, 0, 0, 0);
            tvName.setLayoutParams(nameP);

            row.addView(cb);
            row.addView(tvName);

            // ── Row 2: Date picker — hidden until checkbox is ticked ──
            LinearLayout dateRow = new LinearLayout(this);
            dateRow.setOrientation(LinearLayout.HORIZONTAL);
            dateRow.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams dateRowParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            dateRowParams.setMargins(0, 16, 0, 0);
            dateRow.setLayoutParams(dateRowParams);
            dateRow.setVisibility(android.view.View.GONE); // hidden by default

            TextView tvDateLabel = new TextView(this);
            tvDateLabel.setText("Last serviced on:");
            tvDateLabel.setTextSize(12f);
            tvDateLabel.setTextColor(android.graphics.Color.GRAY);
            LinearLayout.LayoutParams labelP = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            labelP.setMargins(0, 0, 16, 0);
            tvDateLabel.setLayoutParams(labelP);

            EditText etDate = new EditText(this);
            etDate.setHint("Tap to pick date");
            etDate.setFocusable(false);
            etDate.setClickable(true);
            etDate.setBackgroundResource(R.drawable.rounded_edittext);
            etDate.setPadding(24, 0, 24, 0);
            etDate.setTextSize(13f);
            etDate.setLayoutParams(new LinearLayout.LayoutParams(
                    0, 100, 1f));

            // Default to today
            Calendar today = Calendar.getInstance();
            String todayStr = today.get(Calendar.YEAR) + "-"
                    + String.format("%02d", today.get(Calendar.MONTH) + 1) + "-"
                    + String.format("%02d", today.get(Calendar.DAY_OF_MONTH));
            etDate.setText(todayStr);

            etDate.setOnClickListener(v -> {
                Calendar c = Calendar.getInstance();
                new DatePickerDialog(this,
                        (view, y, m, d) -> etDate.setText(
                                y + "-" + String.format("%02d", m + 1)
                                        + "-" + String.format("%02d", d)),
                        c.get(Calendar.YEAR),
                        c.get(Calendar.MONTH),
                        c.get(Calendar.DAY_OF_MONTH)).show();
            });

            dateRow.addView(tvDateLabel);
            dateRow.addView(etDate);

            // ── Checkbox listener — show/hide date row ──
            cb.setOnCheckedChangeListener((buttonView, isChecked) -> {
                dateRow.setVisibility(isChecked
                        ? android.view.View.VISIBLE
                        : android.view.View.GONE);
                // Green tint when checked
                card.setCardBackgroundColor(isChecked ? 0xFFE8F5E9 : 0xFFFFFFFF);
                tvName.setTextColor(isChecked
                        ? 0xFF2E7D32
                        : getResources().getColor(R.color.blue_dark));
            });

            checkList.add(cb);
            dateList.add(etDate);

            col.addView(row);
            col.addView(dateRow);
            card.addView(col);
            layoutTasks.addView(card);
        }
        cursor.close();
    }

    private void goToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }
}