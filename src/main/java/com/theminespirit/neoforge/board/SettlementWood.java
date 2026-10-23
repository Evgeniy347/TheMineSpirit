package com.theminespirit.neoforge.board;

import com.theminespirit.core.building.WoodSpecies;
import com.theminespirit.core.observation.BlockCoord;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;

/**
 * Порода дерева поселения: по запасу брёвен, а пока брёвен нет — по деревьям вокруг дома (одна выборка на место).
 * Нужна первым шагам (схемы и материалы) и выбору варианта рецепта (доски для палок и сундуков), иначе в джунглях
 * брались дубовые доски, которых не добыть (INC-36, приёмка «джунгли»).
 */
public final class SettlementWood {
    private static final int SCAN_RADIUS = 24;
    private static final int SCAN_HEIGHT = 8;
    private static final Map<BlockCoord, String> NEARBY = new ConcurrentHashMap<>();

    private SettlementWood() {}

    public static String of(ServerLevel level, BlockCoord home, Map<String, Integer> stock) {
        Objects.requireNonNull(level, "Порода без уровня не определяется: деревья стоят в мире");
        Objects.requireNonNull(home, "Порода без места не определяется: деревья ищутся вокруг дома");
        if (WoodSpecies.hasLogs(stock)) {
            return WoodSpecies.of(stock);
        }
        return NEARBY.computeIfAbsent(home, at -> nearby(level, at));
    }

    private static String nearby(ServerLevel level, BlockCoord home) {
        Map<String, Integer> logs = new HashMap<>();
        for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx += 2) {
            for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz += 2) {
                for (int dy = 0; dy <= SCAN_HEIGHT; dy++) {
                    BlockPos pos = new BlockPos(home.x() + dx, home.y() + dy, home.z() + dz);
                    String id = BuiltInRegistries.BLOCK
                            .getKey(level.getBlockState(pos).getBlock())
                            .getPath();
                    if (id.endsWith("_log")) {
                        logs.merge(id, 1, Integer::sum);
                    }
                }
            }
        }
        return WoodSpecies.of(logs);
    }
}
