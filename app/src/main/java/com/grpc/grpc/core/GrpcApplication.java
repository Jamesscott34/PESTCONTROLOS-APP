package com.grpc.grpc.core;

import com.grpc.grpc.BuildConfig;

import android.app.Application;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory;

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
            if (BuildConfig.DEBUG) {
                FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                        DebugAppCheckProviderFactory.getInstance());
                // Debug token is printed by Firebase when you first call a backend (e.g. AI Chat). In Logcat search: allow list
                android.util.Log.i("GrpcApplication", "App Check debug provider installed. Open AI Chat and tap Send, then in Logcat search for \"allow list\" - copy the token from that line and add it in Firebase Console → App Check → Manage debug tokens.");
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
            try { StaffDirectory.clearCache(); } catch (Exception ignored) {}
            try { WorkViewLocalEventStore.clearAll(activity.getApplicationContext()); } catch (Exception ignored) {}
            try { WorkViewWidgetHelper.clearWidgetCache(activity.getApplicationContext()); } catch (Exception ignored) {}
            try { LocationSharing.clearLocalCache(activity.getApplicationContext()); } catch (Exception ignored) {}

            // Firebase sign-out (if signed in); no-op for offline
            if (!BuildConfig.IS_OFFLINE) {
                try {
                    FirebaseAuth.getInstance().signOut();
                } catch (Exception ignored) {}
            }

            Intent intent = new Intent(activity, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            intent.putExtra("LOGOUT_REASON", "timeout");
            activity.startActivity(intent);
            activity.finish();
        } catch (Exception ignored) {}
    }
}
