package com.grpc.grpc;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class UserDatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = AppConfig.DATABASE_NAME;
    private static final int DATABASE_VERSION = AppConfig.DATABASE_VERSION;

    // Table for storing user credentials
    public static final String TABLE_USERS = "users";
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_EMAIL = "email";
    public static final String COLUMN_PASSWORD_HASH = "password_hash";
    public static final String COLUMN_NAME = "name";
    public static final String COLUMN_LAST_LOGIN = "last_login";
    public static final String COLUMN_IS_ACTIVE = "is_active";

    // Create table SQL
    private static final String CREATE_USERS_TABLE = "CREATE TABLE " + TABLE_USERS + " (" +
            COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
            COLUMN_EMAIL + " TEXT UNIQUE NOT NULL, " +
            COLUMN_PASSWORD_HASH + " TEXT NOT NULL, " +
            COLUMN_NAME + " TEXT NOT NULL, " +
            COLUMN_LAST_LOGIN + " TEXT, " +
            COLUMN_IS_ACTIVE + " INTEGER DEFAULT 1)";

    public UserDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_USERS_TABLE);
        Log.d("UserDatabaseHelper", "Users table created");
        
        // Insert default users for offline functionality
        insertDefaultUsers(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_USERS);
        onCreate(db);
    }

    /**
     * Insert default users for offline functionality
     */
    private void insertDefaultUsers(SQLiteDatabase db) {
        // Default users that can work offline
        String[][] defaultUsers = {
            {"james@grpestcontrol.ie", "Alison12", "James"},
            {"ian@grpestcontrol.ie", "Kristine", "Ian"},
            {"kristine@grpestcontrol.ie", "Winston12", "Kristine"}
            
        };

        for (String[] user : defaultUsers) {
            ContentValues values = new ContentValues();
            values.put(COLUMN_EMAIL, user[0]);
            values.put(COLUMN_PASSWORD_HASH, hashPassword(user[1]));
            values.put(COLUMN_NAME, user[2]);
            values.put(COLUMN_LAST_LOGIN, "");
            values.put(COLUMN_IS_ACTIVE, 1);

            long result = db.insert(TABLE_USERS, null, values);
            if (result != -1) {
                Log.d("UserDatabaseHelper", "Default user inserted: " + user[0]);
            } else {
                Log.e("UserDatabaseHelper", "Failed to insert default user: " + user[0]);
            }
        }
    }

    /**
     * Hash password using SHA-256
     */
    private String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes());
            StringBuilder hexString = new StringBuilder();
            
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            Log.e("UserDatabaseHelper", "Error hashing password: " + e.getMessage());
            return password; // Fallback to plain text (not recommended for production)
        }
    }

    /**
     * Authenticate user with email and password
     */
    public boolean authenticateUser(String email, String password) {
        SQLiteDatabase db = this.getReadableDatabase();
        String hashedPassword = hashPassword(password);
        
        String[] columns = {COLUMN_ID, COLUMN_NAME};
        String selection = COLUMN_EMAIL + " = ? AND " + COLUMN_PASSWORD_HASH + " = ? AND " + COLUMN_IS_ACTIVE + " = 1";
        String[] selectionArgs = {email, hashedPassword};
        
        android.database.Cursor cursor = db.query(TABLE_USERS, columns, selection, selectionArgs, null, null, null);
        
        boolean isValid = cursor.getCount() > 0;
        
        if (isValid) {
            // Update last login time
            ContentValues values = new ContentValues();
            values.put(COLUMN_LAST_LOGIN, System.currentTimeMillis());
            
            String updateSelection = COLUMN_EMAIL + " = ?";
            String[] updateSelectionArgs = {email};
            
            db.update(TABLE_USERS, values, updateSelection, updateSelectionArgs);
        }
        
        cursor.close();
        return isValid;
    }

    /**
     * Get user name by email
     */
    public String getUserName(String email) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] columns = {COLUMN_NAME};
        String selection = COLUMN_EMAIL + " = ?";
        String[] selectionArgs = {email};
        
        android.database.Cursor cursor = db.query(TABLE_USERS, columns, selection, selectionArgs, null, null, null);
        
        String userName = null;
        if (cursor.moveToFirst()) {
            userName = cursor.getString(cursor.getColumnIndex(COLUMN_NAME));
        }
        
        cursor.close();
        return userName;
    }

    /**
     * Check if user exists
     */
    public boolean userExists(String email) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] columns = {COLUMN_ID};
        String selection = COLUMN_EMAIL + " = ? AND " + COLUMN_IS_ACTIVE + " = 1";
        String[] selectionArgs = {email};
        
        android.database.Cursor cursor = db.query(TABLE_USERS, columns, selection, selectionArgs, null, null, null);
        boolean exists = cursor.getCount() > 0;
        cursor.close();
        
        return exists;
    }

    /**
     * Add new user (for admin functionality)
     */
    public boolean addUser(String email, String password, String name) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        
        values.put(COLUMN_EMAIL, email);
        values.put(COLUMN_PASSWORD_HASH, hashPassword(password));
        values.put(COLUMN_NAME, name);
        values.put(COLUMN_LAST_LOGIN, "");
        values.put(COLUMN_IS_ACTIVE, 1);
        
        long result = db.insert(TABLE_USERS, null, values);
        return result != -1;
    }
} 