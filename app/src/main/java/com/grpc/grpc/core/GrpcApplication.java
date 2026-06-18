package com.grpc.grpc.core;

import com.grpc.grpc.BuildConfig;

import android.app.Application;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory;
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory;

import com.grpc.grpc.login.LoginActivity;
import com.grpc.grpc.location.LocationSharing;
import com.grpc.grpc.workview.data.WorkViewLocalEventStore;
import com.grpc.grpc.workview.data.WorkViewWidgetHelper;

/**
 * Application entry point. App Check debug provider is required because your Firebase project
 * enforces App Check for callables. You must add the debug token from logcat in Firebase Console.
 */
public class GrpcApplication extends Application {

    private static final long SESSION_TIMEOUT_MS = 5L * 60L * 1000L; // 5 minutes

    private int startedCount = 0;
    private long backgroundedAtMs = 0L;

    @Override
    public void onCreate() {
        super.onCreate();
        // Offline flavor: no Firebase – no init, no App Check, no session timeout
        if (!BuildConfig.IS_OFFLINE) {
            FirebaseApp.initializeApp(this);
            // Enable Firestore disk cache so all previously-fetched data is available offline.
            // Writes made offline are queued and synced when connectivity is restored.
            FirebaseFirestore firestoreInstance = FirebaseFirestore.getInstance();
            com.google.firebase.firestore.FirebaseFirestoreSettings firestoreSettings =
                    new com.google.firebase.firestore.FirebaseFirestoreSettings.Builder()
                            .setLocalCacheSettings(
                                    com.google.firebase.firestore.PersistentCacheSettings.newBuilder()
                                            .setSizeBytes(com.google.firebase.firestore.FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                                            .build())
                            .build();
            firestoreInstance.setFirestoreSettings(firestoreSettings);
            boolean isDebuggable = (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
            if (isDebuggable) {
                FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                        DebugAppCheckProviderFactory.getInstance());
                // Debug token is printed by Firebase when you first call a backend. In Logcat search: allow list
                android.util.Log.i("GrpcApplication", "App Check debug provider installed. Trigger a Firestore/backend call, then in Logcat search for \"allow list\" - copy the token and add it in Firebase Console → App Check → Manage debug tokens.");
            } else {
                FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                        PlayIntegrityAppCheckProviderFactory.getInstance());
                android.util.Log.i("GrpcApplication", "App Check Play Integrity provider installed for non-debug build.");
            }
        }

        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity activity, Bundle savedInstanceState) {}
            @Override public void onActivityStarted(Activity activity) {
                startedCount++;
            }
            @Override public void onActivityResumed(Activity activity) {
                // Offline flavor: no session timeout (no login).
                if (BuildConfig.IS_OFFLINE) {
                    backgroundedAtMs = 0L;
                    return;
                }
                if (RememberMeManager.handleExpiryIfNeeded(getApplicationContext())) {
                    if (!(activity instanceof LoginActivity)) {
                        forceLogoutAndReturnToLogin(activity);
                    }
                    backgroundedAtMs = 0L;
                    return;
                }
                if (RememberMeManager.isActive(getApplicationContext())) {
                    backgroundedAtMs = 0L;
                    return;
                }
                // If app was backgrounded for > 5 mins, force logout on next foreground.
                if (backgroundedAtMs > 0L) {
                    long elapsed = System.currentTimeMillis() - backgroundedAtMs;
                    if (elapsed >= SESSION_TIMEOUT_MS) {
                        if (!(activity instanceof LoginActivity)) {
                            forceLogoutAndReturnToLogin(activity);
                        }
                    }
                    backgroundedAtMs = 0L;
                }
            }
            @Override public void onActivityPaused(Activity activity) {}
            @Override public void onActivityStopped(Activity activity) {
                startedCount--;
                if (startedCount <= 0) {
                    startedCount = 0;
                    backgroundedAtMs = System.currentTimeMillis();
                }
            }
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
            @Override public void onActivityDestroyed(Activity activity) {}
        });
    }

    private void forceLogoutAndReturnToLogin(Activity activity) {
        try {
            // Stop background location jobs for the previous user (if any)
            String userName = ActiveUserContext.getActiveUserName(activity);
            if (userName != null && !userName.trim().isEmpty()) {
                LocationSharing.cancelScheduled(activity.getApplicationContext(), userName);
            }

            // Clear app session caches
            SessionManager.clear(activity.getApplicationContext());
            ActiveUserContext.clear(activity.getApplicationContext());
            try { StaffDirectory.clearCache(); } catch (Exception ignored) 