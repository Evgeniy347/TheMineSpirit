package com.theminespirit.core.board;

public final class AuditCadence {
    public static final long NEVER = -1;

    private AuditCadence() {}

    public static boolean due(long last, long now, long step) {
        if (step < 1) {
            throw new IllegalArgumentException(
                    "Шаг обхода " + step + " меньше 1: обход не продвинется, отчёт о повреждении не выйдет");
        }
        return last < 0 || now - last >= step;
    }
}
