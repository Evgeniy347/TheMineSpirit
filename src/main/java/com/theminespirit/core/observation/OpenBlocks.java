package com.theminespirit.core.observation;

import java.util.Comparator;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Отбор ближайших открытых важных блоков для наблюдения: руды, камень и брёвна.
 * Числа сюда не входят — только правило «что важно», порядок и предел списка.
 */
public final class OpenBlocks {
    public static final Comparator<OpenBlock> ORDER = Comparator.comparingDouble(OpenBlock::distance)
            .thenComparing(OpenBlock::item)
            .thenComparing(OpenBlock::pos);

    private OpenBlocks() {}

    public static boolean isImportant(@Nullable String blockId) {
        if (blockId == null) {
            return false;
        }
        String path = blockId.startsWith("minecraft:") ? blockId.substring("minecraft:".length()) : blockId;
        return "stone".equals(path)
                || "deepslate".equals(path)
                || path.endsWith("_ore")
                || path.endsWith("_log")
                || path.endsWith("_stem")
                || path.endsWith("_wood")
                || path.endsWith("_hyphae");
    }

    public static void keepNearest(List<OpenBlock> target, OpenBlock candidate, int limit) {
        if (limit <= 0) {
            return;
        }
        if (target.size() < limit) {
            target.add(candidate);
        } else {
            OpenBlock farthest = target.get(target.size() - 1);
            if (ORDER.compare(candidate, farthest) >= 0) {
                return;
            }
            target.set(target.size() - 1, candidate);
        }
        target.sort(ORDER);
    }
}
