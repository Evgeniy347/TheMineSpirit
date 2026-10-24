package com.theminespirit.core.policy;

/**
 * Какой проверкой защиты судить блок участка поселения (INC-70…72): поставленное игроком — обычной; своё в клетке
 * постройки плана — как своё (его держит запрет по плану); остальное на участке (остатки старых раскладок, в т.ч.
 * поставленные духами по прежнему якорю) — как остаток: чёрный список заборов и факелов его не держит.
 */
public final class SettlementBreak {
    public enum Check {
        DEFAULT,
        OWN,
        SETTLEMENT
    }

    /** Кто поставил блок: игрок, дух или неизвестно (природа, старые версии). */
    public enum Origin {
        PLAYER,
        SPIRIT,
        UNKNOWN
    }

    /** Где блок: вне участка, на участке, в клетке постройки плана. */
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
