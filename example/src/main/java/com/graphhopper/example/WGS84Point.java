package com.graphhopper.example;

public class WGS84Point {
    private  Double latitude;
    private  Double longitude;

    public Double getLatitude() {
        return latitude;
    }

    public WGS84Point setLatitude(Double latitude) {
        this.latitude = latitude;
        return this;
    }

    public Double getLongitude() {
        return longitude;
    }

    public WGS84Point setLongitude(Double longitude) {
        this.longitude = longitude;
        return this;
    }
}
