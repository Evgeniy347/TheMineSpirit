package com.theminespirit.neoforge.board;

import com.theminespirit.core.building.WoodSpecies;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.SettlementPlot;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SettlementWood {
    private static final String LOG_SUFFIX = "_log";
    private static final int SCAN_RADIUS = 24;
    private static final int SCAN_HEIGHT = 8;
    private static final int BIOME_WEIGHT = 1;
    private static final int NEARBY_WEIGHT = 3;
    private static final int STOCK_MIN = 16;
    private static final int WIDE_SCAN_RADIUS = 48;
    private static final int WIDE_SCAN_STEP = 4;
    private static final Logger LOGGER = LoggerFactory.getLogger(SettlementWood.class);
    private static final Map<BlockCoord, Map<String, Integer>> NEARBY = new ConcurrentHashMap<>();
    private static final Map<BlockCoord, String> CHOSEN = new ConcurrentHashMap<>();

    private SettlementWood() {}

    public static String of(ServerLevel level, BlockCoord home, Map<String, Integer> stock) {
        Objects.requireNonNull(home, "Порода без места не определяется: деревья ищутся вокруг дома");
        SettlementPlotStore store = SettlementPlotStore.get(level);
        Optional<SettlementPlot> plot = store.plot();
        boolean settled =
                store.site().map(site -> site.near(home.x(), home.z())).orElse(false);
        if (plot.isPresent() && settled) {
            return plot.orElseThrow().wood();
        }
        return survey(level, home, stock);
    }

    public static String survey(ServerLevel level, BlockCoord home, Map<String, Integer> stock) {
        Objects.requireNonNull(level, "Порода без уровня не определяется: деревья стоят в мире");
        Objects.requireNonNull(home, "Порода без места не определяется: деревья ищутся вокруг дома");
        Objects.requireNonNull(stock, "Запас не может быть null: пустой задают пустой картой");
        Map<String, Integer> nearby = NEARBY.get(home);
        if (nearby == null) {
            nearby = nearby(level, home, SCAN_RADIUS, 2);
            if (!WoodSpecies.hasLogs(nearby)) {
                nearby = nearby(level, home, WIDE_SCAN_RADIUS, WIDE_SCAN_STEP);
            }
            if (WoodSpecies.hasLogs(nearby)) {
                NEARBY.put(home, Map.copyOf(nearby));
            }
        }
        String biome = biomeAt(level, home);
        String species = vote(stock, nearby, biome);
        if (!species.equals(CHOSEN.put(home, species))) {
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
            if (item.endsWith(LOG_SUFFIX)) {
                logs.merge(item, count, Integer::sum);
            }
        });
        return logs;
    }

    public static String biomeAt(ServerLevel level, BlockCoord place) {
        return WoodSpecies.ofBiome(level.getBiome(new BlockPos(place.x(), place.y(), place.z()))
                .unwrapKey()
                .map(key -> key.location().toString())
                .orElse(WoodSpecies.OAK));
    }

    public static Optional<String> biomeRules(Map<String, Integer> stock, String biome) {
        Map<String, Integer> logs = stockLogs(stock);
        return logs.values().stream().anyMatch(count -> count >= STOCK_MIN) ? Optional.empty() : Optional.of(biome);
    }

    private static Map<String, Integer> stockLogs(Map<String, Integer> stock) {
        Map<String, Integer> stockLogs = new HashMap<>();
        stock.forEach((item, count) -> WoodSpecies.speciesOf(item)
                .filter(species -> item.endsWith(LOG_SUFFIX))
                .ifPresent(species -> stockLogs.merge(species + LOG_SUFFIX, count, Integer::sum)));
        return stockLogs;
    }

    static String vote(Map<String, Integer> stock, Map<String, Integer> nearby, String biome) {
        Optional<String> ruled = biomeRules(stock, biome);
        if (ruled.isPresent()) {
            return ruled.get();
        }
        Map<String, Integer> votes = new HashMap<>();
        nearby.forEach((item, count) -> votes.merge(item, count * NEARBY_WEIGHT, Integer::sum));
        Map<String, Integer> stockLogs = stockLogs(stock);
        stockLogs.forEach((item, count) -> {
            if (count >= STOCK_MIN) {
                votes.merge(item, count, Integer::sum);
            }
        });
        votes.merge(biome + LOG_SUFFIX, BIOME_WEIGHT, Integer::sum);
        return WoodSpecies.of(votes);
    }

    static Map<String, Integer> around(ServerLevel level, BlockCoord place) {
        Map<String, Integer> logs = nearby(level, place, SCAN_RADIUS, 2);
        return WoodSpecies.hasLogs(logs) ? logs : nearby(level, place, WIDE_SCAN_RADIUS, WIDE_SCAN_STEP);
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
                    if (id.endsWith(LOG_SUFFIX)) {
                        logs.merge(id, 1, Integer::sum);
                    }
                }
            }
        }
        return logs;
    }
}
