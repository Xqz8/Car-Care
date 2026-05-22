package com.cnsc.carcare;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import android.widget.GridLayout;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.PercentFormatter;
import com.github.mikephil.charting.utils.ColorTemplate;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MainActivity extends AppCompatActivity {
    DatabaseHelper db;
    SessionManager session;

    private PieChart pieChart;
    private BarChart barChart;
    private LinearLayout layoutHomeReminders;

    private int calendarYear;
    private int calendarMonth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        db = new DatabaseHelper(this);
        session = new SessionManager(this);

        pieChart = findViewById(R.id.homePieChart);
        barChart = findViewById(R.id.homeBarChart);
        layoutHomeReminders = findViewById(R.id.layoutHomeReminders);

        Calendar now = Calendar.getInstance();
        calendarYear  = now.get(Calendar.YEAR);
        calendarMonth = now.get(Calendar.MONTH);

        setupButtons();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadDashboardData();
    }

    private void loadDashboardData() {
        int vehicleId = session.getActiveVehicleId();

        TextView tvGreeting = findViewById(R.id.tvGreeting);
        TextView tvUserName = findViewById(R.id.tvUserName);

        if (tvGreeting != null) {
            int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
            if (hour < 12)      tvGreeting.setText("Good Morning 👋");
            else if (hour < 18) tvGreeting.setText("Good Afternoon 👋");
            else                tvGreeting.setText("Good Evening 👋");
        }
        if (tvUserName != null) tvUserName.setText(session.getUserName());

        if (vehicleId != -1) {
            Cursor v = db.getVehicleById(vehicleId);
            if (v != null && v.moveToFirst()) {
                TextView tvVehicleName = findViewById(R.id.tvVehicleName);
                TextView tvVehicleInfo = findViewById(R.id.tvVehicleInfo);
                TextView tvOdometer    = findViewById(R.id.tvOdometer);
                if (tvVehicleName != null) tvVehicleName.setText(v.getString(2));
                if (tvVehicleInfo != null) {
                    String info = v.getString(4) + " " + v.getString(5)
                            + " • " + v.getString(6);
                    tvVehicleInfo.setText(info);
                }
                if (tvOdometer != null)
                    tvOdometer.setText("Odometer: " + v.getInt(8) + " km");
            }
            if (v != null) v.close();

            updateQuickStats(vehicleId);
            displayUpcomingReminders(vehicleId);
            setupPieChart(vehicleId);
            setupBarChart(vehicleId);
            buildCalendar(vehicleId);
        } else {
            // No vehicle selected — show friendly first-launch empty state
            if (layoutHomeReminders != null) {
                layoutHomeReminders.removeAllViews();

                LinearLayout emptyState = new LinearLayout(this);
                emptyState.setOrientation(LinearLayout.VERTICAL);
                emptyState.setGravity(android.view.Gravity.CENTER);
                emptyState.setPadding(32, 48, 32, 48);

                TextView tvIcon = new TextView(this);
                tvIcon.setText("🚗");
                tvIcon.setTextSize(52f);
                tvIcon.setGravity(android.view.Gravity.CENTER);
                emptyState.addView(tvIcon);

                TextView tvTitle = new TextView(this);
                tvTitle.setText("Welcome to CarCare!");
                tvTitle.setTextSize(18f);
                tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
                tvTitle.setTextColor(getResources().getColor(R.color.blue_dark));
                tvTitle.setGravity(android.view.Gravity.CENTER);
                tvTitle.setPadding(0, 16, 0, 8);
                emptyState.addView(tvTitle);

                TextView tvSub = new TextView(this);
                tvSub.setText("Start by adding your first vehicle to track fuel, expenses, and maintenance.");
                tvSub.setTextSize(14f);
                tvSub.setTextColor(android.graphics.Color.GRAY);
                tvSub.setGravity(android.view.Gravity.CENTER);
                tvSub.setPadding(0, 0, 0, 24);
                emptyState.addView(tvSub);

                Button btnAddVehicle = new Button(this);
                btnAddVehicle.setText("Add your first vehicle →");
                btnAddVehicle.setAllCaps(false);
                btnAddVehicle.setTextSize(15f);
                btnAddVehicle.setTextColor(android.graphics.Color.WHITE);
                btnAddVehicle.setBackgroundResource(R.drawable.rounded_button_blue);
                btnAddVehicle.setOnClickListener(v -> {
                    startActivity(new android.content.Intent(this, VehicleManagementActivity.class)
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
                    overridePendingTransition(0, 0);
                });
                emptyState.addView(btnAddVehicle);

                layoutHomeReminders.addView(emptyState);
            }
        }
    }

    // ─────────────────────────────────────────────
    //  CALENDAR
    // ─────────────────────────────────────────────

    private void buildCalendar(int vehicleId) {
        LinearLayout layout = findViewById(R.id.layoutCalendar);
        if (layout == null) return;
        layout.removeAllViews();

        // ── Collect expense dates ──
        Set<String> markedDates = new HashSet<>();
        Cursor c = db.getExpensesByVehicle(vehicleId);
        if (c != null) {
            while (c.moveToNext()) {
                String date = c.getString(4);
                if (date != null) markedDates.add(date);
            }
            c.close();
        }

        // ── Collect PMS due dates ──
        Map<String, List<String>> pmsDueDates = new HashMap<>();
        Cursor pms = db.getPMSTasksByVehicle(vehicleId);
        if (pms != null) {
            while (pms.moveToNext()) {
                String taskName      = pms.getString(
                        pms.getColumnIndexOrThrow("task_name"));
                int    intervalMonths = pms.getInt(
                        pms.getColumnIndexOrThrow("interval_months"));
                String lastDoneDate  = pms.getString(
                        pms.getColumnIndexOrThrow("last_done_date"));
                int    isConfirmed   = pms.getInt(
                        pms.getColumnIndexOrThrow("is_user_confirmed"));

                if (isConfirmed == 1 && intervalMonths > 0
                        && lastDoneDate != null && !lastDoneDate.isEmpty()) {
                    try {
                        SimpleDateFormat sdf =
                                new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                        java.util.Date last = sdf.parse(lastDoneDate);
                        Calendar dueCal = Calendar.getInstance();
                        dueCal.setTime(last);
                        dueCal.add(Calendar.MONTH, intervalMonths);
                        String dueDate = sdf.format(dueCal.getTime());
                        if (!pmsDueDates.containsKey(dueDate))
                            pmsDueDates.put(dueDate, new ArrayList<>());
                        pmsDueDates.get(dueDate).add(taskName);
                    } catch (Exception ignored) {}
                }
            }
            pms.close();
        }

        // ── Collect maintenance done dates ──
        Map<String, List<String>> maintenanceDoneDates = db.getMaintenanceDoneDates(vehicleId);

        // ── Month/Year header ──
        String[] monthNames = {
                "January","February","March","April","May","June",
                "July","August","September","October","November","December"
        };

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(8, 16, 8, 8);

        Button btnPrev = new Button(this);
        btnPrev.setText("‹");
        btnPrev.setAllCaps(false);
        btnPrev.setBackground(null);
        btnPrev.setTextSize(20f);
        btnPrev.setTextColor(getResources().getColor(R.color.blue_primary));

        TextView tvMonthYear = new TextView(this);
        tvMonthYear.setTextSize(16f);
        tvMonthYear.setTypeface(null, android.graphics.Typeface.BOLD);
        tvMonthYear.setGravity(Gravity.CENTER);
        tvMonthYear.setTextColor(getResources().getColor(R.color.blue_dark));
        tvMonthYear.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        tvMonthYear.setText(monthNames[calendarMonth] + " " + calendarYear);

        Button btnNext = new Button(this);
        btnNext.setText("›");
        btnNext.setAllCaps(false);
        btnNext.setBackground(null);
        btnNext.setTextSize(20f);
        btnNext.setTextColor(getResources().getColor(R.color.blue_primary));

        header.addView(btnPrev);
        header.addView(tvMonthYear);
        header.addView(btnNext);
        layout.addView(header);

        // ── Day-of-week labels ──
        GridLayout dayLabels = new GridLayout(this);
        dayLabels.setColumnCount(7);
        String[] days = {"Sun","Mon","Tue","Wed","Thurs","Fri","Sat"};
        for (String day : days) {
            TextView tv = new TextView(this);
            tv.setText(day);
            tv.setGravity(Gravity.CENTER);
            tv.setTypeface(null, android.graphics.Typeface.BOLD);
            tv.setTextSize(12f);
            tv.setTextColor(getResources().getColor(R.color.blue_dark));
            tv.setPadding(0, 8, 0, 8);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = 0;
            lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            tv.setLayoutParams(lp);
            dayLabels.addView(tv);
        }
        layout.addView(dayLabels);

        // ── Date grid ──
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(7);

        Calendar startCal = Calendar.getInstance();
        startCal.set(calendarYear, calendarMonth, 1);
        int startDay    = startCal.get(Calendar.DAY_OF_WEEK) - 1;
        int daysInMonth = startCal.getActualMaximum(Calendar.DAY_OF_MONTH);

        for (int i = 0; i < startDay; i++) {
            TextView blank = new TextView(this);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = 0;
            lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            blank.setLayoutParams(lp);
            grid.addView(blank);
        }

        Calendar today = Calendar.getInstance();

        for (int day = 1; day <= daysInMonth; day++) {
            String dateStr = String.format(Locale.getDefault(),
                    "%d-%02d-%02d", calendarYear, calendarMonth + 1, day);
            boolean hasExpense = markedDates.contains(dateStr);
            boolean hasPMS     = pmsDueDates.containsKey(dateStr);
            boolean hasDone    = maintenanceDoneDates.containsKey(dateStr);
            boolean isToday    = (day == today.get(Calendar.DAY_OF_MONTH))
                    && (calendarMonth == today.get(Calendar.MONTH))
                    && (calendarYear  == today.get(Calendar.YEAR));

            // Build compact indicator dots below the day number
            // Each dot is a small colored circle character at reduced size
            StringBuilder dots = new StringBuilder();
            if (hasExpense) dots.append("★");
            if (hasPMS)     dots.append("⚙");
            if (hasDone)    dots.append("✔");

            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            cell.setPadding(2, 6, 2, 6);

            TextView tv = new TextView(this);
            tv.setText(String.valueOf(day));
            tv.setGravity(Gravity.CENTER);
            tv.setTextSize(12f);

            if (isToday) {
                tv.setBackground(getTodayCircleDrawable());
                tv.setTextColor(android.graphics.Color.WHITE);
                tv.setTypeface(null, android.graphics.Typeface.BOLD);
                tv.setPadding(10, 6, 10, 6);
            } else if (hasDone) {
                tv.setTextColor(0xFF2E7D32);
                tv.setTextColor(getResources().getColor(R.color.blue_dark));
            } else if (hasPMS) {
                tv.setTextColor(0xFFE65100);
            } else if (hasExpense) {
                tv.setTextColor(getResources().getColor(R.color.blue_primary));
            } else {
                tv.setTextColor(getResources().getColor(R.color.blue_dark));
            }

            cell.addView(tv);

            if (dots.length() > 0) {
                TextView tvDots = new TextView(this);
                tvDots.setText(dots.toString());
                tvDots.setGravity(Gravity.CENTER);
                tvDots.setTextSize(7f);  // tiny dots that fit even with 3 icons
                tvDots.setPadding(0, 0, 0, 2);

                // Color each character individually
                android.text.SpannableString ss = new android.text.SpannableString(dots.toString());
                int pos = 0;
                if (hasExpense) {
                    ss.setSpan(new android.text.style.ForegroundColorSpan(0xFFE6A817),
                            pos, pos + 1, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    pos++;
                }
                if (hasPMS) {
                    ss.setSpan(new android.text.style.ForegroundColorSpan(0xFFE65100),
                            pos, pos + 1, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    pos++;
                }
                if (hasDone) {
                    ss.setSpan(new android.text.style.ForegroundColorSpan(0xFF2E7D32),
                            pos, pos + 1, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                tvDots.setText(ss);
                cell.addView(tvDots);
            }

            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = 0;
            lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            cell.setLayoutParams(lp);

            if (hasExpense || hasPMS || hasDone) {
                final List<String> pmsTasks = hasPMS
                        ? pmsDueDates.get(dateStr) : new ArrayList<>();
                final List<String> doneTasks = hasDone
                        ? maintenanceDoneDates.get(dateStr) : new ArrayList<>();
                cell.setOnClickListener(v ->
                        showDayDetails(vehicleId, dateStr, pmsTasks, doneTasks));
            }
            grid.addView(cell);
        }
        layout.addView(grid);

        // ── Legend ──
        LinearLayout legend = new LinearLayout(this);
        legend.setOrientation(LinearLayout.HORIZONTAL);
        legend.setPadding(8, 12, 8, 4);
        legend.setGravity(Gravity.CENTER_VERTICAL);

        TextView tvLeg1 = new TextView(this);
        tvLeg1.setText("★ Expense  ");
        tvLeg1.setTextSize(11f);
        tvLeg1.setTextColor(0xFFE6A817);

        TextView tvLeg2 = new TextView(this);
        tvLeg2.setText("⚙ Due  ");
        tvLeg2.setTextSize(11f);
        tvLeg2.setTextColor(0xFFE65100);

        TextView tvLeg3 = new TextView(this);
        tvLeg3.setText("✔ Done");
        tvLeg3.setTextSize(11f);
        tvLeg3.setTextColor(0xFF2E7D32);

        legend.addView(tvLeg1);
        legend.addView(tvLeg2);
        legend.addView(tvLeg3);
        layout.addView(legend);

        // ── Navigation ──
        btnPrev.setOnClickListener(v -> {
            calendarMonth--;
            if (calendarMonth < 0) { calendarMonth = 11; calendarYear--; }
            buildCalendar(vehicleId);
        });
        btnNext.setOnClickListener(v -> {
            calendarMonth++;
            if (calendarMonth > 11) { calendarMonth = 0; calendarYear++; }
            buildCalendar(vehicleId);
        });
    }

    private android.graphics.drawable.ShapeDrawable getTodayCircleDrawable() {
        android.graphics.drawable.ShapeDrawable circle =
                new android.graphics.drawable.ShapeDrawable(
                        new android.graphics.drawable.shapes.OvalShape());
        circle.getPaint().setColor(
                getResources().getColor(R.color.blue_primary));
        circle.setIntrinsicWidth(40);
        circle.setIntrinsicHeight(40);
        return circle;
    }

    private void showDayDetails(int vehicleId, String date,
                                List<String> pmsTasks, List<String> doneTasks) {
        // Format date nicely for the title: "2026-05-13" → "May 13, 2026"
        String titleDate = date;
        try {
            SimpleDateFormat inFmt  = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            SimpleDateFormat outFmt = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
            java.util.Date d = inFmt.parse(date);
            if (d != null) titleDate = outFmt.format(d);
        } catch (Exception ignored) {}

        StringBuilder sb = new StringBuilder();

        // ── Expenses ──
        Cursor c = db.getExpensesByVehicleAndDate(vehicleId, date);
        if (c != null) {
            boolean hasAny = false;
            StringBuilder expSb = new StringBuilder();
            while (c.moveToNext()) {
                hasAny = true;
                expSb.append("  • ")
                        .append(c.getString(2))
                        .append(" — ₱")
                        .append(String.format(Locale.getDefault(), "%.2f", c.getDouble(3)))
                        .append("\n");
                String notes = c.getString(6);
                if (notes != null && !notes.isEmpty())
                    expSb.append("    Notes: ").append(notes).append("\n");
            }
            c.close();
            if (hasAny) {
                sb.append("★ Expenses\n").append(expSb);
            }
        }

        // ── Maintenance Done ──
        if (doneTasks != null && !doneTasks.isEmpty()) {
            if (sb.length() > 0) sb.append("\n");
            sb.append("✔ Maintenance Done\n");
            for (String task : doneTasks)
                sb.append("  • ").append(task).append("\n");
        }

        // ── Maintenance Due ──
        if (!pmsTasks.isEmpty()) {
            if (sb.length() > 0) sb.append("\n");
            sb.append("⚙ Maintenance Due\n");
            for (String task : pmsTasks)
                sb.append("  • ").append(task).append("\n");
        }

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Records for " + titleDate)
                .setMessage(sb.length() > 0 ? sb.toString().trim() : "No records found.")
                .setPositiveButton("OK", null)
                .show();
    }

    // ─────────────────────────────────────────────
    //  QUICK STATS
    // ─────────────────────────────────────────────

    private void updateQuickStats(int vehicleId) {
        TextView tvTotalExpenses = findViewById(R.id.tvTotalExpenses);
        if (tvTotalExpenses != null) {
            double total = db.getTotalExpensesByVehicle(vehicleId);
            tvTotalExpenses.setText(
                    String.format(Locale.getDefault(), "₱%.2f", total));
        }

        TextView tvMonthlyExpenses = findViewById(R.id.tvMonthlyExpenses);
        if (tvMonthlyExpenses != null) {
            String currentMonth = new SimpleDateFormat("yyyy-MM", Locale.getDefault())
                    .format(Calendar.getInstance().getTime());
            double monthly = db.getMonthlyExpenses(vehicleId, currentMonth);
            tvMonthlyExpenses.setText(
                    String.format(Locale.getDefault(), "₱%.2f", monthly));
        }

        TextView tvFuelEfficiency = findViewById(R.id.tvFuelEfficiency);
        if (tvFuelEfficiency != null) {
            double avgEfficiency = db.getAverageFuelEfficiency(vehicleId);
            tvFuelEfficiency.setText(
                    String.format(Locale.getDefault(), "%.1f km/L", avgEfficiency));
        }

        TextView tvReminderCount = findViewById(R.id.tvReminderCount);
        if (tvReminderCount != null) {
            int overdue  = db.getOverduePMSCount(vehicleId);
            int upcoming = db.getUpcomingPMSCount(vehicleId);
            int total    = overdue + upcoming;
            tvReminderCount.setText(String.valueOf(total));
            tvReminderCount.setTextColor(overdue > 0
                    ? getResources().getColor(android.R.color.holo_red_dark)
                    : getResources().getColor(R.color.blue_dark));
        }
    }

    // ─────────────────────────────────────────────
    //  UPCOMING MAINTENANCE
    // ─────────────────────────────────────────────

    private void displayUpcomingReminders(int vehicleId) {
        if (layoutHomeReminders == null) return;
        layoutHomeReminders.removeAllViews();

        int currentOdo = db.getVehicleOdometer(vehicleId);
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                .format(Calendar.getInstance().getTime());
        Calendar thirtyDaysCal = Calendar.getInstance();
        thirtyDaysCal.add(Calendar.DAY_OF_YEAR, 30);
        String thirtyDaysLater = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                .format(thirtyDaysCal.getTime());

        List<String[]> overdueTasks  = new ArrayList<>();
        List<String[]> upcomingTasks = new ArrayList<>();

        Cursor cursor = db.getPMSTasksByVehicle(vehicleId);
        if (cursor != null) {
            while (cursor.moveToNext()) {
                String taskName       = cursor.getString(
                        cursor.getColumnIndexOrThrow("task_name"));
                int    lastKm         = cursor.getInt(
                        cursor.getColumnIndexOrThrow("last_done_km"));
                int    intervalKm     = cursor.getInt(
                        cursor.getColumnIndexOrThrow("interval_km"));
                String lastDate       = cursor.getString(
                        cursor.getColumnIndexOrThrow("last_done_date"));
                int    intervalMonths = cursor.getInt(
                        cursor.getColumnIndexOrThrow("interval_months"));
                int    isConfirmed    = cursor.getInt(
                        cursor.getColumnIndexOrThrow("is_user_confirmed"));

                boolean isOverdue  = false;
                boolean isUpcoming = false;

                if (intervalKm > 0) {
                    int nextDueKm = lastKm + intervalKm;
                    if (currentOdo >= nextDueKm) {
                        isOverdue = true;
                    } else {
                        int window = Math.max(1000, intervalKm / 5);
                        if (currentOdo >= nextDueKm - window) isUpcoming = true;
                    }
                }

                if (!isOverdue && !isUpcoming
                        && intervalMonths > 0 && lastDate != null) {
                    try {
                        SimpleDateFormat sdf =
                                new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                        java.util.Date last = sdf.parse(lastDate);
                        Calendar dueCal = Calendar.getInstance();
                        dueCal.setTime(last);
                        dueCal.add(Calendar.MONTH, intervalMonths);
                        String dueDate = sdf.format(dueCal.getTime());
                        if (today.compareTo(dueDate) >= 0)
                            isOverdue = true;
                        else if (thirtyDaysLater.compareTo(dueDate) >= 0)
                            isUpcoming = true;
                    } catch (Exception ignored) {}
                }

                if (!isOverdue && !isUpcoming && isConfirmed == 1)
                    isUpcoming = true;

                if (isOverdue)       overdueTasks.add(new String[]{taskName});
                else if (isUpcoming) upcomingTasks.add(new String[]{taskName});
            }
            cursor.close();
        }

        boolean hasAny = !overdueTasks.isEmpty() || !upcomingTasks.isEmpty();

        // Also check custom maintenance tasks (from reminders table)
        List<String[]> customTasks = new ArrayList<>();
        Cursor customCursor = db.getUpcomingCustomTasks(vehicleId);
        if (customCursor != null) {
            while (customCursor.moveToNext()) {
                String type = customCursor.getString(customCursor.getColumnIndexOrThrow("type"));
                String dueDate = customCursor.getString(customCursor.getColumnIndexOrThrow("due_date"));
                customTasks.add(new String[]{type, dueDate != null ? dueDate : ""});
            }
            customCursor.close();
        }

        if (!hasAny && customTasks.isEmpty()) {
            TextView tv = new TextView(this);
            tv.setText("No upcoming maintenance tasks.");
            tv.setTextColor(Color.GRAY);
            layoutHomeReminders.addView(tv);
            return;
        }

        int shown = 0;
        final int MAX_SHOWN = 3;

        for (String[] task : overdueTasks) {
            if (shown >= MAX_SHOWN) break;
            TextView tv = new TextView(this);
            tv.setText("⚠ " + task[0]);
            tv.setTextColor(getResources().getColor(
                    android.R.color.holo_red_dark));
            tv.setPadding(0, 8, 0, 8);
            tv.setTextSize(14f);
            layoutHomeReminders.addView(tv);
            shown++;
        }

        for (String[] task : upcomingTasks) {
            if (shown >= MAX_SHOWN) break;
            TextView tv = new TextView(this);
            tv.setText("🔔 " + task[0]);
            tv.setTextColor(getResources().getColor(R.color.blue_dark));
            tv.setPadding(0, 8, 0, 8);
            tv.setTextSize(14f);
            layoutHomeReminders.addView(tv);
            shown++;
        }

        int total = overdueTasks.size() + upcomingTasks.size();
        if (total > MAX_SHOWN) {
            TextView tvMore = new TextView(this);
            tvMore.setText("+" + (total - MAX_SHOWN) + " more → View Maintenance");
            tvMore.setTextColor(getResources().getColor(R.color.blue_primary));
            tvMore.setTextSize(13f);
            tvMore.setTypeface(null, android.graphics.Typeface.BOLD);
            tvMore.setPadding(0, 8, 0, 0);
            tvMore.setOnClickListener(v -> {
                startActivity(new Intent(this, ReminderActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
                overridePendingTransition(0, 0);
            });
            layoutHomeReminders.addView(tvMore);
        }

        // Show custom maintenance tasks (from reminders table)
        if (!customTasks.isEmpty()) {
            TextView tvCustomHeader = new TextView(this);
            tvCustomHeader.setText("📌 Custom Tasks:");
            tvCustomHeader.setTextColor(getResources().getColor(R.color.blue_dark));
            tvCustomHeader.setPadding(0, 12, 0, 4);
            tvCustomHeader.setTextSize(13f);
            tvCustomHeader.setTypeface(null, android.graphics.Typeface.BOLD);
            layoutHomeReminders.addView(tvCustomHeader);

            int shownCustom = 0;
            for (String[] task : customTasks) {
                if (shownCustom >= 2) break;
                TextView tv = new TextView(this);
                String label = "🔔 " + task[0];
                if (!task[1].isEmpty()) label += " — due " + formatUserDate(task[1]);
                tv.setText(label);
                tv.setTextColor(getResources().getColor(R.color.blue_dark));
                tv.setPadding(0, 6, 0, 6);
                tv.setTextSize(13f);
                layoutHomeReminders.addView(tv);
                shownCustom++;
            }
            if (customTasks.size() > 2) {
                TextView tvMore2 = new TextView(this);
                tvMore2.setText("+" + (customTasks.size() - 2) + " more custom tasks");
                tvMore2.setTextColor(getResources().getColor(R.color.blue_primary));
                tvMore2.setTextSize(12f);
                tvMore2.setPadding(0, 4, 0, 0);
                tvMore2.setOnClickListener(v -> {
                    startActivity(new Intent(this, ReminderActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
                    overridePendingTransition(0, 0);
                });
                layoutHomeReminders.addView(tvMore2);
            }
        }
    }

    // ─────────────────────────────────────────────
    //  CHARTS
    // ─────────────────────────────────────────────

    private void setupPieChart(int vehicleId) {
        if (pieChart == null) return;
        ArrayList<PieEntry> entries = new ArrayList<>();
        HashMap<String, Float> categoryMap = new HashMap<>();

        Cursor cursor = db.getExpensesByVehicle(vehicleId);
        if (cursor != null) {
            while (cursor.moveToNext()) {
                String category = cursor.getString(2);
                float amount    = cursor.getFloat(3);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                    categoryMap.put(category,
                            categoryMap.getOrDefault(category, 0f) + amount);
            }
            cursor.close();
        }

        for (Map.Entry<String, Float> entry : categoryMap.entrySet())
            entries.add(new PieEntry(entry.getValue(), entry.getKey()));

        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setColors(ColorTemplate.MATERIAL_COLORS);
        dataSet.setValueTextColor(Color.BLACK);
        dataSet.setValueTextSize(10f);

        PieData data = new PieData(dataSet);
        data.setValueFormatter(new PercentFormatter(pieChart));

        pieChart.setData(data);
        pieChart.setTouchEnabled(false);
        pieChart.setUsePercentValues(true);
        pieChart.getDescription().setEnabled(false);
        pieChart.getLegend().setEnabled(false);
        pieChart.setHoleRadius(40f);
        pieChart.animateY(1000);
        pieChart.invalidate();
    }

    private void setupBarChart(int vehicleId) {
        if (barChart == null) return;
        ArrayList<BarEntry> entries = new ArrayList<>();
        ArrayList<String> labels    = new ArrayList<>();

        for (int i = 3; i >= 0; i--) {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.MONTH, -i);
            String monthQuery = new SimpleDateFormat("yyyy-MM", Locale.getDefault())
                    .format(cal.getTime());
            String monthName  = new SimpleDateFormat("MMM", Locale.getDefault())
                    .format(cal.getTime());
            float monthlyTotal =
                    (float) db.getMonthlyExpenses(vehicleId, monthQuery);
            entries.add(new BarEntry(3 - i, monthlyTotal));
            labels.add(monthName);
        }

        BarDataSet dataSet = new BarDataSet(entries, "Spending");
        dataSet.setColors(ColorTemplate.LIBERTY_COLORS);

        BarData data = new BarData(dataSet);
        data.setBarWidth(0.6f);
        barChart.setData(data);
        barChart.setTouchEnabled(false);
        barChart.getDescription().setEnabled(false);

        XAxis xAxis = barChart.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);

        barChart.getAxisRight().setEnabled(false);
        barChart.animateY(1000);
        barChart.invalidate();
    }

    // ─────────────────────────────────────────────
    //  NAVIGATION
    // ─────────────────────────────────────────────

    private void setupButtons() {
        findViewById(R.id.btnNavAddExpense).setOnClickListener(v -> {
            startActivity(new Intent(this, AddExpenseActivity.class));
            overridePendingTransition(0, 0);
        });
        findViewById(R.id.btnNavHome).setOnClickListener(v -> {
            loadDashboardData();
            Toast.makeText(this, "Dashboard Updated", Toast.LENGTH_SHORT).show();
        });
        findViewById(R.id.btnNavHistory).setOnClickListener(v -> {
            startActivity(new Intent(this, HistoryActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
            overridePendingTransition(0, 0);
        });
        findViewById(R.id.btnNavReminders).setOnClickListener(v -> {
            startActivity(new Intent(this, ReminderActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
            overridePendingTransition(0, 0);
        });
        findViewById(R.id.btnNavVehicles).setOnClickListener(v -> {
            startActivity(new Intent(this, VehicleManagementActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
            overridePendingTransition(0, 0);
        });
        if (findViewById(R.id.btnSettings) != null) {
            findViewById(R.id.btnSettings).setOnClickListener(v -> {
                startActivity(new Intent(this, SettingsActivity.class));
                overridePendingTransition(0, 0);
            });
        }
    }

    // ─────────────────────────────────────────────
    //  DATE FORMATTING HELPER
    // ─────────────────────────────────────────────
    private String formatUserDate(String rawDate) {
        if (rawDate == null || rawDate.isEmpty()) return "No date";
        try {
            java.text.SimpleDateFormat inFmt  = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault());
            java.text.SimpleDateFormat outFmt = new java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault());
            java.util.Date d = inFmt.parse(rawDate);
            return d != null ? outFmt.format(d) : rawDate;
        } catch (Exception e) {
            return rawDate;
        }
    }
}