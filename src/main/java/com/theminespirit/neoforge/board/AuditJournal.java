package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.AuditDiff;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BuildGap;
import com.theminespirit.core.observation.BlockCoord;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

final class AuditJournal {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_ENTRIES = 32;
    private static final int SHOWN = 8;
    private static final Map<String, BuildGap.Gap> LAST = new HashMap<>();

    private AuditJournal() {}

    static void watch(ServerLevel level, BoardCase step, String key, BuildGap.Gap now) {
        if (LAST.size() > MAX_ENTRIES) {
            LAST.clear();
        }
        BuildGap.Gap before = LAST.put(key, now);
        if (before == null) {
            return;
        }
        AuditDiff diff = AuditDiff.between(before, now, WorldProbe.of(level)::block);
        if (diff.lost().isEmpty() && diff.gained() == 0 && diff.unknownCells().isEmpty()) {
            return;
        }
        if (!diff.lost().isEmpty()) {
            LOGGER.info(
                    "[audit] {}: перестало считаться {} клеток (было {}/{} стало {}/{}): {}{}",
                    step,
                    diff.lost().size(),
                    before.done(),
                    before.total(),
                    now.done(),
                    now.total(),
                    sample(diff.lost()),
                    tail(diff.lost().size()));
        }
        if (diff.gained() > 0) {
            LOGGER.info(
                    "[audit] {}: вернулось в готовность {} клеток (стало {}/{})",
                    step,
                    diff.gained(),
                    now.done(),
                    now.total());
        }
        if (!diff.unknownCells().isEmpty()) {
            LOGGER.info(
                    "[audit] {}: неизвестно {} клеток — чанк не загружен, готовность не считают ({}): {}{}",
                    step,
                    now.unknown(),
                    now.done() + "/" + now.total(),
                    sampleCells(diff.unknownCells()),
                    tailCells(diff.unknownCells().size()));
        }
    }

    private static String sample(List<AuditDiff.Loss> lost) {
        return lost.stream()
                .limit(SHOWN)
                .map(AuditDiff.Loss::text)
                .reduce((left, right) -> left + "; " + right)
                .orElse("");
    }

    private static String sampleCells(List<BlockCoord> cells) {
        return cells.stream()
                .limit(SHOWN)
                .map(BlockCoord::toString)
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
    }

    private static String tail(int lost) {
        return lost > SHOWN ? " … и ещё " + (lost - SHOWN) : "";
    }

    private static String tailCells(int unknown) {
        return unknown > SHOWN ? " … и ещё " + (unknown - SHOWN) : "";
    }
}
