package com.grpc.grpc.routes.util;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.grpc.grpc.routes.model.RouteStop;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public final class GoogleDirectionsClient {
    private static final String DIRECTIONS_URL = "https://maps.googleapis.com/maps/api/directions/json";
    private static final String MAPS_API_KEY_METADATA = "com.google.android.geo.API_KEY";

    private final String apiKey;
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build();

    public GoogleDirectionsClient(Context context) {
        this.apiKey = readApiKey(context);
    }

    public boolean isConfigured() {
        return !TextUtils.isEmpty(apiKey);
    }

    public List<RouteStop> optimizeContractOrder(@Nullable RouteStop origin,
                                                 @Nullable RouteStop destination,
                                                 List<RouteStop> contracts) {
        if (!isConfigured() || contracts == null || contracts.size() < 2 || origin == null || destination == null) {
            return contracts;
        }
        try {
            JSONObject route = fetchRoute(origin, destination, contracts, true);
            if (route == null) return contracts;
            JSONArray waypointOrder = route.optJSONArray("waypoint_order");
            if (waypointOrder == null || waypointOrder.length() != contracts.size()) return contracts;

            List<RouteStop> reordered = new ArrayList<>();
            for (int i = 0; i < waypointOrder.length(); i++) {
                int index = waypointOrder.optInt(i, -1);
                if (index >= 0 && index < contracts.size()) {
                    reordered.add(contracts.get(index));
                }
            }
            return reordered.size() == contracts.size() ? reordered : contracts;
        } catch (Exception ignored) {
            return contracts;
        }
    }

    public boolean populateLegs(List<RouteStop> route) {
        if (!isConfigured() || route == null || route.size() < 2) return false;
        try {
            JSONObject routeJson = fetchRoute(route.get(0), route.get(route.size() - 1), route.subList(1, route.size() - 1), false);
            if (routeJson == null) return false;
            JSONArray legs = routeJson.optJSONArray("legs");
            if (legs == null || legs.length() != route.size() - 1) return false;

            route.get(0).legDistanceKm = 0d;
            route.get(0).estimatedMinutes = 0;
            for (int i = 0; i < legs.length(); i++) {
                JSONObject leg = legs.optJSONObject(i);
                if (leg == null) continue;
                JSONObject distance = leg.optJSONObject("distance");
                JSONObject duration = leg.optJSONObject("duration");
                double km = distance != null ? distance.optDouble("value", Double.NaN) / 1000d : Double.NaN;
                int minutes = duration != null ? (int) Math.ceil(duration.optDouble("value", -1d) / 60d) : -1;
                RouteStop stop = route.get(i + 1);
                stop.legDistanceKm = km;
                stop.estimatedMinutes = minutes;
            }
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public int estimateSegmentMinutes(@Nullable RouteStop origin,
                                      List<RouteStop> contracts,
                                      @Nullable RouteStop destination) {
        if (!isConfigured() || origin == null || destination == null || contracts == null || contracts.isEmpty()) {
            return -1;
        }
        try {
            JSONObject route = fetchRoute(origin, destination, contracts, true);
            if (route == null) return -1;
            JSONArray legs = route.optJSONArray("legs");
            if (legs == null || legs.length() == 0) return -1;
            int total = 0;
            for (int i = 0; i < legs.length(); i++) {
                JSONObject leg = legs.optJSONObject(i);
                if (leg == null) continue;
                JSONObject duration = leg.optJSONObject("duration");
                int minutes = duration != null ? (int) Math.ceil(duration.optDouble("value", -1d) / 60d) : -1;
                if (minutes > 0) total += minutes;
            }
            return total;
        } catch (Exception ignored) {
            return -1;
        }
    }

    public String buildGoogleMapsDirectionsUrl(List<RouteStop> route) {
        if (route == null || route.size() < 2) return "";
        RouteStop origin = route.get(0);
        RouteStop destination = route.get(route.size() - 1);
        if (!origin.hasCoordinates() || !destination.hasCoordinates()) return "";

        HttpUrl.Builder builder = HttpUrl.parse("https://www.google.com/maps/dir/").newBuilder()
                .addQueryParameter("api", "1")
                .addQueryParameter("travelmode", "driving")
                .addQueryParameter("origin", latLng(origin))
                .addQueryParameter("destination", latLng(destination));

        if (route.size() > 2) {
            List<String> waypoints = new ArrayList<>();
            for (int i = 1; i < route.size() - 1; i++) {
                RouteStop stop = route.get(i);
                if (stop != null && stop.hasCoordinates()) {
                    waypoints.add(latLng(stop));
                }
            }
            if (!waypoints.isEmpty()) {
                builder.addQueryParameter("waypoints", TextUtils.join("|", waypoints));
            }
        }
        return builder.build().toString();
    }

    @Nullable
    private JSONObject fetchRoute(RouteStop origin,
                                  RouteStop destination,
                                  List<RouteStop> waypoints,
                                  boolean optimize) throws IOException {
        if (origin == null || destination == null || !origin.hasCoordinates() || !destination.hasCoordinates()) return null;

        HttpUrl.Builder builder = HttpUrl.parse(DIRECTIONS_URL).newBuilder()
                .addQueryParameter("origin", latLng(origin))
                .addQueryParameter("destination", latLng(destination))
                .addQueryParameter("mode", "driving")
                .addQueryParameter("key", apiKey);

        List<String> waypointValues = new ArrayList<>();
        if (waypoints != null) {
            for (RouteStop stop : waypoints) {
                if (stop != null && stop.hasCoordinates()) {
                    waypointValues.add(latLng(stop));
                }
            }
        }
        if (!waypointValues.isEmpty()) {
            String prefix = optimize ? "optimize:true|" : "";
            builder.addQueryParameter("waypoints", prefix + TextUtils.join("|", waypointValues));
        }

        Request request = new Request.Builder().url(builder.build()).get().build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) return null;
            JSONObject payload = new JSONObject(response.body().string());
            if (!"OK".equalsIgnoreCase(payload.optString("status"))) return null;
            JSONArray routes = payload.optJSONArray("routes");
            if (routes == null || routes.length() == 0) return null;
            return routes.optJSONObject(0);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String readApiKey(Context context) {
        if (context == null) return "";
        try {
            ApplicationInfo appInfo = context.getPackageManager().getApplicationInfo(
                    context.getPackageName(),
                    PackageManager.GET_META_DATA
            );
            if (appInfo.metaData == null) return "";
            String key = appInfo.metaData.getString(MAPS_API_KEY_METADATA, "");
            return key != null ? key.trim() : "";
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String latLng(RouteStop stop) {
        return String.format(Locale.US, "%.6f,%.6f", stop.latitude, stop.longitude);
    }
}
