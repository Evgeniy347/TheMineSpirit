package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.StallWatch;
import com.theminespirit.core.building.BuildKey;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

final class BuildPitWatch {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int PIT_DROP = 3;
    private static final int EXIT_MARGIN = 6;
    private static final int SAMPLE = 3;

    private final SpiritEntity spirit;
    private final Level level;
    private final SchematicSite site;
    private final BuildKey buildKey;
    private final StallWatch watch = new StallWatch();
    private @Nullable BlockCoord lastStand;

    BuildPitWatch(SpiritEntity spirit, Level level, SchematicSite site, BuildKey buildKey) {
        this.spirit = spirit;
        this.level = level;
        this.site = site;
        this.buildKey = buildKey;
    }

    boolean stalled(long ticks, int placed, int cleared, int trips) {
        long now = placed * 1_000_003L + cleared * 1_001L + trips;
        return watch.stalled(ticks, now);
    }

    void stalledReport(BlockCoord feet, int placed, int cleared, int trips, Map<BlockCoord, String> blocked) {
        List<String> first = new ArrayList<>(SAMPLE);
        for (Schematic.PlacedEntry entry : site.missing()) {
            if (first.size() == SAMPLE) {
                break;
            }
            first.add(entry.block() + "@" + entry.pos() + " — " + why(entry, blocked));
        }
        LOGGER.warn(
                "Стройка {}: {} тиков без прогресса (поставлено {}, очищено {}, заходов {}), дух {}; первые "
                        + "недостижимые: {}",
                buildKey,
                StallWatch.REPORT_TICKS,
                placed,
                cleared,
                trips,
                feet,
                first);
    }

    void descent(BlockCoord feet, BlockCoord stand) {
        if (feet.y() - stand.y() < PIT_DROP || stand.equals(lastStand)) {
            return;
        }
        lastStand = stand;
        BlockPos underFeet = SchematicSite.toPos(new BlockCoord(feet.x(), feet.y() - 1, feet.z()));
        BlockPos underStand = SchematicSite.toPos(new BlockCoord(stand.x(), stand.y() - 1, stand.z()));
        Optional<Set<BlockCoord>> exit = site.walkable(stand, EXIT_MARGIN);
        LOGGER.info(
                "Стройка {}: дух спускается в яму с y={} на стоянку {} (y={}), низ схемы y={}, опора под духом: "
                        + "{}, опора под стоянкой: {}, {}",
                buildKey,
                feet.y(),
                stand,
                stand.y(),
                site.origin().y(),
                level.getBlockState(underFeet).getBlock().getDescriptionId(),
                level.getBlockState(underStand).getBlock().getDescriptionId(),
                exit.isPresent()
                        ? "выход по земле: клеток " + exit.get().size()
                        : "выхода по земле нет — копать или ставить");
    }

    void hardApproach(
            @Nullable BlockCoord stand, @Nullable String refusal, String networkNote, int builds, String outcome) {
        if (stand == null) {
            return;
        }
        BlockPos below = SchematicSite.toPos(new BlockCoord(stand.x(), stand.y() - 1, stand.z()));
        LOGGER.info(
                "Стройка {}: подход {} к стоянке {} (пол {}), построений пути {}, дух {} в {} от стоянки; {}",
                buildKey,
                outcome,
                stand,
                level.getBlockState(below).getBlock().getDescriptionId(),
                builds,
                spirit.position(),
                String.format(
                        Locale.ROOT,
                        "%.2f",
                        spirit.position().distanceTo(new Vec3(stand.x() + 0.5, stand.y(), stand.z() + 0.5))),
                (refusal == null ? "без отказа" : refusal) + "; " + networkNote);
    }

    private String why(Schematic.PlacedEntry entry, Map<BlockCoord, String> blocked) {
        String reason = blocked.get(entry.pos());
        if (reason != null) {
            return reason;
        }
        return "не отложена: свободна " + site.freeForPlacing(entry) + ", опора " + site.supported(entry)
                + ", материал " + BlockItems.countIn(spirit.getInventory(), entry.block());
    }
}
