package com.theminespirit.neoforge.board;

import com.theminespirit.core.building.WoodSpecies;
import com.theminespirit.core.observation.BlockCoord;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    // Выборка вокруг дома — через клетку по x и z: видна примерно четверть стволов. Вес 3 — деревья вокруг
    // перевешивают средний запас чужой породы (INC-41: дуб из прошлых прогонов в сундуках ставил gather:oak_fence в
    // джунглях), а большой свой запас (64) по-прежнему решает.
    private static final int NEARBY_WEIGHT = 3;
    private static final int STOCK_MIN = 16;
    private static final int WIDE_SCAN_RADIUS = 48;
    private static final int WIDE_SCAN_STEP = 4;
    private static final Logger LOGGER = LoggerFactory.getLogger(SettlementWood.class);
    private static final Map<BlockCoord, Map<String, Integer>> NEARBY = new ConcurrentHashMap<>();
    private static final Map<BlockCoord, String> CHOSEN = new ConcurrentHashMap<>();

    private SettlementWood() {}

    public static String of(ServerLevel level, BlockCoord home, Map<String, Integer> stock) {
        Objects.requireNonNull(level, "Порода без уровня не определяется: деревья стоят в мире");
        Objects.requireNonNull(home, "Порода без места не определяется: деревья ищутся вокруг дома");
        Objects.requireNonNull(stock, "Запас не может быть null: пустой задают пустой картой");
        Map<String, Integer> nearby = NEARBY.get(home);
        if (nearby == null) {
            nearby = nearby(level, home, SCAN_RADIUS, 2);
            if (!WoodSpecies.hasLogs(nearby)) {
                // Деревья у дома вырублены расчисткой участка — выборка дальше, реже (INC-42: «деревья {}»).
                nearby = nearby(level, home, WIDE_SCAN_RADIUS, WIDE_SCAN_STEP);
            }
            if (WoodSpecies.hasLogs(nearby)) {
                // Без брёвен рядом выборка не запоминается: деревья могут прогрузиться позже.
                NEARBY.put(home, Map.copyOf(nearby));
            }
        }
        String biome = WoodSpecies.ofBiome(level.getBiome(new BlockPos(home.x(), home.y(), home.z()))
                .unwrapKey()
                .map(key -> key.location().toString())
                .orElse(WoodSpecies.OAK));
        String species = vote(stock, nearby, biome);
        if (!species.equals(CHOSEN.put(home, species))) {
            // Строка выбора — при смене породы у дома: по ней видно, из чего сложились голоса (INC-41).
            LOGGER.info(
                    "[wood] порода {} у {}: запас {}, деревья {} (вес {}), биом {}",
                    species,
                    home,
                    logsOf(stock),
                    logsOf(nearby),
                    NEARBY_WEIGHT,
                    biome);
        }
        return species;
    }

    private static Map<String, Integer> logsOf(Map<String, Integer> items) {
        Map<String, Integer> logs = new TreeMap<>();
        items.forEach((item, count) -> {
            if (item.endsWith("_log")) {
                logs.merge(item, count, Integer::sum);
            }
        });
        return logs;
    }

    // Порода — по большинству брёвен в запасе и вокруг дома плюс вес биома (INC-39): раньше любой запас брёвен
    // решал один, и пара дубовых брёвен в джунглях ставила gather:oak_fence.
    static String vote(Map<String, Integer> stock, Map<String, Integer> nearby, String biome) {
        Map<String, Integer> votes = new HashMap<>();
        nearby.forEach((item, count) -> votes.merge(item, count * NEARBY_WEIGHT, Integer::sum));
        Map<String, Integer> stockLogs = new HashMap<>();
        stock.forEach((item, count) -> WoodSpecies.speciesOf(item)
                .filter(species -> item.endsWith("_log"))
                .ifPresent(species -> stockLogs.merge(species + "_log", count, Integer::sum)));
        // Малый запас (меньше STOCK_MIN брёвен породы) не голосует: 2 дубовых бревна без деревьев рядом перебивали
        // биом jungle, и ограда шла из дуба (INC-42, живой 24.10).
        stockLogs.forEach((item, count) -> {
            if (count >= STOCK_MIN) {
                votes.merge(item, count, Integer::sum);
            }
        });
        votes.merge(biome + "_log", BIOME_WEIGHT, Integer::sum);
        return WoodSpecies.of(votes);
    }

    private static Map<String, Integer> nearby(ServerLevel level, BlockCoord home, int radius, int step) {
        Map<String, Integer> logs = new HashMap<>();
        for (int dx = -radius; dx <= radius; dx += step) {
            for (int dz = -radius; dz <= radius; dz += step) {
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
