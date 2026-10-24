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
 * Порода дерева поселения: голосование брёвен запаса, деревьев вокруг дома (одна выборка на место) и биома.
 * Нужна первым шагам (схемы и материалы) и выбору варианта рецепта (доски для палок и сундуков), иначе в джунглях
 * брались дубовые доски, которых не добыть (INC-36, приёмка «джунгли»).
 */
public final class SettlementWood {
    private static final int SCAN_RADIUS = 24;
    private static final int SCAN_HEIGHT = 8;
    // Вес биома в голосовании пород — один голос: решает, когда нет ни брёвен в запасе, ни деревьев рядом. Больший
    // вес перебивал запас брёвен в тесте вдали от дуба (biome тайга, сцена — дубы, выборка через 2 их не видела).
    private static final int BIOME_WEIGHT = 1;
    private static final Map<BlockCoord, Map<String, Integer>> NEARBY = new ConcurrentHashMap<>();

    private SettlementWood() {}

    public static String of(ServerLevel level, BlockCoord home, Map<String, Integer> stock) {
        Objects.requireNonNull(level, "Порода без уровня не определяется: деревья стоят в мире");
        Objects.requireNonNull(home, "Порода без места не определяется: деревья ищутся вокруг дома");
        Objects.requireNonNull(stock, "Запас не может быть null: пустой задают пустой картой");
        Map<String, Integer> nearby = NEARBY.get(home);
        if (nearby == null) {
            nearby = nearby(level, home);
            if (WoodSpecies.hasLogs(nearby)) {
                // Без брёвен рядом выборка не запоминается: деревья могут прогрузиться позже.
                NEARBY.put(home, Map.copyOf(nearby));
            }
        }
        String biome = WoodSpecies.ofBiome(level.getBiome(new BlockPos(home.x(), home.y(), home.z()))
                .unwrapKey()
                .map(key -> key.location().toString())
                .orElse(WoodSpecies.OAK));
        return vote(stock, nearby, biome);
    }

    // Порода — по большинству брёвен в запасе и вокруг дома плюс вес биома (INC-39): раньше любой запас брёвен
    // решал один, и пара дубовых брёвен в джунглях ставила gather:oak_fence.
    static String vote(Map<String, Integer> stock, Map<String, Integer> nearby, String biome) {
        Map<String, Integer> votes = new HashMap<>(nearby);
        stock.forEach((item, count) -> votes.merge(item, count, Integer::sum));
        votes.merge(biome + "_log", BIOME_WEIGHT, Integer::sum);
        return WoodSpecies.of(votes);
    }

    private static Map<String, Integer> nearby(ServerLevel level, BlockCoord home) {
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
        return logs;
    }
}
