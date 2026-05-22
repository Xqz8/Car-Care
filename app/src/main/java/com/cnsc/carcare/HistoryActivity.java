package com.cnsc.carcare;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import java.util.Calendar;
import java.util.Locale;

public class HistoryActivity extends AppCompatActivity {
    DatabaseHelper db;
    SessionManager session;
    LinearLayout layoutHistory;

    // Tracks the currently active filter so the list refreshes correctly after editing
    private String activeFilter = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        overridePendingTransition(0, 0);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        ImageButton btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        db = new DatabaseHelper(this);
        session = new SessionManager(this);
        layoutHistory = findViewById(R.id.layoutHistory);

        setupNavigation();
        setupFilterSpinner();
        loadHistory(null);
    }

    private void setupFilterSpinner() {
        Spinner spinner = findViewById(R.id.spinnerFilter);
        String[] filters = {"All", "Fuel", "Maintenance", "Repair", "Insurance", "Registration", "Car Wash", "Accessories", "Other"};

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, filters);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                activeFilter = filters[position].equals("All") ? null : filters[position];
                loadHistory(activeFilter);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void setupNavigation() {
        findViewById(R.id.btnNavHome).setOnClickListener(v -> {
            startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
            overridePendingTransition(0, 0);
        });
        findViewById(R.id.btnNavHistory).setOnClickListener(v -> {
            loadHistory(activeFilter);
            Toast.makeText(this, "History Refreshed", Toast.LENGTH_SHORT).show();
        });
        findViewById(R.id.btnNavReminders).setOnClickListener(v -> {
            startActivity(new Intent(this, ReminderActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
            overridePendingTransition(0, 0);
        });
        findViewById(R.id.btnNavVehicles).setOnClickListener(v -> {
            startActivity(new Intent(this, VehicleManagementActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
            overridePendingTransition(0, 0);
        });
        findViewById(R.id.btnNavAddExpense).setOnClickListener(v -> {
            startActivity(new Intent(this, AddExpenseActivity.class));
            overridePendingTransition(0, 0);
        });
    }

    private void loadHistory(String category) {
        layoutHistory.removeAllViews();
        int vehicleId = session.getActiveVehicleId();

        Cursor cursor = (category == null)
                ? db.getExpensesByVehicle(vehicleId)
                : db.getExpensesByCategory(vehicleId, category);

        if (cursor == null || cursor.getCount() == 0) {
            TextView empty = new TextView(this);
            empty.setText("No expenses found");
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, 80, 0, 0);
            layoutHistory.addView(empty);
            if (cursor != null) cursor.close();
            return;
        }

        while (cursor.moveToNext()) {
            final int id       = cursor.getInt(0);
            final String cat   = cursor.getString(2);
            final double amount = cursor.getDouble(3);
            final String date  = cursor.getString(4);
            final int odo      = cursor.getInt(5);
            final String notes = cursor.getString(6);

            CardView card = new CardView(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, 15, 0, 15);
            card.setLayoutParams(params);
            card.setRadius(35f);
            card.setCardElevation(4f);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(45, 35, 45, 35);
            row.setGravity(Gravity.CENTER_VERTICAL);

            // --- LEFT: category + date ---
            LinearLayout left = new LinearLayout(this);
            left.setOrientation(LinearLayout.VERTICAL);
            left.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView tvCat = new TextView(this);
            tvCat.setText(getCategoryEmoji(cat) + " " + cat);
            tvCat.setTextSize(17f);
            tvCat.setTypeface(null, android.graphics.Typeface.BOLD);
            tvCat.setTextColor(getResources().getColor(R.color.blue_dark));

            TextView tvDate = new TextView(this);
            tvDate.setText(formatUserDate(date) + (odo > 0 ? " • " + odo + " km" : ""));
            tvDate.setTextColor(android.graphics.Color.GRAY);

            left.addView(tvCat);
            left.addView(tvDate);

            if (notes != null && !notes.isEmpty()) {
                TextView tvNotes = new TextView(this);
                tvNotes.setText("📝 " + notes);
                tvNotes.setTextColor(android.graphics.Color.GRAY);
                tvNotes.setTextSize(12f);
                left.addView(tvNotes);
            }

            // --- RIGHT: amount + ✏️ Edit button ---
            LinearLayout right = new LinearLayout(this);
            right.setOrientation(LinearLayout.VERTICAL);
            right.setGravity(Gravity.END);

            TextView tvAmount = new TextView(this);
            tvAmount.setText(String.format(Locale.getDefault(), "₱%.2f", amount));
            tvAmount.setTextSize(17f);
            tvAmount.setTypeface(null, android.graphics.Typeface.BOLD);
            tvAmount.setTextColor(getResources().getColor(R.color.blue_primary));

            // ✏️ Edit button — replaces the old 🗑 Delete button
            TextView btnEdit = new TextView(this);
            btnEdit.setText("✏️ Edit");
            btnEdit.setPadding(10, 10, 0, 0);
            btnEdit.setTextColor(getResources().getColor(R.color.blue_primary));
            btnEdit.setTextSize(12f);
            btnEdit.setOnClickListener(v ->
                    showEditDialog(id, cat, amount, date, odo, notes));

            right.addView(tvAmount);
            right.addView(btnEdit);

            row.addView(left);
            row.addView(right);
            card.addView(row);
            layoutHistory.addView(card);
        }
        cursor.close();
    }

    /**
     * Shows an AlertDialog with editable fields so the user can update an expense.
     * The dialog also contains a Delete option so the action is never accidental.
     */
    private void showEditDialog(int expenseId, String currentCat, double currentAmount,
                                String currentDate, int currentOdo, String currentNotes) {

        // Inflate a simple vertical form inside the dialog
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(60, 40, 60, 20);

        // Category spinner
        TextView lblCat = new TextView(this);
        lblCat.setText("Category");
        lblCat.setTextSize(13f);
        lblCat.setTextColor(android.graphics.Color.GRAY);
        form.addView(lblCat);

        String[] categories = {"Fuel", "Maintenance", "Repair", "Insurance", "Registration", "Car Wash", "Accessories", "Other"};
        Spinner spinnerCat = new Spinner(this);
        ArrayAdapter<String> catAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, categories);
        catAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCat.setAdapter(catAdapter);
        // Pre-select the current category
        for (int i = 0; i < categories.length; i++) {
            if (categories[i].equals(currentCat)) { spinnerCat.setSelection(i); break; }
        }
        form.addView(spinnerCat);

        // Amount field
        TextView lblAmount = new TextView(this);
        lblAmount.setText("Amount (₱)");
        lblAmount.setTextSize(13f);
        lblAmount.setTextColor(android.graphics.Color.GRAY);
        lblAmount.setPadding(0, 20, 0, 0);
        form.addView(lblAmount);

        EditText etAmount = new EditText(this);
        etAmount.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etAmount.setText(String.format(Locale.getDefault(), "%.2f", currentAmount));
        form.addView(etAmount);

        // Date field
        TextView lblDate = new TextView(this);
        lblDate.setText("Date");
        lblDate.setTextSize(13f);
        lblDate.setTextColor(android.graphics.Color.GRAY);
        lblDate.setPadding(0, 20, 0, 0);
        form.addView(lblDate);

        EditText etDate = new EditText(this);
        etDate.setText(currentDate);
        etDate.setFocusable(false);
        etDate.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            new DatePickerDialog(this, (view, y, m, d) ->
                    etDate.setText(String.format(Locale.getDefault(), "%d-%02d-%02d", y, m + 1, d)),
                    c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
        });
        form.addView(etDate);

        // Odometer field — validate against the expense's own original odometer,
        // NOT the vehicle's current odometer, so editing old records stays valid.
        int vehicleId = session.getActiveVehicleId();

        TextView lblOdo = new TextView(this);
        lblOdo.setText("Odometer (km)  —  original: " + currentOdo + " km");
        lblOdo.setTextSize(13f);
        lblOdo.setTextColor(android.graphics.Color.GRAY);
        lblOdo.setPadding(0, 20, 0, 0);
        form.addView(lblOdo);

        EditText etOdo = new EditText(this);
        etOdo.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        etOdo.setText(currentOdo > 0 ? String.valueOf(currentOdo) : "");
        form.addView(etOdo);

        // Notes field
        TextView lblNotes = new TextView(this);
        lblNotes.setText("Notes");
        lblNotes.setTextSize(13f);
        lblNotes.setTextColor(android.graphics.Color.GRAY);
        lblNotes.setPadding(0, 20, 0, 0);
        form.addView(lblNotes);

        EditText etNotes = new EditText(this);
        etNotes.setText(currentNotes);
        form.addView(etNotes);

        // Build the dialog
        new AlertDialog.Builder(this)
                .setTitle("Edit Expense")
                .setView(form)
                .setPositiveButton("Save Changes", (dialog, which) -> {
                    // --- Validate amount ---
                    String amountStr = etAmount.getText().toString().trim();
                    if (amountStr.isEmpty()) {
                        Toast.makeText(this, "Amount cannot be empty", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // --- Validate odometer against the expense's own original value ---
                    // Editing an old record should not require meeting the vehicle's current odometer.
                    String odoStr = etOdo.getText().toString().trim();
                    int newOdo = 0;
                    if (!odoStr.isEmpty()) {
                        newOdo = Integer.parseInt(odoStr);
                        if (newOdo < 0) {
                            Toast.makeText(this,
                                    "Odometer cannot be negative",
                                    Toast.LENGTH_LONG).show();
                            return;
                        }
                    }

                    String newCat   = spinnerCat.getSelectedItem().toString();
                    double newAmount = Double.parseDouble(amountStr);
                    String newDate  = etDate.getText().toString().trim();
                    String newNotes = etNotes.getText().toString().trim();

                    boolean success = db.updateExpense(expenseId, newCat, newAmount, newDate, newOdo, newNotes);
                    if (success) {
                        // Do NOT call updateVehicleOdometer here —
                        // editing historical data must not move the vehicle's current odometer.
                        Toast.makeText(this, "Expense updated ✅", Toast.LENGTH_SHORT).show();
                        loadHistory(activeFilter);
                    } else {
                        Toast.makeText(this, "Error updating expense", Toast.LENGTH_SHORT).show();
                    }
                })
                // Neutral button = Delete (with its own confirmation dialog)
                .setNeutralButton("Delete", (dialog, which) ->
                        confirmDelete(expenseId))
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Secondary confirmation dialog before permanently deleting an expense.
     */
    private void confirmDelete(int expenseId) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Expense")
                .setMessage("Are you sure you want to permanently delete this expense? This cannot be undone.")
                .setPositiveButton("Yes, Delete", (dialog, which) -> {
                    db.deleteExpense(expenseId);
                    Toast.makeText(this, "Expense deleted", Toast.LENGTH_SHORT).show();
                    loadHistory(activeFilter);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private String getCategoryEmoji(String category) {
        switch (category != null ? category : "") {
            case "Fuel":         return "⛽";
            case "Maintenance":  return "🔧";
            case "Repair":       return "🛠️";
            case "Insurance":    return "🛡️";
            case "Registration": return "📋";
            case "Car Wash":     return "🚿";
            case "Accessories":  return "🔩";
            case "Other":        return "⚠️";
            default:             return "💰";
        }
    }

    // ─────────────────────────────────────────────
    //  DATE FORMATTING HELPER
    // ─────────────────────────────────────────────
    private String formatUserDate(String rawDate) {
        if (rawDate == null || rawDate.isEmpty()) return "";
        try {
            java.text.SimpleDateFormat inFmt  = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            java.text.SimpleDateFormat outFmt = new java.text.SimpleDateFormat("MMM d, yyyy",  Locale.getDefault());
            java.util.Date d = inFmt.parse(rawDate);
            return d != null ? outFmt.format(d) : rawDate;
        } catch (Exception e) {
            return rawDate;
        }
    }
}