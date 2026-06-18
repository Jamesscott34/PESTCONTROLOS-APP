package com.grpc.grpc.routes.model;

public class RouteStop {
    public final String documentId;
    public final String name;
    public final String address;
    public final String nextVisit;
    public boolean behind;
    public final String ownerKey;
    public final boolean routeAnchor;
    public final boolean manualJob;
    public double latitude;
    public double longitude;
    public double legDistanceKm = Double.NaN;
    public int estimatedMinutes = -1;
    public int requestedStartMinutes = -1;
    public int requestedEndMinutes = -1;
    public int plannedArrivalMinutes = -1;
    public int plannedStartMinutes = -1;
    public int plannedEndMinutes = -1;

    public RouteStop(String documentId,
                     String name,
                     String address,
                     String nextVisit,
                     boolean behind,
                     String ownerKey,
                     boolean routeAnchor,
                     boolean manualJob,
                     double latitude,
                     double longitude) {
        this.documentId = documentId != null ? documentId : "";
        this.name = name != null ? name : "";
        this.address = address != null ? address : "";
        this.nextVisit = nextVisit != null ? nextVisit : "N/A";
        this.behind = behind;
        this.ownerKey = ownerKey != null ? ownerKey : "";
        this.routeAnchor = routeAnchor;
        this.manualJob = manualJob;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public boolean hasCoordinates() {
        if (Double.isNaN(latitude) || Double.isNaN(longitude)) return false;
        if (Double.isInfinite(latitude) || Double.isInfinite(longitude)) return false;
        if (latitude < -90d || latitude > 90d) return false;
        if (longitude < -180d || longitude > 180d) return false;
        // Many records use (0,0) as an "unset" placeholder; avoid sending it to Directions.
        return !(Math.abs(latitude) < 0.000001d && Math.abs(longitude) < 0.000001d);
    }
}
