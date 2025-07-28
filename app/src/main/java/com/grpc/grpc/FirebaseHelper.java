package com.grpc.grpc;

import android.util.Log;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

/**
 * FirebaseHelper - Utility class for Firebase operations
 * 
 * This class provides helper methods for Firebase initialization,
 * authentication, and common Firestore operations.
 */
public class FirebaseHelper {
    
    private static final String TAG = "FirebaseHelper";
    private static FirebaseFirestore db;
    private static FirebaseAuth auth;
    
    /**
     * Initialize Firebase services
     */
    public static void initialize() {
        try {
            db = FirebaseFirestore.getInstance();
            auth = FirebaseAuth.getInstance();
            Log.d(TAG, "Firebase services initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing Firebase: " + e.getMessage());
        }
    }
    
    /**
     * Get Firestore instance
     */
    public static FirebaseFirestore getFirestore() {
        if (db == null) {
            initialize();
        }
        return db;
    }
    
    /**
     * Get Firebase Auth instance
     */
    public static FirebaseAuth getAuth() {
        if (auth == null) {
            initialize();
        }
        return auth;
    }
    
    /**
     * Check if user is authenticated
     */
    public static boolean isAuthenticated() {
        return auth != null && auth.getCurrentUser() != null;
    }
    
    /**
     * Sign in anonymously if not authenticated
     */
    public static void ensureAuthentication() {
        if (!isAuthenticated()) {
            auth.signInAnonymously()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "Anonymous authentication successful");
                    } else {
                        Log.e(TAG, "Anonymous authentication failed: " + task.getException().getMessage());
                    }
                });
        }
    }
    
    /**
     * Test Firestore connection
     */
    public static void testConnection() {
        db.collection("test").document("test").get()
            .addOnSuccessListener(documentSnapshot -> {
                Log.d(TAG, "Firestore connection test successful");
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Firestore connection test failed: " + e.getMessage());
            });
    }
} 