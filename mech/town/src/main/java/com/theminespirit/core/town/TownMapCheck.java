package com.theminespirit.core.town;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public final class TownMapCheck {
    public static final int MIN_STREET = 5;

    private TownMapCheck() {}

    public static List<TownMapFinding> validate(TownMap map, TownRules rules, ColumnMask region, ColumnMask water) {
        return validate(map, rules, region, water, null);
    }

    public static List<TownMapFinding> validate(
            TownMap map, TownRules rules, ColumnMask region, ColumnMask water, @Nullable TownMap previous) {
        Objects.requireNonNull(map, "Карта не может быть null: проверять нечего");
        Objects.requireNonNull(rules, "Правила не могут быть null: размеры участков берутся оттуда");
        Objects.requireNonNull(region, "Граница не может быть null: карта лежит внутри неё");
        Objects.requireNonNull(water, "Вода не может быть null: по ней видно, куда нельзя ставить");
        List<TownMapFinding> findings = new ArrayList<>();
        TownStreets streets = TownMapRules.streets(map, region);
        ColumnMask ponds = WaterPonds.small(water, region);
        Set<String> plotIds = new HashSet<>();
        for (TownMap.MapPlot plot : map.plots()) {
            boolean inherited = inheritedPlot(previous, plot);
            if (!plotIds.add(plot.id())) {
                findings.add(new TownMapFinding(plot.id(), "duplicate", "участок '" + plot.id() + "' объявлен дважды"));
            }
            Optional<TownEntry> entry = TownMapRules.entryFor(rules, plot.building());
            if (entry.isEmpty()) {
                findings.add(new TownMapFinding(
                        plot.id(),
                        "building",
                        "участок '" + plot.id() + "' ссылается на неизвестную постройку '" + plot.building() + "'"));
            } else {
                TownEntry template = entry.orElseThrow();
                int w = plot.width();
                int d = plot.depth();
                boolean same = (w == template.width() && d == template.depth())
                        || (template.rotate() && w == template.depth() && d == template.width());
                if (!same) {
                    findings.add(new TownMapFinding(
                            plot.id(),
                            "size",
                            "участок '" + plot.id() + "' " + w + "×" + d + " не совпадает с постройкой '"
                                    + plot.building() + "' " + template.width() + "×" + template.depth()));
                }
            }
            if (!facing(plot.facing())) {
                findings.add(new TownMapFinding(
                        plot.id(),
                        "facing",
                        "у участка '" + plot.id() + "' неизвестная сторона двери '" + plot.facing() + "'"));
            }
            checkCells(
                    plot.id(),
                    "участок",
                    plot.minX(),
                    plot.minZ(),
                    plot.minX() + plot.width() - 1,
                    plot.minZ() + plot.depth() - 1,
                    region,
                    water,
                    ponds,
                    streets,
                    inherited,
                    true,
                    findings);
            for (TownMap.MapPlot other : map.plots()) {
                if (other.id().equals(plot.id()) || other.id().compareTo(plot.id()) > 0) {
                    continue;
                }
                if (overlaps(plot, other, rules.spacing().gap())) {
                    findings.add(new TownMapFinding(
                            plot.id(), "overlap", "участок '" + plot.id() + "' накладывается на '" + other.id() + "'"));
                }
            }
        }
        Set<String> streetIds = new HashSet<>();
        for (TownMap.MapStreet street : map.streets()) {
            boolean inherited = previous != null && previous.streets().stream().anyMatch(street::equals);
            if (!streetIds.add(street.id())) {
                findings.add(
                        new TownMapFinding(street.id(), "duplicate", "улица '" + street.id() + "' объявлена дважды"));
            }
            if (!kind(street.kind())) {
                findings.add(new TownMapFinding(
                        street.id(), "kind", "улица '" + street.id() + "' неизвестного вида '" + street.kind() + "'"));
                continue;
            }
            if (!inherited && (street.width() < MIN_STREET || street.depth() < MIN_STREET)) {
                findings.add(new TownMapFinding(
                        street.id(),
                        "narrow",
                        "улица '" + street.id() + "' " + street.width() + "×" + street.depth() + " уже " + MIN_STREET
                                + " блоков"));
            }
            checkCells(
                    street.id(),
                    "улица",
                    street.minX(),
                    street.minZ(),
                    street.maxX(),
                    street.maxZ(),
                    region,
                    water,
                    ponds,
                    streets,
                    false,
                    false,
                    findings);
        }
        checkStreetNetwork(map.streets(), region, previous, findings);
        TownWallCheck.validate(map, region, water, findings);
        return List.copyOf(findings);
    }

    private static void checkStreetNetwork(
            List<TownMap.MapStreet> streets,
            ColumnMask region,
            @Nullable TownMap previous,
            List<TownMapFinding> findings) {
        if (streets.size() < 2) {
            return;
        }
        List<Set<ColumnMask.Cell>> cells = new ArrayList<>();
        for (TownMap.MapStreet street : streets) {
            cells.add(streetCells(street, region));
        }
        for (int index = 0; index < streets.size(); index++) {
            if (inheritedStreet(previous, streets.get(index))) {
                continue;
            }
            boolean linked = false;
            for (int other = 0; other < streets.size() && !linked; other++) {
                linked = other != index && joined(cells.get(index), cells.get(other));
            }
            if (!linked) {
                findings.add(new TownMapFinding(
                        streets.get(index).id(),
                        "disconnected",
                        "улица '" + streets.get(index).id() + "' не соединена с остальными улицами"));
            }
        }
    }

    private static boolean inheritedStreet(@Nullable TownMap previous, TownMap.MapStreet street) {
        return previous != null && previous.streets().stream().anyMatch(street::equals);
    }

    private static Set<ColumnMask.Cell> streetCells(TownMap.MapStreet street, ColumnMask region) {
        Set<ColumnMask.Cell> out = new HashSet<>();
        for (int x = street.minX(); x <= street.maxX(); x++) {
            for (int z = street.minZ(); z <= street.maxZ(); z++) {
                if (region.contains(x, z)) {
                    out.add(new ColumnMask.Cell(x, z));
                }
            }
        }
        return out;
    }

    private static boolean joined(Set<ColumnMask.Cell> first, Set<ColumnMask.Cell> second) {
        for (ColumnMask.Cell cell : first) {
            if (second.contains(cell)
                    || second.contains(new ColumnMask.Cell(cell.x() + 1, cell.z()))
                    || second.contains(new ColumnMask.Cell(cell.x() - 1, cell.z()))
                    || second.contains(new ColumnMask.Cell(cell.x(), cell.z() + 1))
                    || second.contains(new ColumnMask.Cell(cell.x(), cell.z() - 1))) {
                return true;
            }
        }
        return false;
    }

    private static boolean inheritedPlot(@Nullable TownMap previous, TownMap.MapPlot plot) {
        return previous != null && previous.plots().stream().anyMatch(plot::equals);
    }

    private static void checkCells(
            String object,
            String kind,
            int x1,
            int z1,
            int x2,
            int z2,
            ColumnMask region,
            ColumnMask water,
            ColumnMask ponds,
            TownStreets streets,
            boolean allowWater,
            boolean allowPonds,
            List<TownMapFinding> findings) {
        int outside = 0;
        int wet = 0;
        int onStreet = 0;
        int firstX = 0;
        int firstZ = 0;
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                if (!region.contains(x, z)) {
                    if (outside++ == 0) {
                        firstX = x;
                        firstZ = z;
                    }
                    continue;
                }
                if (!allowWater && water.contains(x, z) && !(allowPonds && ponds.contains(x, z))) {
                    wet++;
                }
                if (streets.contains(x, z) && "участок".equals(kind)) {
                    onStreet++;
                }
            }
        }
        if (outside > 0) {
            findings.add(new TownMapFinding(
                    object,
                    "outside",
                    kind + " '" + object + "' выходит за границу поселения в " + firstX + "," + firstZ + " (" + outside
                            + " клеток)"));
        }
        if (wet > 0) {
            findings.add(new TownMapFinding(
                    object, "water", kind + " '" + object + "' заходит в воду (" + wet + " клеток)"));
        }
        if (onStreet > 0) {
            findings.add(new TownMapFinding(
                    object, "street", kind + " '" + object + "' стоит на улице (" + onStreet + " клеток)"));
        }
    }

    private static boolean overlaps(TownMap.MapPlot first, TownMap.MapPlot second, int gap) {
        return first.minX() - gap <= second.minX() + second.width() - 1
                && second.minX() - gap <= first.minX() + first.width() - 1
                && first.minZ() - gap <= second.minZ() + second.depth() - 1
                && second.minZ() - gap <= first.minZ() + first.depth() - 1;
    }

    private static boolean facing(String value) {
        try {
            Placement.Facing.valueOf(value.toUpperCase(Locale.ROOT));
            return true;
        } catch (IllegalArgumentException unknown) {
            return false;
        }
    }

    private static boolean kind(String value) {
        return TownMapRules.MAIN.equals(value) || TownMapRules.SIDE.equals(value);
    }
}
