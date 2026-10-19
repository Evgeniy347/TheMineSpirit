package com.theminespirit.core.tactics.adapter;

public final class ReadBudget {
    private final int limit;
    private int used;

    public ReadBudget(int limit) {
        if (limit < 0) {
            throw new IllegalArgumentException(
                    "ReadBudget.limit = " + limit
                            + ": предел чтений блоков за цикл не может быть отрицательным; возьмите factChunkBudget из настроек");
        }
        this.limit = limit;
    }

    public boolean tryTake() {
        if (used >= limit) {
            return false;
        }
        used++;
        return true;
    }

    public void charge(int reads) {
        if (reads < 0) {
            throw new IllegalArgumentException("ReadBudget.charge = " + reads
                    + ": списать можно только неотрицательное число чтений; проверьте подсчёт вложенного бюджета");
        }
        used = Math.min(limit, used + reads);
    }

    public boolean exhausted() {
        return used >= limit;
    }

    public int used() {
        return used;
    }

    public int remaining() {
        return limit - used;
    }

    public int limit() {
        return limit;
    }
}
