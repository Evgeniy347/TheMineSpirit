package com.theminespirit.neoforge;

import com.theminespirit.core.board.BoardTuning;
import com.theminespirit.core.board.ClearSummary;
import com.theminespirit.core.building.BuildBox;
import com.theminespirit.core.observation.BlockCoord;
import java.util.Map;
import org.slf4j.Logger;

final class ClearReport {
    private static final ClearSummary SUMMARY = new ClearSummary(BoardTuning.logSummaryTicks());

    private ClearReport() {}

    static void clean(Logger logger, SpiritEntity spirit, BuildBox box) {
        SUMMARY.empty(spirit.level().getGameTime()).ifPresent(summary -> {
            if (summary.first()) {
                logger.info(
                        "[clear] {}: ящик {}..{} уже чист: ломать нечего",
                        spirit.getName().getString(),
                        new BlockCoord(box.minX(), box.minY(), box.minZ()),
                        new BlockCoord(box.maxX(), box.maxY(), box.maxZ()));
                return;
            }
            logger.info(
                    "[clear] сводка: очищено {} ящиков, пустых {}, убрано {} блоков",
                    summary.boxes(),
                    summary.empty(),
                    summary.removed());
        });
    }

    record Metrics(
            int removed,
            Map<String, Integer> brokenKinds,
            int approachTicks,
            int breakTicks,
            int collectTicks,
            int approachFailed,
            int approaches,
            int fromPlace,
            int vanished) {}

    static void success(Logger logger, SpiritEntity spirit, BuildBox box, Metrics metrics, String summary) {
        SUMMARY.cleared(metrics.removed(), spirit.level().getGameTime()).ifPresent(report -> {
            if (report.first()) {
                logger.info(
                        "[clear] {}: ящик {}..{} расчищен: убрано {} {}, тиков подход {} лом {} подбор {}, "
                                + "срывов подхода {}, подходов {}, с места {}, исчезло {}; {}",
                        spirit.getName().getString(),
                        new BlockCoord(box.minX(), box.minY(), box.minZ()),
                        new BlockCoord(box.maxX(), box.maxY(), box.maxZ()),
                        metrics.removed(),
                        metrics.brokenKinds(),
                        metrics.approachTicks(),
                        metrics.breakTicks(),
                        metrics.collectTicks(),
                        metrics.approachFailed(),
                        metrics.approaches(),
                        metrics.fromPlace(),
                        metrics.vanished(),
                        summary);
                return;
            }
            logger.info(
                    "[clear] сводка: очищено {} ящиков, пустых {}, убрано {} блоков (последний {}..{}: {})",
                    report.boxes(),
                    report.empty(),
                    report.removed(),
                    new BlockCoord(box.minX(), box.minY(), box.minZ()),
                    new BlockCoord(box.maxX(), box.maxY(), box.maxZ()),
                    summary);
        });
    }
}
