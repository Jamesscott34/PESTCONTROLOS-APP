/**
 * ReportDatabaseHelper.java
 *
 * This class handles the creation, management, and interaction with the SQLite database
 * for storing company reports and event data. It provides methods for inserting, updating,
 * deleting, and querying data from the database.
 *
 * Key Features:
 * - Manages tables for company reports and events.
 * - Handles database creation and version upgrades.
 * - Provides CRUD (Create, Read, Update, Delete) operations for both reports and events.
 */

package com.grpc.grpc;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.*;
import android.widget.Toast;

import java.util.ArrayList;

/**
 * SQLite Database Helper for managing Company Reports and Events in the GRPEST application.
 */
public class ReportDatabaseHelper extends SQLiteOpenHelper {

    // Database Configuration
    private static final String DATABASE_NAME = "grpest_reports.db";
    private static final int DATABASE_VERSION = 2;

    // Company Reports Table and Columns
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

    // Events Table and Columns
    private static final String TABLE_EVENTS = "events";
    private static final String COLUMN_EVENT_ID = "_id";
    private static final String COLUMN_EVENT_DATE = "date";
    private static final String COLUMN_EVENT_NAME = "event_name";

    private final Context context;

    /**
     * Constructor initializes the database helper with the provided context.
     *
     * @param context The Android context where the database will be used.
     */
    public ReportDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        this.context = context;
    }

    /**
     * Creates the database tables if they don't already exist.
     * Called when the database is first created.
     */
    @Override
    public void onCreate(SQLiteDatabase db) {
        // Create the Company Reports Table
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

        // Create the Events Table
        db.execSQL("CREATE TABLE " + TABLE_EVENTS + " (" +
                COLUMN_EVENT_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COLUMN_EVENT_DATE + " TEXT, " +
                COLUMN_EVENT_NAME + " TEXT)");
    }

    /**
     * Upgrades the database by dropping all existing tables and recreating them.
     *
     * @param db The database instance.
     * @param oldVersion The previous database version.
     * @param newVersion The new database version.
     */
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Drop the tables and recreate them for a fresh start
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_COMPANY_REPORTS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_EVENTS);
        onCreate(db);
    }

    /**
     * Clears all data from all tables in the database.
     *
     * @param db The writable database instance.
     */
    public void clearDatabase(SQLiteDatabase db) {
        db.execSQL("DELETE FROM " + TABLE_COMPANY_REPORTS);
        db.execSQL("DELETE FROM " + TABLE_EVENTS);
    }

    // ==================== EVENTS CRUD OPERATIONS ====================

    /**
     * Inserts a new event into the events table.
     *
     * @param date The date of the event.
     * @param eventName The name of the event.
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
     * Updates the date of an existing event.
     *
     * @param eventName The name of the event to be updated.
     * @param newDate The new date for the event.
     */
    public void updateEventDate(String eventName, String newDate) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_EVENT_DATE, newDate);

        int rowsAffected = db.update(TABLE_EVENTS, values, COLUMN_EVENT_NAME + "=?", new String[]{eventName});
        db.close();

        if (rowsAffected > 0) {
            Toast.makeText(context, "Event date updated successfully!", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(context, "Error: Event not found.", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Deletes an event from the events table.
     *
     * @param eventName The name of the event to delete.
     */
    public void deleteEvent(String eventName) {
        SQLiteDatabase db = this.getWritableDatabase();
        int rowsDeleted = db.delete(TABLE_EVENTS, COLUMN_EVENT_NAME + "=?", new String[]{eventName});
        db.close();

        if (rowsDeleted > 0) {
            Toast.makeText(context, "Event deleted successfully!", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(context, "Error: Event not found.", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Retrieves all events for a specified date.
     *
     * @param date The date for which to retrieve events.
     * @return A list of event names occurring on the specified date.
     */
    public ArrayList<String> getEventsByDate(String date) {
        ArrayList<String> events = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.query(
                TABLE_EVENTS,
                new String[]{COLUMN_EVENT_NAME},
                COLUMN_EVENT_DATE + "=?",
                new String[]{date},
                null, null, null
        );

        if (cursor != null && cursor.moveToFirst()) {
            do {
                events.add(cursor.getString(0));
            } while (cursor.moveToNext());
            cursor.close();
        }
        db.close();
        return events;
    }
}
