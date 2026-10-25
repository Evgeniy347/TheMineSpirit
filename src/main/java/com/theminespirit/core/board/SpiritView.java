package com.theminespirit.core.board;

public record SpiritView(boolean inCombat, boolean onExpedition, boolean boardBusy) {

    public static SpiritView free() {
        return new SpiritView(false, false, false);
    }

    public boolean busy() {
        return inCombat || onExpedition || boardBusy;
    }
}
