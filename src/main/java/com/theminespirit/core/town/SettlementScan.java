package com.theminespirit.core.town;

import com.theminespirit.core.board.PlotCells;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.policy.BlockProtection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class SettlementScan {
    public static final int PIT_DEPTH_MAX = 64;
    private static final int STRAY_ABOVE = 4;
    private static final int TRACE_DEPTH = 16;

    public interface World {
        boolean loaded(int x, int z);

        boolean open(int x, int y, int z);

        String blockId(int x, int y, int z);

        boolean planned(int x, int y, int z);

        boolean playerPlaced(int x, int y, int z);

        boolean spiritMarked(int x, int y, int z);

        boolean registered(int x, int y, int z);

        boolean plant(String blockId);
    }

    public record Strays(
            Map<BlockCoord, Integer> pits,
            List<BlockCoord> mounds,
            List<StrayGroups.Cell> strays,
            List<BlockCoord> traces,
            int unknown) {
        public Strays {
            pits = Map.copyOf(Objects.requireNonNull(pits, "Ямы не могут быть null"));
            mounds = List.copyOf(Objects.requireNonNull(mounds, "Насыпи не могут быть null"));
            strays = List.copyOf(Objects.requireNonNull(strays, "Лишнее не может быть null"));
            traces = List.copyOf(Objects.requireNonNull(traces, "Следы не могут быть null"));
            if (unknown < 0) {
                throw new IllegalArgumentException(
                        "Неизвестных колонок " + unknown + ": отсутствие неизвестности — ноль, а не минус");
            }
        }

        public static Strays empty() {
            return new Strays(Map.of(), List.of(), List.of(), List.of(), 0);
        }
    }

    private SettlementScan() {}

    public static Strays scan(List<ColumnMask.Cell> columns, int level, BlockProtection rules, World world) {
        Objects.requireNonNull(columns, "Колонки участка не могут быть null: сканировать нечего");
        Objects.requireNonNull(rules, "Правила лишнего не могут быть null: без них не отличить мусор");
        Objects.requireNonNull(world, "Мир не может быть null: без него блоки не прочитать");
        Map<BlockCoord, Integer> pits = new LinkedHashMap<>();
        List<BlockCoord> mounds = new ArrayList<>();
        List<StrayGroups.Cell> strays = new ArrayList<>();
        List<BlockCoord> traces = new ArrayList<>();
        int unknown = 0;
        for (ColumnMask.Cell column : columns) {
            int x = column.x();
            int z = column.z();
            if (!world.loaded(x, z)) {
                unknown++;
                continue;
            }
            int surface = surface(world, x, z, level);
            BlockCoord ground = new BlockCoord(x, level - 1, z);
            if (surface < level - 1 && !world.planned(ground.x(), ground.y(), ground.z())) {
                pits.put(ground, level - 1 - surface);
            }
            if (surface >= level) {
                for (int y = level; y <= surface; y++) {
                    if (world.open(x, y, z) || world.planned(x, y, z)) {
                        continue;
                    }
                    String id = world.blockId(x, y, z);
                    if (rules.natural(id) && !world.plant(id) && !world.playerPlaced(x, y, z)) {
                        mounds.add(new BlockCoord(x, y, z));
                    }
                }
            }
            for (int y = level - TRACE_DEPTH; y <= level + STRAY_ABOVE; y++) {
                BlockCoord at = new BlockCoord(x, y, z);
                boolean open = world.open(x, y, z);
                if (open) {
                    if (world.spiritMarked(x, y, z)) {
                        traces.add(at);
                    }
                    continue;
                }
                if (y < level - 1) {
                    continue;
                }
                if (world.planned(x, y, z)
                        || world.playerPlaced(x, y, z)
                        || world.registered(x, y, z)
                        || !rules.stray(world.blockId(x, y, z))) {
                    continue;
                }
                strays.add(new StrayGroups.Cell(at, world.blockId(x, y, z)));
            }
        }
        return new Strays(pits, mounds, strays, traces, unknown);
    }

    private static int surface(World world, int x, int z, int level) {
        for (int y = level + PlotCells.FREE_HEIGHT - 1; y > level - PIT_DEPTH_MAX; y--) {
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
