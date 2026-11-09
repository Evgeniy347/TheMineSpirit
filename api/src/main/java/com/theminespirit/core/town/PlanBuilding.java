package com.theminespirit.core.town;

public enum PlanBuilding {
    NONE,
    HOUSE,
    YARD,
    ORCHARD,
    FIELD,
    MINE,
    FENCE,
    STREET;

    public String area() {
        return switch (this) {
            case HOUSE -> "дом";
            case YARD -> "двор";
            case ORCHARD -> "сад";
            case FIELD -> "поле";
            case MINE -> "шахта";
            case FENCE -> "ограда";
            case STREET -> "улица";
            case NONE -> "план поселения";
        };
    }
}
