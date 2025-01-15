package com.grpc.grpc;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.*;
import android.widget.Toast;

import java.util.ArrayList;

/**
 * SQLite Database Helper for managing Company Reports, Events, and Quotations.
 */
public class ReportDatabaseHelper extends SQLiteOpenHelper {

    // Database Configuration
    private static final String DATABASE_NAME = "grpest_reports.db";
    private static final int DATABASE_VERSION = 3;  // Incremented for quotes support

    // CompanyReports Table
    private static final String TABLE_COMPANY_REPORTS = "CompanyReports";
    private static final String COLUMN_COMPANY_ID = "id";
    private static final String COLUMN_COMPANY_NAME = "name";
    private static final String COLUMN_COMPANY_ADDRESS = "address";
    private static final String COLUMN_COMPANY_DATE = "date";
    private static final String COLUMN_COMPANY_VISIT_TYPE = "visit_type";
    private static final String COLUMN_COMPANY_SITE_INSPECTION = "site_inspection";
    private static final String COLUMN_COMPANY_RECOMMENDATIONS = "recommendations";
    private static final String COLUMN_COMPANY_FOLLOW_UP = "follow_up";
    private static final String COLUMN_COMPANY_PREP = "prep";
    private static final String COLUMN_COMPANY_TECH = "tech";

    // Events Table
    private static final String TABLE_EVENTS = "events";
    private static final String COLUMN_EVENT_ID = "_id";
    private static final String COLUMN_EVENT_DATE = "date";
    private static final String COLUMN_EVENT_NAME = "event_name";

    // Quotes Table with corrected columns
    private static final String TABLE_QUOTES = "quotes";
    private static final String COLUMN_QUOTE_ID = "id";
    private static final String COLUMN_QUOTE_NUMBER = "quote_number";
    private static final String COLUMN_QUOTE_DATE = "date";
    private static final String COLUMN_QUOTE_ADDRESS = "address";
    private static final String COLUMN_QUOTE_DESCRIPTION = "description";
    private static final String COLUMN_QUOTE_TOTAL = "total_amount";
    private static final String COLUMN_QUOTE_EMAIL = "email";
    private static final String COLUMN_QUOTE_MOBILE = "mobile_number";


    private final Context context;

    /**
     * Constructor for initializing the database helper.
     */
    public ReportDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        this.context = context;
    }

    /**
     * Creates the database tables when first initialized.
     */
    @Override
    public void onCreate(SQLiteDatabase db) {

        // Company Reports Table
        db.execSQL("CREATE TABLE " + TABLE_COMPANY_REPORTS + " (" +
                COLUMN_COMPANY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COLUMN_COMPANY_NAME + " TEXT, " +
                COLUMN_COMPANY_ADDRESS + " TEXT, " +
                COLUMN_COMPANY_DATE + " TEXT, " +
                COLUMN_COMPANY_VISIT_TYPE + " TEXT, " +
                COLUMN_COMPANY_SITE_INSPECTION + " TEXT, " +
                COLUMN_COMPANY_RECOMMENDATIONS + " TEXT, " +
                COLUMN_COMPANY_FOLLOW_UP + " TEXT, " +
                COLUMN_COMPANY_PREP + " TEXT, " +
                COLUMN_COMPANY_TECH + " TEXT)");

        // Events Table
        db.execSQL("CREATE TABLE " + TABLE_EVENTS + " (" +
                COLUMN_EVENT_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COLUMN_EVENT_DATE + " TEXT, " +
                COLUMN_EVENT_NAME + " TEXT)");

        // Create Quotes Table
        db.execSQL("CREATE TABLE " + TABLE_QUOTES + " (" +
                COLUMN_QUOTE_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COLUMN_QUOTE_NUMBER + " TEXT, " +
                COLUMN_QUOTE_DATE + " TEXT, " +
                COLUMN_QUOTE_ADDRESS + " TEXT, " +
                COLUMN_QUOTE_DESCRIPTION + " TEXT, " +
                COLUMN_QUOTE_TOTAL + " REAL, " +
                COLUMN_QUOTE_EMAIL + " TEXT, " +
                COLUMN_QUOTE_MOBILE + " TEXT)");
    }

    /**
     * Upgrades the database by dropping and recreating tables.
     */
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_COMPANY_REPORTS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_EVENTS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_QUOTES);
        onCreate(db);
    }

    /**
     * Clears all data from all tables (use with caution).
     */
    public void clearDatabase(SQLiteDatabase db) {
        db.execSQL("DELETE FROM " + TABLE_COMPANY_REPORTS);
        db.execSQL("DELETE FROM " + TABLE_EVENTS);
        db.execSQL("DELETE FROM " + TABLE_QUOTES);
    }

    // ==================== EVENTS CRUD OPERATIONS ====================

    /**
     * Insert a new event into the database.
     */
    public void insertEvent(String date, String eventName) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_EVENT_DATE, date);
        values.put(COLUMN_EVENT_NAME, eventName);
        db.insert(TABLE_EVENTS, null, values);
        db.close();
    }

    /**
     * Fetch all events by date.
     */
    public ArrayList<String> getEventsByDate(String date) {
        ArrayList<String> events = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_EVENTS, new String[]{COLUMN_EVENT_NAME},
                COLUMN_EVENT_DATE + "=?", new String[]{date}, null, null, null);

        if (cursor != null && cursor.moveToFirst()) {
            do {
                events.add(cursor.getString(0));
            } while (cursor.moveToNext());
            cursor.close();
        }
        db.close();
        return events;
    }

    // ==================== QUOTES CRUD OPERATIONS ====================

    /**
     * Insert a new quote into the database.
     */
    public void insertQuote(String date, String address,
                            String quoteDescription, String description, double totalAmount,
                            String userEmail, String mobileNumber) {

        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("date", date);
        values.put("address", address);
        values.put("description", quoteDescription);
        values.put("total_amount", totalAmount);
        values.put("email", userEmail);
        values.put("mobile_number", mobileNumber);

        // ✅ Declaring the result variable properly
        long result = db.insert("quotes", null, values);  // Capture the result of the insert operation
        db.close();  // Close the database connection

        // ✅ Check the result of the insertion
        if (result == -1) {
            Toast.makeText(context, "Error inserting quote.", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(context, "Quote saved successfully!", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Fetch all quotes from the database.
     */
    public ArrayList<String> getAllQuotes() {
        ArrayList<String> quotes = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_QUOTES, new String[]{COLUMN_QUOTE_NUMBER, COLUMN_QUOTE_DATE},
                null, null, null, null, COLUMN_QUOTE_DATE + " DESC");

        if (cursor != null && cursor.moveToFirst()) {
            do {
                String quoteInfo = "Quote #" + cursor.getString(0) + " - Date: " + cursor.getString(1);
                quotes.add(quoteInfo);
            } while (cursor.moveToNext());
            cursor.close();
        }
        db.close();
        return quotes;
    }

    /**
     * Delete a quote by its quote number.
     */
    public void deleteQuote(String quoteNumber) {
        SQLiteDatabase db = this.getWritableDatabase();
        int rowsDeleted = db.delete(TABLE_QUOTES, COLUMN_QUOTE_NUMBER + "=?", new String[]{quoteNumber});
        db.close();

        if (rowsDeleted > 0) {
            Toast.makeText(context, "Quote deleted successfully!", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(context, "Failed to delete quote. Quote not found.", Toast.LENGTH_SHORT).show();
        }
    }
    /**
     * Fetch all dates for a specific event name.
     */
    public ArrayList<String> getDatesByName(String eventName) {
        ArrayList<String> dates = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(
                "events", // Table name
                new String[]{"date"}, // Column to retrieve
                "event_name LIKE ?", // WHERE clause
                new String[]{"%" + eventName + "%"}, // Filter value
                null, null, "date ASC"
        );

        if (cursor != null && cursor.moveToFirst()) {
            do {
                dates.add(cursor.getString(0)); // Adding date to the list
            } while (cursor.moveToNext());
            cursor.close();
        }
        db.close();
        return dates;
    }

    /**
     * Delete all events related to a specific event name.
     */
    public void deleteEventsByName(String eventName) {
        SQLiteDatabase db = this.getWritableDatabase();
        int deletedRows = db.delete(
                "events", // Table name
                "event_name LIKE ?", // WHERE clause
                new String[]{"%" + eventName + "%"} // Filter value
        );
        db.close();

        if (deletedRows > 0) {
            Toast.makeText(context, "Deleted " + deletedRows + " events for " + eventName, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(context, "No events found for " + eventName, Toast.LENGTH_SHORT).show();
        }
    }

    public void GetDatesByName(String eventName) {
    }
}
