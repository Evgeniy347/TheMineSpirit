package com.theminespirit.core.town;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.ToIntFunction;

public final class TownMapRules {
    public static final String MAIN = "main";
    public static final String SIDE = "side";
    public static final String RING = "ring";

    private TownMapRules() {}

    public static TownPlan build(
            TownMap map, TownRules rules, ColumnMask region, BlockCoord anchor, ToIntFunction<String> stageOf) {
        Objects.requireNonNull(map, "Карта не может быть null: по ней строится план");
        Objects.requireNonNull(rules, "Правила плана не могут быть null: размеры участков берутся оттуда");
        Objects.requireNonNull(region, "Граница не может быть null: план живёт внутри неё");
        Objects.requireNonNull(anchor, "Якорь поселения не может быть null: высота берётся из мира");
        Objects.requireNonNull(stageOf, "Этап постройки не может быть null: задание доски берёт этап");
        TownStreets streets = streets(map, rules, region);
        List<Placement> placements = new ArrayList<>();
        for (TownMap.MapPlot plot : map.plots()) {
            TownEntry entry = rules.entry(plot.building())
                    .orElseThrow(() -> new IllegalArgumentException("Нет постройки '" + plot.building()
                            + "' в каталоге участков: карта ссылается на неизвестную постройку"));
            placements.add(new Placement(
                    plot.id(),
                    entry.name(),
                    stageOf.applyAsInt(plot.building()),
                    plot.minX(),
                    plot.minZ(),
                    plot.width(),
                    plot.depth(),
                    plot.door()));
        }
        FenceLine wall = wallLine(map, streets, region).orElse(new FenceLine(List.of(), List.of()));
        return new TownPlan(anchor, region, streets, placements, wall, List.of());
    }

    public static TownStreets streets(TownMap map, TownRules rules, ColumnMask region) {
        Objects.requireNonNull(map, "Карта не может быть null: улицы берутся из неё");
        Objects.requireNonNull(rules, "Правила не могут быть null: ширины улиц — данные");
        Objects.requireNonNull(region, "Граница не может быть null: улицы идут внутри неё");
        BitSet main = new BitSet(region.width() * region.depth());
        BitSet side = new BitSet(region.width() * region.depth());
        BitSet ring = new BitSet(region.width() * region.depth());
        for (TownMap.MapStreet street : map.streets()) {
            if (street.ring()) {
                ringBand(region, rules.streets(), ring);
                continue;
            }
            BitSet target = MAIN.equals(street.kind()) ? main : side;
            for (int x = street.minX(); x <= street.maxX(); x++) {
                for (int z = street.minZ(); z <= street.maxZ(); z++) {
                    if (region.contains(x, z)) {
                        target.set((z - region.minZ()) * region.width() + x - region.minX());
                    }
                }
            }
        }
        return new TownStreets(
                new ColumnMask(region.minX(), region.minZ(), region.width(), region.depth(), main),
                new ColumnMask(region.minX(), region.minZ(), region.width(), region.depth(), side),
                new ColumnMask(region.minX(), region.minZ(), region.width(), region.depth(), ring));
    }

    private static void ringBand(ColumnMask region, TownRules.Streets rules, BitSet out) {
        GridBits bits = new GridBits(region.width(), region.depth());
        BitSet inside = new BitSet(bits.size());
        for (ColumnMask.Cell cell : region.cells()) {
            inside.set(bits.at(cell.x() - region.minX(), cell.z() - region.minZ()));
        }
        int[] depth = bits.insetDistance(inside);
        for (int index = inside.nextSetBit(0); index >= 0; index = inside.nextSetBit(index + 1)) {
            int here = depth[index];
            if (here > rules.ringInset() && here <= rules.ringInset() + rules.ring()) {
                out.set(index);
            }
        }
    }

    public static Optional<FenceLine> wallLine(TownMap map, TownStreets streets, ColumnMask region) {
        Objects.requireNonNull(map, "Карта не может быть null: стена берётся из неё");
        Objects.requireNonNull(streets, "Улицы не могут быть null: по ним видны проёмы");
        Objects.requireNonNull(region, "Граница не может быть null: стена идёт по ней");
        Set<ColumnMask.Cell> cells = new LinkedHashSet<>();
        for (TownMap.WallSegment segment : map.wall()) {
            if (segment.horizontal()) {
                for (int x = segment.minX(); x <= segment.maxX(); x++) {
                    cells.add(new ColumnMask.Cell(x, segment.z1()));
                }
            } else {
                for (int z = segment.minZ(); z <= segment.maxZ(); z++) {
                    cells.add(new ColumnMask.Cell(segment.x1(), z));
                }
            }
        }
        if (cells.isEmpty()) {
            return Optional.empty();
        }
        List<ColumnMask.Cell> loop = order(cells);
        List<Integer> gates = new ArrayList<>();
        for (int index = 0; index < loop.size(); index++) {
            if (streets.contains(loop.get(index).x(), loop.get(index).z())) {
                gates.add(index);
            }
        }
        return Optional.of(new FenceLine(loop, gates));
    }

    private static List<ColumnMask.Cell> order(Set<ColumnMask.Cell> cells) {
        Set<ColumnMask.Cell> left = new HashSet<>(cells);
        ColumnMask.Cell start = cells.stream()
                .min((a, b) -> a.z() != b.z() ? Integer.compare(a.z(), b.z()) : Integer.compare(a.x(), b.x()))
                .orElseThrow();
        List<ColumnMask.Cell> loop = new ArrayList<>();
        ColumnMask.Cell current = start;
        ColumnMask.Cell previous = null;
        while (loop.size() <= cells.size()) {
            loop.add(current);
            left.remove(current);
            ColumnMask.Cell next = null;
            for (ColumnMask.Cell neighbour : neighbours(current)) {
                if (neighbour.equals(previous)) {
                    continue;
                }
                if (!left.contains(neighbour) && !neighbour.equals(start)) {
                    continue;
                }
                next = neighbour;
                break;
            }
            if (next == null || next.equals(start)) {
                break;
            }
            previous = current;
            current = next;
        }
        return List.copyOf(loop);
    }

    private static List<ColumnMask.Cell> neighbours(ColumnMask.Cell cell) {
        return List.of(
                new ColumnMask.Cell(cell.x() + 1, cell.z()),
                new ColumnMask.Cell(cell.x() - 1, cell.z()),
                new ColumnMask.Cell(cell.x(), cell.z() + 1),
                new ColumnMask.Cell(cell.x(), cell.z() - 1));
    }
}
