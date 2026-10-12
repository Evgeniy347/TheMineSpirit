package com.theminespirit.core.policy;

public record BodyFlags(boolean inLava, boolean onFire, boolean inWater, boolean hurtRecently) {
    public static final BodyFlags CALM = new BodyFlags(false, false, false, false);
}
