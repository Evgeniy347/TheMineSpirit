package com.theminespirit.core.board;

import java.util.HashMap;
import java.util.Map;

public final class StageAudit {
    private static final int PERCENT = 100;

    private StageAudit() {}

    public static boolean unbuilt(int missing, int total, int rebuildPercent) {
        if (missing < 0 || total < 0 || missing > total) {
            throw new IllegalArgumentException("Аудит постройки: нет " + missing + " из " + total
                    + " — недостача не может быть отрицательной или больше плана, проверьте подсчёт блоков");
        }
        return total > 0 && (long) missing * PERCENT >= (long) rebuildPercent * total;
    }

    public static boolean progressed(int placedBefore, int placedAfter, int total) {
        return placedAfter > placedBefore || placedAfter >= total;
    }

    public static Map<String, Integer> shortage(Map<String, Integer> need) {
        Map<String, Integer> shortage = new HashMap<>();
        need.forEach((item, count) -> {
            if (count != null && count > 0) {
                shortage.put(item, count);
            }
        });
        return Map.copyOf(shortage);
    }
}
