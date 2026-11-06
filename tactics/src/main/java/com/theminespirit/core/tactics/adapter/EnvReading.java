package com.theminespirit.core.tactics.adapter;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.tactics.EnvFacts;
import java.util.Optional;

public record EnvReading(
        double lavaDistance,
        double dropHeight,
        Optional<BlockCoord> water,
        double waterDistance,
        Optional<BlockCoord> cover,
        double coverDistance,
        int reads,
        boolean cutByBudget) {
    public static final EnvReading NOTHING = new EnvReading(
            EnvFacts.FAR_DISTANCE,
            0,
            Optional.empty(),
            EnvFacts.FAR_DISTANCE,
            Optional.empty(),
            EnvFacts.FAR_DISTANCE,
            0,
            false);

    public EnvReading {
        if (water == null || cover == null) {
            throw new IllegalArgumentException("EnvReading.water/cover = null: отсутствие цели записывается "
                    + "Optional.empty(), иначе адаптер не отличит «нет воды» от ошибки сборки");
        }
        if (reads < 0) {
            throw new IllegalArgumentException("EnvReading.reads = " + reads + ": число чтений не может быть "
                    + "отрицательным; проверьте подсчёт в EnvScanner");
        }
    }
}
