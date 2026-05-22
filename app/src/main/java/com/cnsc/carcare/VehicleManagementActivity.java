package com.cnsc.carcare;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

public class VehicleManagementActivity extends AppCompatActivity {
    DatabaseHelper db;
    SessionManager session;
    LinearLayout layoutVehicles;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        overridePendingTransition(0, 0);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vehicle_management);

        db = new DatabaseHelper(this);
        session = new SessionManager(this);
        layoutVehicles = findViewById(R.id.layoutVehicles);

        ImageButton btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        findViewById(R.id.btnAddVehicle).setOnClickListener(v -> {
            startActivity(new Intent(this, AddVehicleActivity.class));
            overridePendingTransition(0, 0);
        });

        setupNavigation();
        loadVehicles();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadVehicles();
    }

    private void setupNavigation() {
        findViewById(R.id.btnNavHome).setOnClickListener(v -> {
            startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
            overridePendingTransition(0, 0);
        });
        findViewById(R.id.btnNavHistory).setOnClickListener(v -> {
            startActivity(new Intent(this, HistoryActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
            overridePendingTransition(0, 0);
        });
        findViewById(R.id.btnNavReminders).setOnClickListener(v -> {
            startActivity(new Intent(this, ReminderActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
            overridePendingTransition(0, 0);
        });
        findViewById(R.id.btnNavVehicles).setOnClickListener(v -> { /* already here */ });
        findViewById(R.id.btnNavAddExpense).setOnClickListener(v -> {
            startActivity(new Intent(this, AddExpenseActivity.class));
            overridePendingTransition(0, 0);
        });
    }

    private void loadVehicles() {
        layoutVehicles.removeAllViews();
        int userId   = session.getUserId();
        int activeId = session.getActiveVehicleId();
        Cursor cursor = db.getVehiclesByUser(userId);

        if (cursor == null || cursor.getCount() == 0) {
            TextView empty = new TextView(this);
            empty.setText("No vehicles yet. Tap + Add to get started!");
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, 60, 0, 0);
            layoutVehicles.addView(empty);
            return;
        }

        while (cursor.moveToNext()) {
            final int id       = cursor.getInt(0);    // id
            // index 1 = user_id (not needed here)
            final String name  = cursor.getString(2); // name (nickname)
            final String type  = cursor.getString(3); // type
            final String brand = cursor.getString(4); // brand
            final String model = cursor.getString(5); // model
            final String year  = cursor.getString(6); // year
            // index 7 = plate (not needed here)
            final int odometer = cursor.getInt(8);    // odometer
            final boolean isActive = (id == activeId);

            // --- Card ---
            CardView card = new CardView(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, 0, 0, 24);
            card.setLayoutParams(params);
            card.setRadius(28f);
            card.setCardElevation(isActive ? 12f : 4f);

            if (isActive) {
                card.setCardBackgroundColor(getResources().getColor(android.R.color.white));
            }

            LinearLayout inner = new LinearLayout(this);
            inner.setOrientation(LinearLayout.HORIZONTAL);
            inner.setPadding(40, 40, 40, 40);
            inner.setGravity(Gravity.CENTER_VERTICAL);

            // --- Text group (LEFT SIDE) ---
            LinearLayout textGroup = new LinearLayout(this);
            textGroup.setOrientation(LinearLayout.VERTICAL);
            textGroup.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            // Line 1: emoji + nickname
            TextView tvLine1 = new TextView(this);
            tvLine1.setText((isActive ? "✅ " : "🚗 ") + name);
            tvLine1.setTextSize(18f);
            tvLine1.setTextColor(getResources().getColor(R.color.blue_dark));
            tvLine1.setTypeface(null, android.graphics.Typeface.BOLD);

            // Line 2: type • brand model
            TextView tvLine2 = new TextView(this);
            tvLine2.setText(type + "  •  " + brand + " " + model);
            tvLine2.setTextSize(13f);
            tvLine2.setTextColor(android.graphics.Color.GRAY);

            // Line 3: odometer
            TextView tvLine3 = new TextView(this);
            tvLine3.setText("🛣 " + odometer + " km");
            tvLine3.setTextSize(13f);
            tvLine3.setTextColor(android.graphics.Color.GRAY);

            textGroup.addView(tvLine1);
            textGroup.addView(tvLine2);
            textGroup.addView(tvLine3);

            // --- Button group (RIGHT SIDE) ---
            LinearLayout btnGroup = new LinearLayout(this);
            btnGroup.setOrientation(LinearLayout.VERTICAL);
            btnGroup.setGravity(Gravity.CENTER);

            if (!isActive) {
                Button btnSelect = new Button(this);
                btnSelect.setText("Select");
                btnSelect.setAllCaps(false);
                btnSelect.setOnClickListener(v -> {
                    session.setActiveVehicle(id);
                    Toast.makeText(this, name + " set as active!", Toast.LENGTH_SHORT).show();
                    loadVehicles();
                });

                ImageButton btnDelete = new ImageButton(this);
                btnDelete.setImageResource(android.R.drawable.ic_menu_delete);
                btnDelete.setBackground(null);
                btnDelete.setPadding(0, 8, 0, 0);
                btnDelete.setOnClickListener(v -> confirmVehicleDelete(id, name));

                ImageButton btnEdit = new ImageButton(this);
                btnEdit.setImageResource(android.R.drawable.ic_menu_edit);
                btnEdit.setBackground(null);
                btnEdit.setPadding(0, 8, 0, 0);
                final String fType = type, fBrand = brand, fModel = model, fYear = year;
                final int fOdo = odometer;
                // plate is at index 7
                final String fPlate = cursor.getString(7);
                btnEdit.setOnClickListener(v -> showEditVehicleDialog(id, name, fType,
                        fBrand, fModel, fYear, fPlate, fOdo));

                btnGroup.addView(btnSelect);
                LinearLayout editDeleteRow = new LinearLayout(this);
                editDeleteRow.setOrientation(LinearLayout.HORIZONTAL);
                editDeleteRow.addView(btnEdit);
                editDeleteRow.addView(btnDelete);
                btnGroup.addView(editDeleteRow);
            } else {
                // Active vehicle: show "In use" label + edit button
                TextView tvActive = new TextView(this);
                tvActive.setText("In use");
                tvActive.setTextSize(11f);
                tvActive.setTextColor(getResources().getColor(R.color.blue_primary));

                ImageButton btnEdit = new ImageButton(this);
                btnEdit.setImageResource(android.R.drawable.ic_menu_edit);
                btnEdit.setBackground(null);
                btnEdit.setPadding(0, 8, 0, 0);
                final String fType = type, fBrand = brand, fModel = model, fYear = year;
                final int fOdo = odometer;
                final String fPlate = cursor.getString(7);
                btnEdit.setOnClickListener(v -> showEditVehicleDialog(id, name, fType,
                        fBrand, fModel, fYear, fPlate, fOdo));

                btnGroup.addView(tvActive);
                btnGroup.addView(btnEdit);
            }

            inner.addView(textGroup);
            inner.addView(btnGroup);
            card.addView(inner);
            layoutVehicles.addView(card);
        }
        cursor.close();
    }

    private void showEditVehicleDialog(int vehicleId, String currentName, String currentType,
                                       String currentBrand, String currentModel, String currentYear,
                                       String currentPlate, int currentOdometer) {
        androidx.appcompat.app.AlertDialog.Builder builder =
                new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("✏️ Edit Vehicle");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(60, 40, 60, 20);

        // Nickname
        TextView tvName = new TextView(this);
        tvName.setText("Nickname:");
        tvName.setTextSize(13f);
        tvName.setPadding(0, 0, 0, 6);
        EditText etName = new EditText(this);
        etName.setText(currentName);
        etName.setBackground(getResources().getDrawable(R.drawable.rounded_edittext));
        etName.setPadding(24, 16, 24, 16);
        LinearLayout.LayoutParams fieldParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 120);
        fieldParams.setMargins(0, 0, 0, 20);
        etName.setLayoutParams(fieldParams);

        // Type spinner
        TextView tvType = new TextView(this);
        tvType.setText("Type:");
        tvType.setTextSize(13f);
        tvType.setPadding(0, 0, 0, 6);
        android.widget.Spinner spinnerType = new android.widget.Spinner(this);
        String[] types = {"Car", "SUV / AUV", "Van", "Truck", "Motorcycle"};
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, types);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerType.setAdapter(adapter);
        for (int i = 0; i < types.length; i++) {
            if (types[i].equals(currentType)) { spinnerType.setSelection(i); break; }
        }
        LinearLayout.LayoutParams spinnerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        spinnerParams.setMargins(0, 0, 0, 20);
        spinnerType.setLayoutParams(spinnerParams);

        // Brand
        TextView tvBrand = new TextView(this);
        tvBrand.setText("Brand:");
        tvBrand.setTextSize(13f);
        tvBrand.setPadding(0, 0, 0, 6);
        EditText etBrand = new EditText(this);
        etBrand.setText(currentBrand);
        etBrand.setBackground(getResources().getDrawable(R.drawable.rounded_edittext));
        etBrand.setPadding(24, 16, 24, 16);
        etBrand.setLayoutParams(fieldParams);

        // Model
        TextView tvModel = new TextView(this);
        tvModel.setText("Model:");
        tvModel.setTextSize(13f);
        tvModel.setPadding(0, 0, 0, 6);
        EditText etModel = new EditText(this);
        etModel.setText(currentModel);
        etModel.setBackground(getResources().getDrawable(R.drawable.rounded_edittext));
        etModel.setPadding(24, 16, 24, 16);
        etModel.setLayoutParams(fieldParams);

        // Year
        TextView tvYear = new TextView(this);
        tvYear.setText("Year:");
        tvYear.setTextSize(13f);
        tvYear.setPadding(0, 0, 0, 6);
        EditText etYear = new EditText(this);
        etYear.setText(currentYear);
        etYear.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        etYear.setBackground(getResources().getDrawable(R.drawable.rounded_edittext));
        etYear.setPadding(24, 16, 24, 16);
        etYear.setLayoutParams(fieldParams);

        // Plate
        TextView tvPlate = new TextView(this);
        tvPlate.setText("Plate Number:");
        tvPlate.setTextSize(13f);
        tvPlate.setPadding(0, 0, 0, 6);
        EditText etPlate = new EditText(this);
        etPlate.setText(currentPlate);
        etPlate.setBackground(getResources().getDrawable(R.drawable.rounded_edittext));
        etPlate.setPadding(24, 16, 24, 16);
        etPlate.setLayoutParams(fieldParams);

        // Odometer
        TextView tvOdo = new TextView(this);
        tvOdo.setText("Odometer (km):");
        tvOdo.setTextSize(13f);
        tvOdo.setPadding(0, 0, 0, 6);
        EditText etOdo = new EditText(this);
        etOdo.setText(String.valueOf(currentOdometer));
        etOdo.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        etOdo.setBackground(getResources().getDrawable(R.drawable.rounded_edittext));
        etOdo.setPadding(24, 16, 24, 16);
        etOdo.setLayoutParams(fieldParams);

        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        layout.addView(tvName);     layout.addView(etName);
        layout.addView(tvType);     layout.addView(spinnerType);
        layout.addView(tvBrand);    layout.addView(etBrand);
        layout.addView(tvModel);    layout.addView(etModel);
        layout.addView(tvYear);     layout.addView(etYear);
        layout.addView(tvPlate);    layout.addView(etPlate);
        layout.addView(tvOdo);      layout.addView(etOdo);
        sv.addView(layout);

        builder.setView(sv);
        builder.setPositiveButton("Save", (dialog, which) -> {
            String name   = etName.getText().toString().trim();
            String type   = spinnerType.getSelectedItem().toString();
            String brand  = etBrand.getText().toString().trim();
            String model  = etModel.getText().toString().trim();
            String year   = etYear.getText().toString().trim();
            String plate  = etPlate.getText().toString().trim();
            String odoStr = etOdo.getText().toString().trim();

            if (name.isEmpty() || brand.isEmpty() || model.isEmpty() || year.isEmpty()) {
                Toast.makeText(this, "Please fill in all required fields", Toast.LENGTH_SHORT).show();
                return;
            }
            int odo = odoStr.isEmpty() ? currentOdometer : Integer.parseInt(odoStr);
            boolean updated = db.updateVehicle(vehicleId, name, type, brand, model, year, plate, odo);
            if (updated) {
                Toast.makeText(this, name + " updated! ✅", Toast.LENGTH_SHORT).show();
                loadVehicles();
            } else {
                Toast.makeText(this, "Failed to update vehicle", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void confirmVehicleDelete(int vehicleId, String vehicleName) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Vehicle")
                .setMessage("Are you sure you want to remove \"" + vehicleName + "\"?\n\n"
                        + "All expenses, fuel logs, and reminders for this vehicle will also be permanently deleted.")
                .setPositiveButton("Yes, Delete", (dialog, which) -> {
                    db.deleteVehicle(vehicleId);
                    Toast.makeText(this, vehicleName + " removed", Toast.LENGTH_SHORT).show();
                    loadVehicles();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}