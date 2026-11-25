package com.theminespirit.core.policy;

public final class PlanFilterGate {
    private long applied = Long.MIN_VALUE;

    public boolean due(long planGeneration) {
        if (applied == planGeneration) {
            return false;
        }
        applied = planGeneration;
        return true;
    }
}
