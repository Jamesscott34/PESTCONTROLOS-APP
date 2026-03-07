package com.grpc.grpc.reports.data;

import com.grpc.grpc.core.*;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.widget.Toast;

import java.util.ArrayList;

/**
 * ReportDatabaseHelper.java
 *
 * This class is an SQLite database helper that manages the storage of company reports, events,
 * general quotations, and bird control quotations. It provides methods for inserting, retrieving,
 * and deleting data related to pest control reports and quotations.
 *
 * Features:
 * - Creates and manages tables for company reports, events, general quotations, and bird quotations
 * - Supports inserting and retrieving events based on date and name
 * - Handles database version upgrades by recreating tables when needed
 * - Provides methods for saving and deleting reports and quotes
 * - Ensures data persistence for structured reporting in pest control operations
 *
 * Author: GRPC
 */

public class ReportDatabaseHelper extends SQLiteOpenHelper {

    // Database Configuration
    private static final String DATABASE_NAME = "grpest_reports.db";
    private static final int DATABASE_VERSION = 4; // 4: additive template columns for custom report

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
    /** Optional: set when report was created from a saved template. */
    private static final String COLUMN_TEMPLATE_ID = "template_id";
    /** Optional: body content for template-based reports (Label: value lines). */
    private static final String COLUMN_TEMPLATE_CONTENT = "template_content";

    // Events Table
    private static final String TABLE_EVENTS = "events";
    private static final String COLUMN_EVENT_ID = "_id";
    private static final String COLUMN_EVENT_DATE = "date";
    private static final String COLUMN_EVENT_NAME = "event_name";

    // General Quotes Table
    private static final String TABLE_QUOTES = "quotes";
    private static final String COLUMN_QUOTE_ID = "id";
    private static final String COLUMN_QUOTE_NUMBER = "quote_number";
    private static final String COLUMN_QUOTE_DATE = "date";
    private static final String COLUMN_QUOTE_ADDRESS = "address";
    private static final String COLUMN_QUOTE_DESCRIPTION = "description";
    private static final String COLUMN_QUOTE_TOTAL = "total_amount";
    private static final String COLUMN_QUOTE_EMAIL = "email";
    private static final String COLUMN_QUOTE_MOBILE = "mobile_number";

    // Bird Quotes Table
    private static final String TABLE_BIRD_QUOTES = "BirdQuotes";
    private static final String COLUMN_BIRD_QUOTE_ID = "id";
    private static final String COLUMN_BIRD_QUOTE_NUMBER = "quote_number";
    private static final String COLUMN_BIRD_QUOTE_DATE = "date";
    private static final String COLUMN_BIRD_QUOTE_ADDRESS = "address";
    private static final String COLUMN_BIRD_QUOTE_DESCRIPTION = "description";
    private static final String COLUMN_BIRD_QUOTE_TOTAL = "total_amount";
    private static final String COLUMN_BIRD_QUOTE_EMAIL = "email";
    private static final String COLUMN_BIRD_QUOTE_MOBILE = "mobile_number";

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
        // Company Reports Table (template_id and template_content for template-based reports)
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
                COLUMN_COMPANY_TECH + " TEXT, " +
                COLUMN_TEMPLATE_ID + " TEXT, " +
                COLUMN_TEMPLATE_CONTENT + " TEXT)");

        // Events Table
        db.execSQL("CREATE TABLE " + TABLE_EVENTS + " (" +
                COLUMN_EVENT_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COLUMN_EVENT_DATE + " TEXT, " +
                COLUMN_EVENT_NAME + " TEXT)");

        // General Quotes Table
        db.execSQL("CREATE TABLE " + TABLE_QUOTES + " (" +
                COLUMN_QUOTE_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COLUMN_QUOTE_NUMBER + " TEXT, " +
                COLUMN_QUOTE_DATE + " TEXT, " +
                COLUMN_QUOTE_ADDRESS + " TEXT, " +
                COLUMN_QUOTE_DESCRIPTION + " TEXT, " +
                COLUMN_QUOTE_TOTAL + " REAL, " +
                COLUMN_QUOTE_EMAIL + " TEXT, " +
                COLUMN_QUOTE_MOBILE + " TEXT)");

        // Bird Quotes Table
        db.execSQL("CREATE TABLE " + TABLE_BIRD_QUOTES + " (" +
                COLUMN_BIRD_QUOTE_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COLUMN_BIRD_QUOTE_NUMBER + " TEXT, " +
                COLUMN_BIRD_QUOTE_DATE + " TEXT, " +
                COLUMN_BIRD_QUOTE_ADDRESS + " TEXT, " +
                COLUMN_BIRD_QUOTE_DESCRIPTION + " TEXT, " +
                COLUMN_BIRD_QUOTE_TOTAL + " REAL, " +
                COLUMN_BIRD_QUOTE_EMAIL + " TEXT, " +
                COLUMN_BIRD_QUOTE_MOBILE + " TEXT)");
    }

    /**
     * Upgrades the database. Version 4: add template_id and template_content to CompanyReports
     * without dropping tables (additive, backward compatible).
     */
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 3) {
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_COMPANY_REPORTS);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_EVENTS);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_QUOTES);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_BIRD_QUOTES);
            onCreate(db);
        } else if (oldVersion < 4) {
            try {
                db.execSQL("ALTER TABLE " + TABLE_COMPANY_REPORTS + " ADD COLUMN " + COLUMN_TEMPLATE_ID + " TEXT");
            } catch (Exception ignored) { /* column may already exist */ }
            try {
                db.execSQL("ALTER TABLE " + TABLE_COMPANY_REPORTS + " ADD COLUMN " + COLUMN_TEMPLATE_CONTENT + " TEXT");
            } catch (Exception ignored) { /* column may already exist */ }
        }
    }

    /**
     * Inserts a new event into the database.
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
     * Fetches all events by date.
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

    /**
     * Inserts a new general quote into the database.
     */
    public void insertQuote(String number, String date, String address, String description, double totalAmount, String email, String mobile, boolean b) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_QUOTE_NUMBER, number);
        values.put(COLUMN_QUOTE_DATE, date);
        values.put(COLUMN_QUOTE_ADDRESS, address);
        values.put(COLUMN_QUOTE_DESCRIPTION, description);
        values.put(COLUMN_QUOTE_TOTAL, totalAmount);
        values.put(COLUMN_QUOTE_EMAIL, email);
        values.put(COLUMN_QUOTE_MOBILE, mobile);
        long result = db.insert(TABLE_QUOTES, null, values);
        db.close();

        if (result == -1) {
            Toast.makeText(context, "Error inserting quote.", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(context, "Quote saved successfully!", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Inserts a new bird quote into the database.
     */
    public void insertBirdQuote(String number, String date, String address, String description, double totalAmount, String email, String mobile) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_BIRD_QUOTE_NUMBER, number);
        values.put(COLUMN_BIRD_QUOTE_DATE, date);
        values.put(COLUMN_BIRD_QUOTE_ADDRESS, address);
        values.put(COLUMN_BIRD_QUOTE_DESCRIPTION, description);
        values.put(COLUMN_BIRD_QUOTE_TOTAL, totalAmount);
        values.put(COLUMN_BIRD_QUOTE_EMAIL, email);
        values.put(COLUMN_BIRD_QUOTE_MOBILE, mobile);
        long result = db.insert(TABLE_BIRD_QUOTES, null, values);
        db.close();

        if (result == -1) {
            Toast.makeText(context, "Error inserting quotation.", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(context, "Quotation saved successfully!", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Clears all data from all tables.
     */
    public void clearDatabase() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL("DELETE FROM " + TABLE_COMPANY_REPORTS);
        db.execSQL("DELETE FROM " + TABLE_EVENTS);
        db.execSQL("DELETE FROM " + TABLE_QUOTES);
        db.execSQL("DELETE FROM " + TABLE_BIRD_QUOTES);
        db.close();
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
}





