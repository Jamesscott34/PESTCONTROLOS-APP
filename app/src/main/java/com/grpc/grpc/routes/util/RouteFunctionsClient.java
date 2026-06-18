package com.grpc.grpc.routes.util;

import androidx.annotation.Nullable;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.HttpsCallableResult;
import com.grpc.grpc.routes.model.RouteStop;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class RouteFunctionsClient {
    private final FirebaseFunctions functions = FirebaseFunctions.getInstance("europe-west1");

    public List<RouteStop> optimizeContractOrder(@Nullable RouteStop origin,
                                                 @Nullable RouteStop destination,
                                                 List<RouteStop> contracts) throws Exception {
        if (origin == null || destination == null || contracts == null || contracts.size() < 2) {
            return contracts;
        }
        RoutePlanResponse response = requestRoutePlan(origin, destination, contracts, true);
        if (response.waypointOrder.isEmpty()) {
            return contracts;
        }
        List<RouteStop> reordered = new ArrayList<>();
        for (Integer index : response.waypointOrder) {
            if (index != null && index >= 0 && index < contracts.size()) {
                reordered.add(contracts.get(index));
            }
        }
        return reordered.size() == contracts.size() ? reordered : contracts;
    }

    public boolean populateLegs(List<RouteStop> route) throws Exception {
        if (route == null || route.size() < 2) return false;
        List<RouteStop> waypoints = route.size() > 2 ? route.subList(1, route.size() - 1) : new ArrayList<>();
        RoutePlanResponse response = requestRoutePlan(route.get(0), route.get(route.size() - 1), waypoints, false);
        if (response.legs.size() != route.size() - 1) return false;

        route.get(0).legDistanceKm = 0d;
        route.get(0).estimatedMinutes = 0;
        for (int i = 0; i < response.legs.size(); i++) {
            LegData leg = response.legs.get(i);
            RouteStop stop = route.get(i + 1);
            stop.legDistanceKm = leg.distanceKm;
            stop.estimatedMinutes = leg.durationMinutes;
        }
        return true;
    }

    public int estimateSegmentMinutes(@Nullable RouteStop origin,
                                      List<RouteStop> contracts,
                                      @Nullable RouteStop destination) throws Exception {
        if (origin == null || destination == null || contracts == null || contracts.isEmpty()) return -1;
        RoutePlanResponse response = requestRoutePlan(origin, destination, contracts, false);
        return response.totalMinutes;
    }

    private RoutePlanResponse requestRoutePlan(RouteStop origin,
                                               RouteStop destination,
                                               List<RouteStop> waypoints,
                                               boolean optimize) throws Exception {
        if (origin == null || !origin.hasCoordinates()) {
            throw new IllegalArgumentException("Route origin is missing valid coordinates.");
        }
        if (destination == null || !destination.hasCoordinates()) {
            throw new IllegalArgumentException("Route destination is missing valid coordinates.");
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("origin", pointMap(origin));
        payload.put("destination", pointMap(destination));
        payload.put("optimize", optimize);

        List<Map<String, Object>> waypointMaps = new ArrayList<>();
        if (waypoints != null) {
            for (RouteStop stop : waypoints) {
                if (stop != null && stop.hasCoordinates()) {
                    waypointMaps.add(pointMap(stop));
                }
            }
        }
        payload.put("waypoints", waypointMaps);

        HttpsCallableResult result = Tasks.await(functions.getHttpsCallable("getRoutePlan").call(payload));
        Object raw = result != null ? result.getData() : null;
        if (!(raw instanceof Map)) {
            throw new IllegalStateException("Route function returned an invalid response.");
        }
        return RoutePlanResponse.from((Map<?, ?>) raw);
    }

    private static Map<String, Object> pointMap(RouteStop stop) {
        Map<String, Object> point = new HashMap<>();
        point.put("lat", stop.latitude);
        point.put("lng", stop.longitude);
        return point;
    }

    private static final class RoutePlanResponse {
        final List<Integer> waypointOrder = new ArrayList<>();
        final List<LegData> legs = new ArrayList<>();
        int totalMinutes = -1;

        static RoutePlanResponse from(Map<?, ?> data) {
            RoutePlanResponse response = new RoutePlanResponse();

            Object rawOrder = data.get("waypointOrder");
            if (rawOrder instanceof List) {
                for (Object item : (List<?>) rawOrder) {
                    if (item instanceof Number) {
                        response.waypointOrder.add(((Number) item).intValue());
                    }
                }
            }

            Object rawLegs = data.get("legs");
            if (rawLegs instanceof List) {
                for (Object item : (List<?>) rawLegs) {
                    if (!(item instanceof Map)) continue;
                    Map<?, ?> legMap = (Map<?, ?>) item;
                    LegData leg = new LegData();
                    Object distance = legMap.get("distanceKm");
                    Object duration = legMap.get("durationMinutes");
                    leg.distanceKm = distance instanceof Number ? ((Number) distance).doubleValue() : Double.NaN;
                    leg.durationMinutes = duration instanceof Number ? ((Number) duration).intValue() : -1;
                    response.legs.add(leg);
                    if (leg.durationMinutes > 0) {
                        response.totalMinutes = response.totalMinutes < 0 ? leg.durationMinutes : response.totalMinutes + leg.durationMinutes;
                    }
                }
            }

            if (response.totalMinutes < 0) response.totalMinutes = 0;
            return response;
        }
    }

    private static final class LegData {
        double distanceKm = Double.NaN;
        int durationMinutes = -1;
    }
}
