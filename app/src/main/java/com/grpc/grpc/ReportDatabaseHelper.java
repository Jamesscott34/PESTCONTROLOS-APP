package com.grpc.grpc;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.*;
import android.widget.Toast;

import java.util.ArrayList;



/**
 * SQLite Database Helper for managing House Reports, Company Reports, Users, and Events.
 */

public class ReportDatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "grpest_reports.db";
    private static final int DATABASE_VERSION = 2;


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

    private Context context;

    public ReportDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        this.context = context;
    }

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
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Drop all tables and recreate

        db.execSQL("DROP TABLE IF EXISTS " + TABLE_COMPANY_REPORTS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_EVENTS);
        onCreate(db);
    }

    /**
     * Clears all data from all tables, used for resetting the database.
     */
    public void clearDatabase(SQLiteDatabase db) {

        db.execSQL("DELETE FROM " + TABLE_COMPANY_REPORTS);
        db.execSQL("DELETE FROM " + TABLE_EVENTS);
    }

    /**
     * Insert a new event into the events table.
     */
    public void insertEvent(String date, String eventName) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_EVENT_DATE, date);
        values.put(COLUMN_EVENT_NAME, eventName);
        db.insert(TABLE_EVENTS, null, values);
        db.close();
    }
    public void updateEventDate(String eventDetails, String newDate) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("event_date", newDate);
        int rowsAffected = db.update("events", values, "event_details = ?", new String[]{eventDetails});
        db.close();

        if (rowsAffected > 0) {
            Toast.makeText(context, "Event moved successfully!", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(context, "Failed to move event. Event not found.", Toast.LENGTH_SHORT).show();
        }
    }


    public void deleteEvent(String eventDetails) {
        SQLiteDatabase db = this.getWritableDatabase();
        int rowsDeleted = db.delete("events", "event_details = ?", new String[]{eventDetails});
        db.close();

        if (rowsDeleted > 0) {
            Toast.makeText(context, "Event deleted successfully!", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(context, "Failed to delete event. Event not found.", Toast.LENGTH_SHORT).show();
        }
    }


    /**
     * Fetch all events for a given date.
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
}
