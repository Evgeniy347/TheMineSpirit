package com.theminespirit.core.town;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

public final class Walkways {
    public static final int MAX_LINK = 24;
    public static final int HEADROOM = 2;

    private Walkways() {}

    public static List<ColumnMask.Cell> of(TownPlan plan, int stage) {
        Objects.requireNonNull(plan, "План не может быть null: тропы берутся из плана поселения");
        TownStreets streets = plan.streets();
        ColumnMask street = streets.main().union(streets.side()).union(streets.ring());
        Set<ColumnMask.Cell> cells = new LinkedHashSet<>(street.cells());
        for (int gate : plan.fence().gates()) {
            cells.add(plan.fence().posts().get(gate));
        }
        for (Placement place : plan.upToStage(stage)) {
            cells.addAll(link(plan, street, place));
        }
        return List.copyOf(cells);
    }

    public static List<BlockCoord> clearance(
            SettlementScan.World world, Predicate<String> keep, List<ColumnMask.Cell> ways, int level) {
        Objects.requireNonNull(world, "Мир не может быть null: срезка над тропами считается по его блокам");
        Objects.requireNonNull(keep, "Правило «не трогать» не может быть null: сундуки и станции не срезаются");
        List<BlockCoord> cut = new ArrayList<>();
        for (ColumnMask.Cell cell : ways) {
            if (!world.loaded(cell.x(), cell.z())) {
                continue;
            }
            int floor = world.lotLevel(cell.x(), cell.z()).orElse(level);
            for (int y = floor + HEADROOM - 1; y >= floor; y--) {
                if (!world.open(cell.x(), y, cell.z())
                        && !world.planned(cell.x(), y, cell.z())
                        && !world.registered(cell.x(), y, cell.z())
                        && !world.playerPlaced(cell.x(), y, cell.z())
                        && !keep.test(world.blockId(cell.x(), y, cell.z()))) {
                    cut.add(new BlockCoord(cell.x(), y, cell.z()));
                }
            }
        }
        return List.copyOf(cut);
    }

    static List<ColumnMask.Cell> link(TownPlan plan, ColumnMask street, Placement place) {
        int dx = place.facing() == Placement.Facing.EAST ? 1 : place.facing() == Placement.Facing.WEST ? -1 : 0;
        int dz = place.facing() == Placement.Facing.SOUTH ? 1 : place.facing() == Placement.Facing.NORTH ? -1 : 0;
        int x = dx > 0 ? place.x() + place.width() : dx < 0 ? place.x() - 1 : place.x() + place.width() / 2;
        int z = dz > 0 ? place.z() + place.depth() : dz < 0 ? place.z() - 1 : place.z() + place.depth() / 2;
        Set<ColumnMask.Cell> path = new LinkedHashSet<>();
        for (int step = 0; step < MAX_LINK; step++, x += dx, z += dz) {
            if (street.contains(x, z)) {
                return List.copyOf(path);
            }
            int cx = x;
            int cz = z;
            if (!plan.onPlot(x, z) || plan.placements().stream().anyMatch(other -> other.covers(cx, cz))) {
                return List.copyOf(path);
            }
            path.add(new ColumnMask.Cell(x, z));
        }
        return List.copyOf(path);
    }
}
