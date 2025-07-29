package com.grpc.grpc;

import android.util.Log;

/**
 * AppConfig - Centralized configuration management
 * 
 * This class provides access to environment variables and configuration
 * settings that are embedded in the APK during build time.
 * 
 * Usage:
 * - Access Firebase configuration: AppConfig.FIREBASE_PROJECT_ID
 * - Check environment: AppConfig.isProduction()
 * - Get API URLs: AppConfig.getApiBaseUrl()
 */
public class AppConfig {
    
    // ============================================================================
    // FIREBASE CONFIGURATION
    // ============================================================================
    
    /**
     * Firebase Project ID from build configuration
     */
    public static final String FIREBASE_PROJECT_ID = BuildConfig.FIREBASE_PROJECT_ID;
    
    /**
     * Firebase API Key from build configuration
     */
    public static final String FIREBASE_API_KEY = BuildConfig.FIREBASE_API_KEY;
    
    /**
     * Firebase Storage Bucket
     */
    public static final String FIREBASE_STORAGE_BUCKET = "grpc-app-12345.appspot.com";
    
    // ============================================================================
    // API CONFIGURATION
    // ============================================================================
    
    /**
     * Base URL for API endpoints
     */
    public static final String API_BASE_URL = BuildConfig.API_BASE_URL;
    
    /**
     * API version
     */
    public static final String API_VERSION = "v1";
    
    /**
     * Full API URL
     */
    public static String getApiUrl() {
        return API_BASE_URL + "/" + API_VERSION;
    }
    
    // ============================================================================
    // APP CONFIGURATION
    // ============================================================================
    
    /**
     * Current environment (production/development)
     */
    public static final String APP_ENVIRONMENT = BuildConfig.APP_ENVIRONMENT;
    
    /**
     * App version
     */
    public static final String APP_VERSION = "1.0.0";
    
    /**
     * App name
     */
    public static final String APP_NAME = "GRPest Control";
    
    /**
     * Check if running in production environment
     */
    public static boolean isProduction() {
        return "production".equals(APP_ENVIRONMENT);
    }
    
    /**
     * Check if running in development environment
     */
    public static boolean isDevelopment() {
        return "development".equals(APP_ENVIRONMENT);
    }
    

    
    // ============================================================================
    // DATABASE CONFIGURATION
    // ============================================================================
    
    /**
     * Local database name
     */
    public static final String DATABASE_NAME = "UserDatabase";
    
    /**
     * Database version
     */
    public static final int DATABASE_VERSION = 1;
    
    // ============================================================================
    // NOTIFICATION CONFIGURATION
    // ============================================================================
    
    /**
     * Notification channel ID
     */
    public static final String NOTIFICATION_CHANNEL_ID = "grpc_notifications";
    
    /**
     * Notification channel name
     */
    public static final String NOTIFICATION_CHANNEL_NAME = "GRPest Control Notifications";
    
    // ============================================================================
    // FILE STORAGE CONFIGURATION
    // ============================================================================
    
    /**
     * Main storage directory
     */
    public static final String STORAGE_DIRECTORY = "GRPestControl";
    
    /**
     * PDF reports directory
     */
    public static final String PDF_DIRECTORY = "Reports";
    
    /**
     * Contracts directory
     */
    public static final String CONTRACT_DIRECTORY = "Contracts";
    
    // ============================================================================
    // SECURITY CONFIGURATION
    // ============================================================================
    
    /**
     * Password hashing algorithm
     */
    public static final String PASSWORD_HASH_ALGORITHM = "SHA-256";
    
    /**
     * Encryption enabled flag
     */
    public static final boolean ENCRYPTION_ENABLED = true;
    
    // ============================================================================
    // DEVELOPMENT SETTINGS
    // ============================================================================
    
    /**
     * Debug mode flag
     */
    public static final boolean DEBUG_MODE = false;
    
    /**
     * Log level
     */
    public static final String LOG_LEVEL = "INFO";
    
    // ============================================================================
    // UTILITY METHODS
    // ============================================================================
    
    /**
     * Log configuration on app startup
     */
    public static void logConfiguration() {
        Log.d("AppConfig", "=== App Configuration ===");
        Log.d("AppConfig", "Environment: " + APP_ENVIRONMENT);
        Log.d("AppConfig", "Firebase Project: " + FIREBASE_PROJECT_ID);
        Log.d("AppConfig", "API Base URL: " + API_BASE_URL);
        Log.d("AppConfig", "Debug Mode: " + DEBUG_MODE);
        Log.d("AppConfig", "Encryption: " + ENCRYPTION_ENABLED);
        Log.d("AppConfig", "=========================");
    }
    
    /**
     * Get full configuration summary
     */
    public static String getConfigurationSummary() {
        return String.format(
            "Environment: %s\n" +
            "Firebase Project: %s\n" +
            "API Base URL: %s\n" +
            "Debug Mode: %s\n" +
            "Encryption: %s",
            APP_ENVIRONMENT,
            FIREBASE_PROJECT_ID,
            API_BASE_URL,
            DEBUG_MODE,
            ENCRYPTION_ENABLED
        );
    }
} 