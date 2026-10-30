package com.theminespirit.neoforge;

import com.theminespirit.core.building.BuildBox;
import com.theminespirit.core.observation.BlockCoord;
import java.util.Map;
import org.slf4j.Logger;

final class ClearReport {
    private ClearReport() {}

    static void progress(
            Logger logger, SpiritEntity spirit, BuildBox box, int removed, int remaining, BlockCoord stand) {
        logger.info(
                "[clear] {}: ящик {}..{}, срезано {}, осталось {}, стоянка {}",
                spirit.getName().getString(),
                new BlockCoord(box.minX(), box.minY(), box.minZ()),
                new BlockCoord(box.maxX(), box.maxY(), box.maxZ()),
                removed,
                remaining,
                stand);
    }

    static void clean(Logger logger, SpiritEntity spirit, BuildBox box) {
        logger.info(
                "[clear] {}: ящик {}..{} уже чист: ломать нечего",
                spirit.getName().getString(),
                new BlockCoord(box.minX(), box.minY(), box.minZ()),
                new BlockCoord(box.maxX(), box.maxY(), box.maxZ()));
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
        logger.info(
                "[clear] {}: ящик {}..{} расчищен: убрано {} {}, тиков подход {} лом {} подбор {}, срывов подхода {}, подходов {}, с места {}, исчезло {}; {}",
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
    }
}
