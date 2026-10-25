package com.theminespirit.neoforge;

public record BuildStats(long planStands, long planTrips, int stands, int trips, int deviations) {
    public long planApproaches() {
        return planStands + planTrips;
    }

    public int approaches() {
        return stands + trips;
    }
}
