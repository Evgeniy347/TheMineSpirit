package com.theminespirit.neoforge;

/**
 * Итог последней стройки по схеме для аудита: стоянки и заходы плана (BuildPlanner) против исполненных и сколько
 * клеток плана ушло на обычную стройку (со стоянки плана и запасных не поставились).
 */
public record BuildStats(long planStands, long planTrips, int stands, int trips, int deviations) {
    public long planApproaches() {
        return planStands + planTrips;
    }

    public int approaches() {
        return stands + trips;
    }
}
