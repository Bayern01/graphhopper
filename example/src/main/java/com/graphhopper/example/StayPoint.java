package com.graphhopper.example;

import com.graphhopper.util.shapes.GHPoint;

public class StayPoint {
    private GHPoint point;
    private int radius;
    private long starttime;
    private int staytime;

    public StayPoint(GHPoint p, int r, long t, int st) {
        this.point = p;
        this.radius = r;
        this.starttime = t;
        this.staytime = st;
    }

    public GHPoint getPoint() {
        return point;
    }

    public int getRadius() {
        return radius;
    }

    public long getStarttime() { return starttime;}

    public int getStaytime() { return staytime;}
}
