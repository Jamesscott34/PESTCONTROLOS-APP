package com.grpc.grpc;

import android.app.Application;

import com.google.firebase.FirebaseApp;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory;

/**
 * Application entry point. App Check debug provider is required because your Firebase project
 * enforces App Check for callables. You must add the debug token from logcat in Firebase Console.
 */
public class GrpcApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        FirebaseApp.initializeApp(this);

        if (BuildConfig.DEBUG) {
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance());
            // Debug token is printed by Firebase when you first call a backend (e.g. AI Chat). In Logcat search: allow list
            android.util.Log.i("GrpcApplication", "App Check debug provider installed. Open AI Chat and tap Send, then in Logcat search for \"allow list\" - copy the token from that line and add it in Firebase Console → App Check → Manage debug tokens.");
        }
    }
}
