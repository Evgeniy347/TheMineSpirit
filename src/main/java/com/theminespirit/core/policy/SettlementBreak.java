package com.theminespirit.core.policy;

public final class SettlementBreak {
    public enum Check {
        DEFAULT,
        OWN,
        SETTLEMENT
    }

    public enum Origin {
        PLAYER,
        SPIRIT,
        UNKNOWN
    }

    public enum Place {
        OFF_PLOT,
        PLOT,
        PLAN
    }

    private SettlementBreak() {}

    public static Check of(Origin origin, Place place) {
        if (origin == Origin.PLAYER) {
            return Check.DEFAULT;
        }
        if (place == Place.PLAN && origin == Origin.SPIRIT) {
            return Check.OWN;
        }
        if (place != Place.OFF_PLOT) {
            return Check.SETTLEMENT;
        }
        return origin == Origin.SPIRIT ? Check.OWN : Check.DEFAULT;
    }
}
