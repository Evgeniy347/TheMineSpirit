package com.theminespirit.core.town;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.policy.StrayRules;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import org.jspecify.annotations.Nullable;

public final class SettlementScan {
    public static final int PIT_DEPTH_MAX = 64;
    private static final int STRAY_ABOVE = 32;
    private static final int TRACE_DEPTH = 16;
    private static final int TRACE_SHALLOW = 4;

    public interface World {
        boolean loaded(int x, int z);

        boolean open(int x, int y, int z);

        String blockId(int x, int y, int z);

        int top(int x, int z);

        boolean planned(int x, int y, int z);

        OptionalInt lotLevel(int x, int z);

        OptionalInt dugTop(int x, int z);

        boolean playerPlaced(int x, int y, int z);

        boolean spiritMarked(int x, int y, int z);

        boolean temporary(int x, int y, int z);

        boolean registered(int x, int y, int z);

        boolean plant(String blockId);
    }

    public record Strays(
            Map<BlockCoord, Integer> pits,
            List<BlockCoord> mounds,
            List<BlockCoord> pillars,
            List<StrayGroups.Cell> strays,
            List<BlockCoord> traces,
            int unknown,
            int natural) {
        public Strays {
            pits = Map.copyOf(Objects.requireNonNull(pits, "Ямы не могут быть null"));
            mounds = List.copyOf(Objects.requireNonNull(mounds, "Насыпи не могут быть null"));
            pillars = List.copyOf(Objects.requireNonNull(pillars, "Столбы не могут быть null"));
            strays = List.copyOf(Objects.requireNonNull(strays, "Лишнее не может быть null"));
            traces = List.copyOf(Objects.requireNonNull(traces, "Следы не могут быть null"));
            if (unknown < 0 || natural < 0) {
                throw new IllegalArgumentException("Неизвестных колонок " + unknown + " и природного " + natural
                        + ": отсутствие — ноль, а не минус");
            }
        }

        public Strays(
                Map<BlockCoord, Integer> pits,
                List<BlockCoord> mounds,
                List<StrayGroups.Cell> strays,
                List<BlockCoord> traces,
                int unknown) {
            this(pits, mounds, List.of(), strays, traces, unknown, 0);
        }

        public Strays(
                Map<BlockCoord, Integer> pits,
                List<BlockCoord> mounds,
                List<StrayGroups.Cell> strays,
                List<BlockCoord> traces,
                int unknown,
                int natural) {
            this(pits, mounds, List.of(), strays, traces, unknown, natural);
        }

        public static Strays empty() {
            return new Strays(Map.of(), List.of(), List.of(), List.of(), List.of(), 0, 0);
        }
    }

    private SettlementScan() {}

    public static Strays scan(List<ColumnMask.Cell> columns, int level, StrayRules rules, World world) {
        Objects.requireNonNull(columns, "Колонки участка не могут быть null: сканировать нечего");
        Objects.requireNonNull(rules, "Правила лишнего не могут быть null: без них не отличить мусор");
        Objects.requireNonNull(world, "Мир не может быть null: без него блоки не прочитать");
        Map<BlockCoord, Integer> pits = new LinkedHashMap<>();
        List<BlockCoord> mounds = new ArrayList<>();
        List<BlockCoord> pillars = new ArrayList<>();
        List<StrayGroups.Cell> strays = new ArrayList<>();
        List<BlockCoord> traces = new ArrayList<>();
        int unknown = 0;
        int natural = 0;
        for (ColumnMask.Cell column : columns) {
            int x = column.x();
            int z = column.z();
            if (!world.loaded(x, z)) {
                unknown++;
                continue;
            }
            int top = world.top(x, z);
            int surface = surface(world, x, z, level, top);
            OptionalInt lot = world.lotLevel(x, z);
            OptionalInt dug = world.dugTop(x, z);
            if (lot.isPresent()) {
                int lotLevel = lot.getAsInt();
                BlockCoord ground = new BlockCoord(x, lotLevel - 1, z);
                if (surface < lotLevel - 1
                        && !plannedRange(world, x, z, surface + 1, lotLevel - 1)
                        && !markedRange(world, x, z, surface + 1, lotLevel - 1)) {
                    pits.put(ground, lotLevel - 1 - surface);
                }
                List<BlockCoord> raised = new ArrayList<>();
                naturalBlocks(world, rules, x, z, lotLevel, surface, raised);
                raisedInto(raised, lotLevel, mounds, pillars);
            } else {
                natural += naturalBlocks(world, rules, x, z, level, surface, null);
                if (dug.isPresent()
                        && surface < dug.getAsInt()
                        && !plannedRange(world, x, z, surface + 1, dug.getAsInt())
                        && !markedRange(world, x, z, surface + 1, dug.getAsInt())) {
                    pits.put(new BlockCoord(x, dug.getAsInt(), z), dug.getAsInt() - surface);
                }
            }
            boolean deep = surface < level - 1 || dug.isPresent();
            int from = deep ? level - TRACE_DEPTH : Math.min(level - TRACE_SHALLOW, surface);
            int above = top < level ? level - 1 : Math.max(level + STRAY_ABOVE, surface);
            for (int y = from; y <= above; y++) {
                BlockCoord at = new BlockCoord(x, y, z);
                boolean open = world.open(x, y, z);
                if (open) {
                    if (y < level - 1 && world.spiritMarked(x, y, z)) {
                        traces.add(at);
                    }
                    continue;
                }
                if (y < level - 1) {
                    continue;
                }
                String id = world.blockId(x, y, z);
                if (world.plant(id)
                        || world.planned(x, y, z)
                        || world.playerPlaced(x, y, z)
                        || world.registered(x, y, z)
                        || (!world.temporary(x, y, z) && !rules.stray(id))) {
                    continue;
                }
                boolean high = PlotCells.defect(new PlotCells.Cell(y - level, false, false), PlotCells.Found.OTHER)
                        .filter(PlotCells.Defect.PILLAR::equals)
                        .isPresent();
                if (high) {
                    pillars.add(at);
                } else {
                    strays.add(new StrayGroups.Cell(at, id));
                }
            }
        }
        return new Strays(pits, mounds, pillars, strays, traces, unknown, natural);
    }

    private static void raisedInto(
            List<BlockCoord> raised, int lotLevel, List<BlockCoord> mounds, List<BlockCoord> pillars) {
        boolean pillar = raised.stream()
                .anyMatch(at -> PlotCells.defect(
                                new PlotCells.Cell(at.y() - lotLevel, false, false), PlotCells.Found.NATURAL)
                        .filter(PlotCells.Defect.PILLAR::equals)
                        .isPresent());
        (pillar ? pillars : mounds).addAll(raised);
    }

    private static int naturalBlocks(
            World world, StrayRules rules, int x, int z, int from, int surface, @Nullable List<BlockCoord> work) {
        int found = 0;
        for (int y = from; y <= surface; y++) {
            if (world.open(x, y, z) || world.planned(x, y, z)) {
                continue;
            }
            String id = world.blockId(x, y, z);
            if (!rules.natural(id) || world.plant(id) || world.playerPlaced(x, y, z)) {
                continue;
            }
            found++;
            if (work != null) {
                work.add(new BlockCoord(x, y, z));
            }
        }
        return found;
    }

    private static boolean plannedRange(World world, int x, int z, int from, int to) {
        for (int y = from; y <= to; y++) {
            if (world.planned(x, y, z)) {
                return true;
            }
        }
        return false;
    }

    private static boolean markedRange(World world, int x, int z, int from, int to) {
        for (int y = from; y <= to; y++) {
            if (world.spiritMarked(x, y, z)) {
                return true;
            }
        }
        return false;
    }

    private static int surface(World world, int x, int z, int level, int knownTop) {
        int top = knownTop < level ? knownTop : Math.max(knownTop, level + PlotCells.FREE_HEIGHT - 1);
        for (int y = top; y > level - PIT_DEPTH_MAX; y--) {
            if (world.open(x, y, z)) {
                continue;
            }
            String id = world.blockId(x, y, z);
            if (!world.plant(id)) {
                return y;
            }
        }
        return level - PIT_DEPTH_MAX;
    }
}
