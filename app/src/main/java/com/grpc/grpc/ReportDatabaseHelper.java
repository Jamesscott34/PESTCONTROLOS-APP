package com.grpc.grpc;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * SQLite Database Helper for managing House Reports, Company Reports, Users, and Emails.
 */
public class ReportDatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "grpest_reports.db";
    private static final int DATABASE_VERSION = 1;

    public ReportDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // Create House Reports Table
        db.execSQL("CREATE TABLE HouseReports (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "name TEXT, " +
                "address TEXT, " +
                "date TEXT, " +
                "site_inspection TEXT, " +
                "recommendations TEXT, " +
                "follow_up TEXT," +
                "prep TEXT," +
                "tech TEXT)");

        // Create Company Reports Table
        db.execSQL("CREATE TABLE CompanyReports (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "name TEXT, " +
                "address TEXT, " +
                "date TEXT, " +
                "visit_type TEXT, " +
                "site_inspection TEXT, " +
                "recommendations TEXT, " +
                "follow_up TEXT," +
                "prep TEXT," +
                "tech TEXT)");



    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Drop all tables and recreate if upgrading database version
        db.execSQL("DROP TABLE IF EXISTS HouseReports");
        db.execSQL("DROP TABLE IF EXISTS CompanyReports");
        onCreate(db);
    }

    /**
     * Clears all data from all tables, used for resetting the database.
     */
    public void clearDatabase(SQLiteDatabase db) {
        db.execSQL("DELETE FROM HouseReports");
        db.execSQL("DELETE FROM CompanyReports");
    }
}
