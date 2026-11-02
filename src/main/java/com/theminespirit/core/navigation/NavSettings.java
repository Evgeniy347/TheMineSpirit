// lib: Из Baritone (github.com/cabaletta/baritone, api/Settings.java), лицензия LGPL-3.0.
package com.theminespirit.core.navigation;

public record NavSettings(
        long primaryTimeoutMs,
        long failureTimeoutMs,
        int maxNodes,
        int maxChunkBorderFetch,
        int maxFall,
        int maxWaterFall,
        double jumpPenalty,
        double placeCost,
        double breakPenalty,
        boolean canBreak,
        boolean canPlace) {

    public NavSettings {
        if (primaryTimeoutMs < 0 || failureTimeoutMs < 0) {
            throw new IllegalArgumentException("Сроки поиска (" + primaryTimeoutMs + ", " + failureTimeoutMs
                    + ") отрицательны: отрицательный срок запрещает любой поиск");
        }
        if (maxNodes < 1 || maxChunkBorderFetch < 1) {
            throw new IllegalArgumentException("Пределы поиска: узлов " + maxNodes + ", границ чанков "
                    + maxChunkBorderFetch + ": нужен хотя бы один узел и одна граница");
        }
        if (maxFall < 1 || maxWaterFall < maxFall) {
            throw new IllegalArgumentException("Падение: maxFall = " + maxFall + ", maxWaterFall = " + maxWaterFall
                    + ": спуск хотя бы на 1, в воду — не меньше, чем на землю");
        }
        if (jumpPenalty < 0 || placeCost < 0 || breakPenalty < 0) {
            throw new IllegalArgumentException("Надбавки (прыжок " + jumpPenalty + ", установка " + placeCost
                    + ", ломка " + breakPenalty + ") не бывают отрицательными");
        }
    }

    public static NavSettings defaults() {
        return new NavSettings(500, 2000, 200_000, 50, 3, 12, 2.0, 20.0, 2.0, true, true);
    }
}
