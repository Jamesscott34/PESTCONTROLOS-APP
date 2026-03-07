package com.grpc.grpc.location.ui;

import com.grpc.grpc.R;
import com.grpc.grpc.core.*;
import com.grpc.grpc.location.LocationSharing;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.color.MaterialColors;
import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Admin-only: shows each tech's last known location and opens it in Maps.
 * Works offline using a small local SharedPreferences cache.
 */
public class LocationFinderActivity extends AppCompatActivity {
    private java.util.List<StaffDirectory.OwnerOption> techOptions = new java.util.ArrayList<>();

    private String userName;
    private LinearLayout container;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_location_finder);
        if (DemoFirebaseExpiryHelper.finishIfBlocked(this)) return;

        userName = getIntent().getStringExtra("USER_NAME");
        if (TextUtils.isEmpty(userName)) {
            userName = getSharedPreferences("GRPC", MODE_PRIVATE).getString("USER_NAME", "User");
        }

        SessionManager.ensureLoaded(this, null);
        if (!SessionManager.canUseLocationFinder(this)) {
            Toast.makeText(this, "Access denied.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        db = FirebaseFirestore.getInstance();
        container = findViewById(R.id.techniciansContainer);

        Button back = findViewById(R.id.locationFinderBackButton);
        if (back != null) back.setOnClickListener(v -> finish());

        loadTechListAndBuildUi();
    }

    private void loadTechListAndBuildUi() {
        StaffDirectory.fetchOwnerOptions(this, options -> runOnUiThread(() -> {
            techOptions = options != null ? options : new java.util.ArrayList<>();
            buildTechRows();
            attachFirestoreListeners();
        }));
    }

    private void buildTechRows() {
        if (container == null) return;
        container.removeAllViews();

        for (StaffDirectory.OwnerOption opt : techOptions) {
            String tech = opt != null ? opt.ownerKey : "";
            if (TextUtils.isEmpty(tech)) continue;
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(24, 16, 24, 16);
            card.setBackgroundResource(R.drawable.surface_frame);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, 0, 0, 12);
            card.setLayoutParams(params);

            TextView title = new TextView(this);
            title.setText(tech);
            title.setTextSize(18);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            title.setTextColor(resolveColorAttr(android.R.attr.textColorPrimary));
            card.addView(title);

            TextView details = new TextView(this);
            details.setTextSize(14);
            details.setTextColor(resolveColorAttr(android.R.attr.textColorSecondary));
            details.setTag("details_" + tech);
            card.addView(details);

            Button open = new Button(this);
            open.setText("Open on Maps");
            try {
                // Prefer theme-aware colors (Material)
                int bg = MaterialColors.getColor(open, com.google.android.material.R.attr.colorPrimary);
                int fg = MaterialColors.getColor(open, com.google.android.material.R.attr.colorOnPrimary);
                open.setBackgroundTintList(android.content.res.ColorStateList.valueOf(bg));
                open.setTextColor(fg);
            } catch (Exception ignored) {
                // Fallback: keep default styling
            }
            String techKey = (opt != null && opt.staffId != null && opt.staffId.length() >= 15)
                    ? opt.staffId
                    : LocationSharing.userKey(tech);
            open.setOnClickListener(v -> openTechLocation(techKey, tech));
            card.addView(open);

            container.addView(card);

            // Seed from offline cache immediately
            updateDetailsFromCache(techKey, tech);
        }
    }

    private void attachFirestoreListeners() {
        if (db == null || techOptions == null) return;
        for (StaffDirectory.OwnerOption opt : techOptions) {
            String tech = opt != null ? opt.ownerKey : "";
            if (TextUtils.isEmpty(tech)) continue;
            // Use authUid (opt.staffId) for doc id when present; else fallback to legacy key
            final String techKey = (opt != null && opt.staffId != null && opt.staffId.length() >= 15)
                    ? opt.staffId
                    : LocationSharing.userKey(tech);
            if (techKey.isEmpty()) continue;

            db.collection(LocationSharing.COLLECTION_LAST_LOCATIONS)
                    .document(techKey)
                    .addSnapshotListener((snap, err) -> {
                        if (snap == null || !snap.exists()) {
                            // Could be expired; keep cache for offline display
                            updateDetailsFromCache(techKey, tech);
                            return;
                        }
                        try {
                            Double lat = snap.getDouble("lat");
                            Double lng = snap.getDouble("lng");
                            Long ts = snap.getLong("clientTimestampMs");
                            String lastMapQuery = snap.getString("lastMapQuery");
                            Long lastMapTs = snap.getLong("lastMapClientTimestampMs");

                            JSONObject json = new JSONObject();
                            json.put("userKey", techKey);
                            if (lat != null) json.put("lat", lat);
                            if (lng != null) json.put("lng", lng);
                            if (ts != null) json.put("clientTimestampMs", ts);
                            if (!TextUtils.isEmpty(lastMapQuery)) json.put("lastMapQuery", lastMapQuery);
                            if (lastMapTs != null) json.put("lastMapClientTimestampMs", lastMapTs);
                            LocationSharing.cacheLastLocation(this, techKey, json.toString());
                        } catch (Exception ignored) {}

                        updateDetailsFromCache(techKey, tech);
                    });
        }
    }

    private void updateDetailsFromCache(String techKey, String techDisplay) {
        TextView details = findDetailsView(techDisplay);
        if (details == null) return;

        String cached = LocationSharing.getCachedLastLocation(this, techKey);
        if (TextUtils.isEmpty(cached)) {
            details.setText("No cached location.");
            return;
        }

        try {
            JSONObject json = new JSONObject(cached);
            Double lat = json.has("lat") ? json.optDouble("lat") : null;
            Double lng = json.has("lng") ? json.optDouble("lng") : null;
            long ts = json.optLong("clientTimestampMs", 0L);
            String lastMapQuery = json.optString("lastMapQuery", "");
            long mapTs = json.optLong("lastMapClientTimestampMs", 0L);

            StringBuilder sb = new StringBuilder();
            if (!TextUtils.isEmpty(lastMapQuery)) {
                sb.append("Last map: ").append(lastMapQuery).append("\n");
                if (mapTs > 0) sb.append("Map time: ").append(formatTime(mapTs)).append("\n");
            }
            if (lat != null && lng != null) {
                sb.append("GPS: ").append(String.format(Locale.getDefault(), "%.5f, %.5f", lat, lng)).append("\n");
            }
            if (ts > 0) sb.append("Updated: ").append(formatTime(ts));
            if (sb.length() == 0) sb.append("No location fields.");

            details.setText(sb.toString().trim());
        } catch (Exception e) {
            details.setText("Invalid cached location.");
        }
    }

    private TextView findDetailsView(String tech) {
        if (container == null) return null;
        Object tag = "details_" + tech;
        for (int i = 0; i < container.getChildCount(); i++) {
            LinearLayout card = (LinearLayout) container.getChildAt(i);
            for (int j = 0; j < card.getChildCount(); j++) {
                android.view.View v = card.getChildAt(j);
                if (v instanceof TextView && tag.equals(v.getTag())) {
                    return (TextView) v;
                }
            }
        }
        return null;
    }

    private void openTechLocation(String techKey, String techDisplay) {
        String cached = LocationSharing.getCachedLastLocation(this, techKey);
        if (TextUtils.isEmpty(cached)) {
            Toast.makeText(this, "No location saved for " + techDisplay, Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            JSONObject json = new JSONObject(cached);
            String lastMapQuery = json.optString("lastMapQuery", "");
            if (!TextUtils.isEmpty(lastMapQuery)) {
                openMapsQuery(lastMapQuery);
                return;
            }

            if (json.has("lat") && json.has("lng")) {
                double lat = json.optDouble("lat");
                double lng = json.optDouble("lng");
                openMapsLatLng(lat, lng);
                return;
            }
        } catch (Exception ignored) {}

        Toast.makeText(this, "No usable location for " + techDisplay, Toast.LENGTH_SHORT).show();
    }

    private void openMapsQuery(String query) {
        Uri uri = Uri.parse("geo:0,0?q=" + Uri.encode(query));
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        intent.setPackage("com.google.android.apps.maps");
        try {
            startActivity(intent);
        } catch (Exception e) {
            intent.setPackage(null);
            startActivity(intent);
        }
    }

    private void openMapsLatLng(double lat, double lng) {
        Uri uri = Uri.parse("geo:" + lat + "," + lng + "?q=" + lat + "," + lng);
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        intent.setPackage("com.google.android.apps.maps");
        try {
            startActivity(intent);
        } catch (Exception e) {
            intent.setPackage(null);
            startActivity(intent);
        }
    }

    private String formatTime(long ms) {
        try {
            return new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(new Date(ms));
        } catch (Exception e) {
            return "Unknown";
        }
    }

    private int resolveColorAttr(int attr) {
        try {
            TypedValue tv = new TypedValue();
            if (getTheme() != null && getTheme().resolveAttribute(attr, tv, true)) {
                if (tv.resourceId != 0) {
                    return androidx.core.content.ContextCompat.getColor(this, tv.resourceId);
                }
                return tv.data;
            }
        } catch (Exception ignored) {}
        return 0xFFAAAAAA;
    }
}

