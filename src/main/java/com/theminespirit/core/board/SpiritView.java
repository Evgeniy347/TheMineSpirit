package com.theminespirit.core.board;

/**
 * Состояние духа для проверки применимости постоянного задания: в бою, в походе,
 * занят ли заданием доски. Мир из ядра не читает: снимок собирает адаптер.
 */
public record SpiritView(boolean inCombat, boolean onExpedition, boolean boardBusy) {

    public static SpiritView free() {
        return new SpiritView(false, false, false);
    }

    public boolean busy() {
        return inCombat || onExpedition || boardBusy;
    }
}
