package com.cnsc.carcare;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "carcare.db";
    private static final int DB_VERSION = 8; // Bumped from 7 → 8 for pms_done_history table
    // Table names
    public static final String TABLE_VEHICLES  = "vehicles";
    public static final String TABLE_EXPENSES  = "expenses";
    public static final String TABLE_FUEL      = "fuel_logs";
    public static final String TABLE_REMINDERS = "reminders";
    public static final String TABLE_USERS     = "users";
    public static final String TABLE_PMS       = "pms_tasks";
    public static final String TABLE_PMS_HISTORY = "pms_done_history";

    public DatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {

        // Users table
        db.execSQL("CREATE TABLE " + TABLE_USERS + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT," +
                "email TEXT UNIQUE," +
                "password TEXT," +
                "created_at TEXT)");

        // Vehicles table
        db.execSQL("CREATE TABLE " + TABLE_VEHICLES + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "user_id INTEGER," +
                "name TEXT," +
                "type TEXT," +
                "brand TEXT," +
                "model TEXT," +
                "year TEXT," +
                "plate TEXT," +
                "odometer INTEGER," +
                "image_path TEXT," +
                "created_at TEXT)");

        // Expenses table
        db.execSQL("CREATE TABLE " + TABLE_EXPENSES + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "vehicle_id INTEGER," +
                "category TEXT," +
                "amount REAL," +
                "date TEXT," +
                "odometer INTEGER," +
                "notes TEXT," +
                "receipt_image TEXT," +
                "created_at TEXT)");

        // Fuel logs table
        db.execSQL("CREATE TABLE " + TABLE_FUEL + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "vehicle_id INTEGER," +
                "liters REAL," +
                "total_cost REAL," +
                "price_per_liter REAL," +
                "odometer INTEGER," +
                "date TEXT," +
                "efficiency REAL," +
                "created_at TEXT)");

        // Reminders table
        db.execSQL("CREATE TABLE " + TABLE_REMINDERS + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "vehicle_id INTEGER," +
                "type TEXT," +
                "note TEXT," +
                "due_date TEXT," +
                "due_odometer INTEGER," +
                "is_done INTEGER DEFAULT 0," +
                "completed_date TEXT," +
                "created_at TEXT)");

        // PMS Tasks table
        // is_user_confirmed = 1 means the user explicitly checked this task during
        // vehicle registration (PmsConfirmActivity). Tasks seeded but NOT checked
        // stay at 0 — they are NOT shown as "Recently Serviced".
        db.execSQL("CREATE TABLE " + TABLE_PMS + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "vehicle_id INTEGER," +
                "task_name TEXT," +
                "interval_km INTEGER," +
                "interval_months INTEGER," +
                "last_done_km INTEGER," +
                "last_done_date TEXT," +
                "is_user_confirmed INTEGER DEFAULT 0," +
                "created_at TEXT)");

        // PMS Done History — one row per "Mark as Done" event
        db.execSQL("CREATE TABLE " + TABLE_PMS_HISTORY + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "vehicle_id INTEGER," +
                "task_name TEXT," +
                "done_date TEXT," +
                "done_km INTEGER," +
                "created_at TEXT)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 6) {
            // Non-destructive migration: just add the missing column
            try {
                db.execSQL("ALTER TABLE " + TABLE_PMS +
                        " ADD COLUMN is_user_confirmed INTEGER DEFAULT 0");
            } catch (Exception e) {
                // Column may already exist on a partial upgrade — safe to ignore
            }
        }
        if (oldVersion < 7) {
            // Add completed_date to reminders so we can show done-date on calendar
            try {
                db.execSQL("ALTER TABLE " + TABLE_REMINDERS +
                        " ADD COLUMN completed_date TEXT");
            } catch (Exception e) {
                // Safe to ignore if already exists
            }
        }
        if (oldVersion < 8) {
            // Create the pms_done_history table for accurate calendar done-date tracking
            try {
                db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_PMS_HISTORY + " (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "vehicle_id INTEGER," +
                        "task_name TEXT," +
                        "done_date TEXT," +
                        "done_km INTEGER," +
                        "created_at TEXT)");
            } catch (Exception e) {
                // Safe to ignore if already exists
            }
            return;
        }
        // Full wipe for any other version jump
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_USERS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_VEHICLES);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_EXPENSES);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_FUEL);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_REMINDERS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_PMS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_PMS_HISTORY);
        onCreate(db);
    }

    // ==================== USER METHODS ====================

    public boolean registerUser(String name, String email, String password) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("name", name);
        values.put("email", email);
        values.put("password", password);
        values.put("created_at", getCurrentDateTime());
        long result = db.insert(TABLE_USERS, null, values);
        return result != -1;
    }

    public Cursor loginUser(String email, String password) {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery("SELECT * FROM " + TABLE_USERS +
                " WHERE email=? AND password=?", new String[]{email, password});
    }

    public Cursor getUserById(int userId) {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery("SELECT * FROM " + TABLE_USERS + " WHERE id=?",
                new String[]{String.valueOf(userId)});
    }

    // ==================== VEHICLE METHODS ====================

    public long addVehicle(int userId, String name, String type, String brand,
                           String model, String year, String plate,
                           int odometer, String imagePath) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("user_id", userId);
        values.put("name", name);
        values.put("type", type);
        values.put("brand", brand);
        values.put("model", model);
        values.put("year", year);
        values.put("plate", plate);
        values.put("odometer", odometer);
        values.put("image_path", imagePath);
        values.put("created_at", getCurrentDateTime());
        return db.insert(TABLE_VEHICLES, null, values);
    }

    public Cursor getVehiclesByUser(int userId) {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery("SELECT * FROM " + TABLE_VEHICLES + " WHERE user_id=?",
                new String[]{String.valueOf(userId)});
    }

    public Cursor getVehicleById(int vehicleId) {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery("SELECT * FROM " + TABLE_VEHICLES + " WHERE id=?",
                new String[]{String.valueOf(vehicleId)});
    }

    public boolean updateVehicleOdometer(int vehicleId, int newOdometer) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("odometer", newOdometer);
        int rows = db.update(TABLE_VEHICLES, values, "id=?",
                new String[]{String.valueOf(vehicleId)});
        return rows > 0;
    }

    public boolean updateVehicle(int vehicleId, String name, String type, String brand,
                                 String model, String year, String plate, int odometer) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("name", name);
        values.put("type", type);
        values.put("brand", brand);
        values.put("model", model);
        values.put("year", year);
        values.put("plate", plate);
        values.put("odometer", odometer);
        int rows = db.update(TABLE_VEHICLES, values, "id=?",
                new String[]{String.valueOf(vehicleId)});
        return rows > 0;
    }

    public boolean deleteVehicle(int vehicleId) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_EXPENSES,   "vehicle_id=?", new String[]{String.valueOf(vehicleId)});
        db.delete(TABLE_FUEL,       "vehicle_id=?", new String[]{String.valueOf(vehicleId)});
        db.delete(TABLE_REMINDERS,  "vehicle_id=?", new String[]{String.valueOf(vehicleId)});
        db.delete(TABLE_PMS,        "vehicle_id=?", new String[]{String.valueOf(vehicleId)});
        int rows = db.delete(TABLE_VEHICLES, "id=?", new String[]{String.valueOf(vehicleId)});
        return rows > 0;
    }

    // ==================== EXPENSE METHODS ====================

    public long insertExpense(int vehicleId, String category, double amount,
                              String date, int odometer, String notes, String receiptImage) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("vehicle_id", vehicleId);
        values.put("category", category);
        values.put("amount", amount);
        values.put("date", date);
        values.put("odometer", odometer);
        values.put("notes", notes);
        values.put("receipt_image", receiptImage);
        values.put("created_at", getCurrentDateTime());
        return db.insert(TABLE_EXPENSES, null, values);
    }

    public boolean updateExpense(int expenseId, String category, double amount,
                                 String date, int odometer, String notes) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("category", category);
        values.put("amount", amount);
        values.put("date", date);
        values.put("odometer", odometer);
        values.put("notes", notes);
        int rows = db.update(TABLE_EXPENSES, values, "id=?",
                new String[]{String.valueOf(expenseId)});
        return rows > 0;
    }

    public int getVehicleOdometer(int vehicleId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_VEHICLES, new String[]{"odometer"}, "id=?",
                new String[]{String.valueOf(vehicleId)}, null, null, null);
        int currentOdo = 0;
        if (cursor != null && cursor.moveToFirst()) {
            currentOdo = cursor.getInt(0);
            cursor.close();
        }
        return currentOdo;
    }

    public Cursor getAllExpenses() {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery("SELECT * FROM " + TABLE_EXPENSES + " ORDER BY date DESC", null);
    }

    public Cursor getExpensesByVehicle(int vehicleId) {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery("SELECT * FROM " + TABLE_EXPENSES +
                        " WHERE vehicle_id=? ORDER BY date DESC",
                new String[]{String.valueOf(vehicleId)});
    }

    public Cursor getExpensesByVehicleAndDate(int vehicleId, String date) {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery(
                "SELECT * FROM " + TABLE_EXPENSES +
                        " WHERE vehicle_id=? AND date=? ORDER BY id DESC",
                new String[]{String.valueOf(vehicleId), date}
        );
    }

    public Cursor getExpenseById(int expenseId) {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery("SELECT * FROM " + TABLE_EXPENSES + " WHERE id=?",
                new String[]{String.valueOf(expenseId)});
    }

    public double getTotalExpensesByVehicle(int vehicleId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = db.rawQuery("SELECT SUM(amount) FROM " + TABLE_EXPENSES +
                " WHERE vehicle_id=?", new String[]{String.valueOf(vehicleId)});
        double total = 0;
        if (c.moveToFirst()) total = c.getDouble(0);
        c.close();
        return total;
    }

    public double getMonthlyExpenses(int vehicleId, String month) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = db.rawQuery("SELECT SUM(amount) FROM " + TABLE_EXPENSES +
                        " WHERE vehicle_id=? AND date LIKE '%" + month + "%'",
                new String[]{String.valueOf(vehicleId)});
        double total = 0;
        if (c.moveToFirst()) total = c.getDouble(0);
        c.close();
        return total;
    }

    public Cursor getExpensesByCategory(int vehicleId, String category) {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery("SELECT * FROM " + TABLE_EXPENSES +
                        " WHERE vehicle_id=? AND category=? ORDER BY date DESC",
                new String[]{String.valueOf(vehicleId), category});
    }

    public boolean deleteExpense(int expenseId) {
        SQLiteDatabase db = this.getWritableDatabase();
        return db.delete(TABLE_EXPENSES, "id=?",
                new String[]{String.valueOf(expenseId)}) > 0;
    }

    // ==================== FUEL METHODS ====================

    public long addFuelLog(int vehicleId, double liters, double totalCost,
                           double pricePerLiter, int odometer, String date) {
        SQLiteDatabase db = this.getWritableDatabase();

        double efficiency = 0;
        Cursor prev = db.rawQuery("SELECT odometer, liters FROM " + TABLE_FUEL +
                        " WHERE vehicle_id=? ORDER BY odometer DESC LIMIT 1",
                new String[]{String.valueOf(vehicleId)});
        if (prev.moveToFirst()) {
            int prevOdo = prev.getInt(0);
            double prevLiters = prev.getDouble(1);
            if (odometer > prevOdo && prevLiters > 0) {
                efficiency = (odometer - prevOdo) / prevLiters;
            }
        }
        prev.close();

        ContentValues values = new ContentValues();
        values.put("vehicle_id", vehicleId);
        values.put("liters", liters);
        values.put("total_cost", totalCost);
        values.put("price_per_liter", pricePerLiter);
        values.put("odometer", odometer);
        values.put("date", date);
        values.put("efficiency", efficiency);
        values.put("created_at", getCurrentDateTime());
        return db.insert(TABLE_FUEL, null, values);
    }

    public Cursor getFuelLogsByVehicle(int vehicleId) {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery("SELECT * FROM " + TABLE_FUEL +
                        " WHERE vehicle_id=? ORDER BY date DESC",
                new String[]{String.valueOf(vehicleId)});
    }

    public double getAverageFuelEfficiency(int vehicleId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = db.rawQuery("SELECT AVG(efficiency) FROM " + TABLE_FUEL +
                        " WHERE vehicle_id=? AND efficiency > 0",
                new String[]{String.valueOf(vehicleId)});
        double avg = 0;
        if (c.moveToFirst()) avg = c.getDouble(0);
        c.close();
        return avg;
    }

    // ==================== REMINDER METHODS ====================

    public boolean addReminder(int vehicleId, String type, String note, String date, int odo) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("vehicle_id", vehicleId);
        cv.put("type", type);
        cv.put("note", note);
        cv.put("due_date", date);
        cv.put("due_odometer", odo);
        cv.put("is_done", 0);
        cv.put("created_at", getCurrentDateTime());
        long result = db.insert(TABLE_REMINDERS, null, cv);
        return result != -1;
    }

    public Cursor getRemindersByVehicle(int vehicleId) {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery("SELECT * FROM " + TABLE_REMINDERS +
                        " WHERE vehicle_id=? AND is_done=0 ORDER BY due_date ASC",
                new String[]{String.valueOf(vehicleId)});
    }

    public boolean markReminderDone(int reminderId) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("is_done", 1);
        values.put("completed_date", getCurrentDateTime().substring(0, 10)); // yyyy-MM-dd
        return db.update(TABLE_REMINDERS, values, "id=?",
                new String[]{String.valueOf(reminderId)}) > 0;
    }

    public boolean deleteReminder(int reminderId) {
        SQLiteDatabase db = this.getWritableDatabase();
        return db.delete(TABLE_REMINDERS, "id=?",
                new String[]{String.valueOf(reminderId)}) > 0;
    }

    public boolean updateReminder(int reminderId, String type, String note, String date, int odo) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("type", type);
        cv.put("note", note);
        cv.put("due_date", date);
        cv.put("due_odometer", odo);
        return db.update(TABLE_REMINDERS, cv, "id=?",
                new String[]{String.valueOf(reminderId)}) > 0;
    }

    /**
     * Returns a map of date → list of task names for all maintenance tasks
     * that were actually completed (via Mark as Done or confirmed during setup).
     * Primary source: pms_done_history. Fallback: pms_tasks last_done_date for
     * existing users whose history table was empty before this version.
     * Also includes custom reminders with a completed_date.
     */
    public Map<String, List<String>> getMaintenanceDoneDates(int vehicleId) {
        Map<String, List<String>> result = new HashMap<>();

        SQLiteDatabase db = this.getReadableDatabase();

        // Primary: history rows inserted by markPMSTaskDoneConfirmed / confirmPMSTask
        Cursor pms = db.rawQuery(
                "SELECT task_name, done_date FROM " + TABLE_PMS_HISTORY +
                        " WHERE vehicle_id=? AND done_date IS NOT NULL AND done_date != ''",
                new String[]{String.valueOf(vehicleId)});
        if (pms != null) {
            while (pms.moveToNext()) {
                String name = pms.getString(0);
                String date = pms.getString(1);
                if (date != null && !date.isEmpty()) {
                    if (!result.containsKey(date)) result.put(date, new java.util.ArrayList<>());
                    result.get(date).add(name);
                }
            }
            pms.close();
        }

        // Fallback: for existing users who confirmed tasks before this table existed,
        // read last_done_date directly from pms_tasks
        if (result.isEmpty()) {
            Cursor fallback = db.rawQuery(
                    "SELECT task_name, last_done_date FROM " + TABLE_PMS +
                            " WHERE vehicle_id=? AND is_user_confirmed=1" +
                            " AND last_done_date IS NOT NULL AND last_done_date != ''",
                    new String[]{String.valueOf(vehicleId)});
            if (fallback != null) {
                while (fallback.moveToNext()) {
                    String name = fallback.getString(0);
                    String date = fallback.getString(1);
                    if (date != null && !date.isEmpty()) {
                        if (!result.containsKey(date)) result.put(date, new java.util.ArrayList<>());
                        result.get(date).add(name);
                    }
                }
                fallback.close();
            }
        }

        // Custom reminders: is_done=1 with a completed_date set by markReminderDone
        Cursor rem = db.rawQuery(
                "SELECT type, completed_date FROM " + TABLE_REMINDERS +
                        " WHERE vehicle_id=? AND is_done=1 AND completed_date IS NOT NULL AND completed_date != ''",
                new String[]{String.valueOf(vehicleId)});
        if (rem != null) {
            while (rem.moveToNext()) {
                String name = rem.getString(0);
                String date = rem.getString(1);
                if (date != null && !date.isEmpty()) {
                    if (!result.containsKey(date)) result.put(date, new java.util.ArrayList<>());
                    result.get(date).add(name);
                }
            }
            rem.close();
        }

        return result;
    }

    // ==================== PMS METHODS ====================

    /**
     * Seeds standard PMS tasks per Philippine PMS standards,
     * differentiated by vehicle type.
     * All tasks are seeded with is_user_confirmed = 0.
     * PmsConfirmActivity will call confirmPMSTask() for each task the user checks.
     */
    public void seedPMSTasks(int vehicleId, int currentOdometer, String vehicleType) {
        SQLiteDatabase db = this.getWritableDatabase();
        String today = new java.text.SimpleDateFormat("yyyy-MM-dd",
                java.util.Locale.getDefault()).format(new java.util.Date());

        // {task_name, interval_km, interval_months}
        String[][] tasks;

        switch (vehicleType) {
            case "Motorcycle":
                tasks = new String[][] {
                        {"Engine Oil Change",                    "3000",  "3"},
                        {"Air Filter Cleaning/Replacement",      "6000",  "6"},
                        {"Spark Plug Check/Replacement",         "6000",  "6"},
                        {"Chain Lubrication & Tension",          "500",   "1"},
                        {"Brake Inspection",                     "5000",  "6"},
                        {"Tire Pressure Check",                  "1000",  "1"},
                        {"Battery Inspection",                   "10000", "12"},
                        {"Coolant Check (if water-cooled)",      "10000", "12"},
                        {"LTO Registration Renewal",             "0",     "12"},
                };
                break;

            case "Truck":
                tasks = new String[][] {
                        {"Engine Oil & Filter Change",           "5000",  "3"},
                        {"Air Filter Inspection",                "10000", "6"},
                        {"Tire Rotation & Balancing",            "10000", "6"},
                        {"Brake Inspection",                     "10000", "6"},
                        {"Differential Oil Change",              "20000", "12"},
                        {"Transmission Fluid Check",             "20000", "12"},
                        {"Battery Inspection",                   "20000", "12"},
                        {"Power Steering Fluid Check",           "20000", "12"},
                        {"Fuel Filter Replacement",              "30000", "18"},
                        {"Coolant / Radiator Flush",             "40000", "24"},
                        {"Brake Fluid Replacement",              "40000", "24"},
                        {"Timing Belt/Chain Check",              "80000", "48"},
                        {"LTO Registration Renewal",             "0",     "12"},
                };
                break;

            case "Van":
                tasks = new String[][] {
                        {"Engine Oil & Filter Change",           "5000",  "3"},
                        {"Air Filter Inspection",                "10000", "6"},
                        {"Tire Rotation & Balancing",            "10000", "6"},
                        {"Brake Inspection",                     "10000", "6"},
                        {"Battery Inspection",                   "20000", "12"},
                        {"Transmission Fluid Check",             "20000", "12"},
                        {"Power Steering Fluid Check",           "20000", "12"},
                        {"Spark Plugs Replacement",              "20000", "12"},
                        {"Fuel Filter Replacement",              "40000", "24"},
                        {"Coolant / Radiator Flush",             "40000", "24"},
                        {"Brake Fluid Replacement",              "40000", "24"},
                        {"Timing Belt Check",                    "60000", "36"},
                        {"LTO Registration Renewal",             "0",     "12"},
                };
                break;

            case "Car":
            case "SUV / AUV":
            default:
                tasks = new String[][] {
                        {"Oil & Oil Filter Change",              "5000",  "3"},
                        {"Air Filter Inspection",                "10000", "6"},
                        {"Tire Rotation & Balancing",            "10000", "6"},
                        {"Brake Inspection",                     "10000", "6"},
                        {"Battery Inspection",                   "20000", "12"},
                        {"Transmission Fluid Check",             "20000", "12"},
                        {"Spark Plugs Replacement",              "20000", "12"},
                        {"Power Steering Fluid Check",           "20000", "12"},
                        {"Coolant / Radiator Flush",             "40000", "24"},
                        {"Fuel Filter Replacement",              "40000", "24"},
                        {"Brake Fluid Replacement",              "40000", "24"},
                        {"Timing Belt Check",                    "60000", "36"},
                        {"LTO Registration Renewal",             "0",     "12"},
                };
                break;
        }

        for (String[] task : tasks) {
            ContentValues cv = new ContentValues();
            cv.put("vehicle_id",        vehicleId);
            cv.put("task_name",         task[0]);
            cv.put("interval_km",       Integer.parseInt(task[1]));
            cv.put("interval_months",   Integer.parseInt(task[2]));
            cv.put("last_done_km",      currentOdometer);
            cv.put("last_done_date",    today);
            cv.put("is_user_confirmed", 0); // Not confirmed until user explicitly checks it
            cv.put("created_at",        getCurrentDateTime());
            db.insert(TABLE_PMS, null, cv);
        }
    }

    /**
     * Returns ALL PMS tasks for a vehicle (used by PmsConfirmActivity).
     */
    public Cursor getPMSTasksByVehicle(int vehicleId) {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery(
                "SELECT * FROM " + TABLE_PMS +
                        " WHERE vehicle_id=? ORDER BY interval_km ASC, interval_months ASC",
                new String[]{String.valueOf(vehicleId)});
    }

    /**
     * NEW: Returns only tasks the user confirmed during registration OR later
     * marked as done via "Mark as Done" (is_user_confirmed = 1).
     * These are tasks that have a real "Last Done" date/km.
     * Used by ReminderActivity for the "Upcoming" and "Overdue" sections.
     */
    public Cursor getConfirmedPMSTasks(int vehicleId) {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery(
                "SELECT * FROM " + TABLE_PMS +
                        " WHERE vehicle_id=? AND is_user_confirmed=1" +
                        " ORDER BY interval_km ASC, interval_months ASC",
                new String[]{String.valueOf(vehicleId)});
    }

    /**
     * NEW: Returns tasks that were NOT confirmed during registration and have
     * never been marked done — i.e., tasks with no real service history.
     * Used by ReminderActivity for the "Not Yet Started" section.
     * These tasks will NOT show a "Last Done" label.
     */
    public Cursor getNotStartedPMSTasks(int vehicleId) {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery(
                "SELECT * FROM " + TABLE_PMS +
                        " WHERE vehicle_id=? AND is_user_confirmed=0" +
                        " ORDER BY interval_km ASC, interval_months ASC",
                new String[]{String.valueOf(vehicleId)});
    }

    /**
     * Returns only tasks the user explicitly confirmed during registration.
     * Used by ReminderActivity / PMS screen to show "Recently Serviced" section.
     */
    public Cursor getRecentlyServicedTasks(int vehicleId) {
        SQLiteDatabase db = this.getReadableDatabase();
        String today = new java.text.SimpleDateFormat("yyyy-MM-dd",
                java.util.Locale.getDefault()).format(new java.util.Date());
        return db.rawQuery(
                "SELECT * FROM " + TABLE_PMS +
                        " WHERE vehicle_id=? AND is_user_confirmed=1 AND last_done_date=?" +
                        " ORDER BY task_name ASC",
                new String[]{String.valueOf(vehicleId), today});
    }

    /**
     * Returns overdue PMS tasks for a vehicle.
     * A task is overdue when:
     *   current_odometer >= last_done_km + interval_km  (if interval_km > 0)
     *   OR today >= last_done_date + interval_months    (if interval_months > 0)
     */
    public int getOverduePMSCount(int vehicleId) {
        SQLiteDatabase db = this.getReadableDatabase();
        int currentOdo = getVehicleOdometer(vehicleId);
        String today = new java.text.SimpleDateFormat("yyyy-MM-dd",
                java.util.Locale.getDefault()).format(new java.util.Date());

        Cursor c = db.rawQuery(
                "SELECT last_done_km, interval_km, last_done_date, interval_months" +
                        " FROM " + TABLE_PMS + " WHERE vehicle_id=?",
                new String[]{String.valueOf(vehicleId)});

        int overdueCount = 0;
        if (c != null) {
            while (c.moveToNext()) {
                int lastKm        = c.getInt(0);
                int intervalKm    = c.getInt(1);
                String lastDate   = c.getString(2);
                int intervalMonths = c.getInt(3);

                boolean kmOverdue = intervalKm > 0 && currentOdo >= lastKm + intervalKm;
                boolean dateOverdue = false;
                if (intervalMonths > 0 && lastDate != null) {
                    try {
                        java.text.SimpleDateFormat sdf =
                                new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault());
                        java.util.Date last = sdf.parse(lastDate);
                        java.util.Calendar cal = java.util.Calendar.getInstance();
                        cal.setTime(last);
                        cal.add(java.util.Calendar.MONTH, intervalMonths);
                        String dueDate = sdf.format(cal.getTime());
                        dateOverdue = today.compareTo(dueDate) >= 0;
                    } catch (Exception ignored) {}
                }

                if (kmOverdue || dateOverdue) overdueCount++;
            }
            c.close();
        }
        return overdueCount;
    }

    /**
     * Returns count of PMS tasks due within the next 30 days or next 500 km.
     */
    public int getUpcomingPMSCount(int vehicleId) {
        SQLiteDatabase db = this.getReadableDatabase();
        int currentOdo = getVehicleOdometer(vehicleId);
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.add(java.util.Calendar.DAY_OF_YEAR, 30);
        String thirtyDaysLater = new java.text.SimpleDateFormat("yyyy-MM-dd",
                java.util.Locale.getDefault()).format(cal.getTime());
        String today = new java.text.SimpleDateFormat("yyyy-MM-dd",
                java.util.Locale.getDefault()).format(new java.util.Date());

        Cursor c = db.rawQuery(
                "SELECT last_done_km, interval_km, last_done_date, interval_months, is_user_confirmed" +
                        " FROM " + TABLE_PMS + " WHERE vehicle_id=?",
                new String[]{String.valueOf(vehicleId)});

        int upcomingCount = 0;
        if (c != null) {
            while (c.moveToNext()) {
                int    lastKm         = c.getInt(0);
                int    intervalKm     = c.getInt(1);
                String lastDate       = c.getString(2);
                int    intervalMonths = c.getInt(3);
                int    isConfirmed    = c.getInt(4);

                boolean isOverdue  = false;
                boolean isUpcoming = false;

                // ── km-based check ──
                if (intervalKm > 0) {
                    int nextDueKm = lastKm + intervalKm;
                    if (currentOdo >= nextDueKm) {
                        isOverdue = true;
                    } else {
                        // Match the same window used in MainActivity
                        int window = Math.max(1000, intervalKm / 5);
                        if (currentOdo >= nextDueKm - window) {
                            isUpcoming = true;
                        }
                    }
                }

                // ── date-based check (only if km didn't already classify it) ──
                if (!isOverdue && !isUpcoming && intervalMonths > 0 && lastDate != null) {
                    try {
                        java.text.SimpleDateFormat sdf =
                                new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault());
                        java.util.Date last = sdf.parse(lastDate);
                        java.util.Calendar dueCal = java.util.Calendar.getInstance();
                        dueCal.setTime(last);
                        dueCal.add(java.util.Calendar.MONTH, intervalMonths);
                        String dueDate = sdf.format(dueCal.getTime());
                        if (today.compareTo(dueDate) >= 0) {
                            isOverdue = true;
                        } else if (thirtyDaysLater.compareTo(dueDate) >= 0) {
                            isUpcoming = true;
                        }
                    } catch (Exception ignored) {}
                }

                // ── Confirmed fallback — mirrors MainActivity logic ──
                if (!isOverdue && !isUpcoming && isConfirmed == 1) {
                    isUpcoming = true;
                }

                if (isUpcoming) upcomingCount++;
            }
            c.close();
        }
        return upcomingCount;
    }

    /**
     * Marks a PMS task as done — resets the last_done_km and last_done_date
     * so the next due interval is calculated from this point forward.
     */
    public boolean markPMSTaskDone(int taskId, int currentOdometer) {
        SQLiteDatabase db = this.getWritableDatabase();
        String today = new java.text.SimpleDateFormat("yyyy-MM-dd",
                java.util.Locale.getDefault()).format(new java.util.Date());
        ContentValues cv = new ContentValues();
        cv.put("last_done_km",   currentOdometer);
        cv.put("last_done_date", today);
        return db.update(TABLE_PMS, cv, "id=?",
                new String[]{String.valueOf(taskId)}) > 0;
    }

    /**
     * Marks a PMS task as done FROM the maintenance screen (Mark as Done button).
     * Sets is_user_confirmed = 1 so it moves OUT of "Not Yet Started"
     * and into "Upcoming" or "Overdue" with a real Last Done date.
     * Also inserts a history record so the calendar shows this date accurately.
     */
    public boolean markPMSTaskDoneConfirmed(int taskId, int currentOdometer) {
        SQLiteDatabase db = this.getWritableDatabase();
        String today = new java.text.SimpleDateFormat("yyyy-MM-dd",
                java.util.Locale.getDefault()).format(new java.util.Date());
        ContentValues cv = new ContentValues();
        cv.put("last_done_km",      currentOdometer);
        cv.put("last_done_date",    today);
        cv.put("is_user_confirmed", 1);
        boolean updated = db.update(TABLE_PMS, cv, "id=?",
                new String[]{String.valueOf(taskId)}) > 0;

        // Record in history so the calendar shows the correct done-date
        if (updated) {
            // Get task_name for the history record
            Cursor c = db.rawQuery("SELECT task_name, vehicle_id FROM " + TABLE_PMS +
                    " WHERE id=?", new String[]{String.valueOf(taskId)});
            if (c != null && c.moveToFirst()) {
                String taskName = c.getString(0);
                int vehicleId   = c.getInt(1);
                ContentValues hv = new ContentValues();
                hv.put("vehicle_id", vehicleId);
                hv.put("task_name",  taskName);
                hv.put("done_date",  today);
                hv.put("done_km",    currentOdometer);
                hv.put("created_at", getCurrentDateTime());
                db.insert(TABLE_PMS_HISTORY, null, hv);
                c.close();
            }
        }
        return updated;
    }

    /**
     * Updates last done km and date for a PMS task
     * (used by PmsConfirmActivity to set initial service dates).
     */
    public boolean updatePMSLastDone(int taskId, int lastKm, String lastDate) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("last_done_km",   lastKm);
        cv.put("last_done_date", lastDate);
        return db.update(TABLE_PMS, cv, "id=?",
                new String[]{String.valueOf(taskId)}) > 0;
    }

    /**
     * Marks a PMS task as explicitly confirmed by the user during registration.
     * Called from PmsConfirmActivity when the user checks a checkbox.
     * Also updates last_done_km and last_done_date, and records in history
     * so the calendar shows the correct done-date.
     */
    public boolean confirmPMSTask(int taskId, int lastKm, String lastDate) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("is_user_confirmed", 1);
        cv.put("last_done_km",      lastKm);
        cv.put("last_done_date",    lastDate);
        boolean updated = db.update(TABLE_PMS, cv, "id=?",
                new String[]{String.valueOf(taskId)}) > 0;

        // Also record in history so the calendar shows this date
        if (updated && lastDate != null && !lastDate.isEmpty()) {
            Cursor c = db.rawQuery("SELECT task_name, vehicle_id FROM " + TABLE_PMS +
                    " WHERE id=?", new String[]{String.valueOf(taskId)});
            if (c != null && c.moveToFirst()) {
                String taskName = c.getString(0);
                int vehicleId   = c.getInt(1);
                ContentValues hv = new ContentValues();
                hv.put("vehicle_id", vehicleId);
                hv.put("task_name",  taskName);
                hv.put("done_date",  lastDate);
                hv.put("done_km",    lastKm);
                hv.put("created_at", getCurrentDateTime());
                db.insert(TABLE_PMS_HISTORY, null, hv);
                c.close();
            }
        }
        return updated;
    }

    /**
     * Returns all custom maintenance tasks (from reminders table, is_done=0)
     * for a vehicle. Used by MainActivity to show on home screen.
     */
    public Cursor getUpcomingCustomTasks(int vehicleId) {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery("SELECT * FROM " + TABLE_REMINDERS +
                        " WHERE vehicle_id=? AND is_done=0 ORDER BY due_date ASC",
                new String[]{String.valueOf(vehicleId)});
    }

    // ==================== UTILITY ====================

    private String getCurrentDateTime() {
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
                java.util.Locale.getDefault()).format(new java.util.Date());
    }
}