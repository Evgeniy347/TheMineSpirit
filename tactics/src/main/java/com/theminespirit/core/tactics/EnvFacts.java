package com.theminespirit.core.tactics;

public record EnvFacts(
        double lavaNear,
        double dropHeight,
        int light,
        TimeOfDay timeOfDay,
        double homeDistance,
        boolean homeReachable,
        double coverDistance,
        double waterDistance) {
    public static final double FAR_DISTANCE = 1000.0;
    public static final int MAX_LIGHT = 15;

    public EnvFacts {
        FactChecks.range("EnvFacts.lavaNear", lavaNear, 0, FAR_DISTANCE);
        FactChecks.range("EnvFacts.dropHeight", dropHeight, 0, 400);
        FactChecks.range("EnvFacts.light", light, 0, MAX_LIGHT);
        FactChecks.required("EnvFacts.timeOfDay", timeOfDay);
        FactChecks.range("EnvFacts.homeDistance", homeDistance, 0, FAR_DISTANCE);
        FactChecks.range("EnvFacts.coverDistance", coverDistance, 0, FAR_DISTANCE);
        FactChecks.range("EnvFacts.waterDistance", waterDistance, 0, FAR_DISTANCE);
    }

    public boolean night() {
        return timeOfDay == TimeOfDay.NIGHT;
    }
}
