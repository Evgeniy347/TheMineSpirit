package com.theminespirit.core.building;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.ToIntFunction;

/**
 * Витрина {@code /spirit demo city N} (решение владельца 12): постройки этапа поселения {@code N - 1} стоят вдоль
 * улицы по обе стороны, двери — к улице, каждая — на своей площадке без наложений. Чистые функции: выбор построек
 * по этапу и раскладка участков; мир и духи — в {@code DemoService}.
 *
 * <p>Улица идёт от точки старта по направлению взгляда; её ось свободна, площадки слева и справа начинаются в
 * клетке от оси и идут вдоль улицы впритык, каждая сторона — своим курсором.
 */
public final class DemoCity {
    public static final int MIN_LEVEL = 1;
    public static final int MAX_LEVEL = Preset.STAGE_MAX + 1;

    public record Pick(DemoBuildings.Building building, DemoBuildings.Level level) {
        public Pick {
            Objects.requireNonNull(building, "Постройка витрины не может быть null: нечего строить");
            Objects.requireNonNull(level, "Уровень постройки витрины не может быть null: неизвестна схема");
        }
    }

    public record Plot(DemoPlacement.Door door, DemoPlacement.Size size) {
        public Plot {
            Objects.requireNonNull(door, "Дверь участка не может быть null: без неё не развернуть постройку к улице");
            Objects.requireNonNull(size, "Размер участка не может быть null: без него не разложить улицу");
        }
    }

    private DemoCity() {}

    // Для каждой семьи с уровнями — старший уровень, доступный к этапу cityLevel - 1; семьи без уровней (старые
    // схемы) и семьи, ни один уровень которых ещё не доступен, в витрину не входят.
    public static List<Pick> pick(List<DemoBuildings.Building> catalog, ToIntFunction<String> stageOf, int cityLevel) {
        if (cityLevel < MIN_LEVEL || cityLevel > MAX_LEVEL) {
            throw new IllegalArgumentException("Уровень витрины " + cityLevel + " вне " + MIN_LEVEL + ".." + MAX_LEVEL
                    + ": уровень N показывает этап поселения N-1, этапов " + (MAX_LEVEL - MIN_LEVEL + 1));
        }
        int stage = cityLevel - 1;
        List<Pick> picks = new ArrayList<>();
        for (DemoBuildings.Building building : catalog) {
            DemoBuildings.Level best = null;
            for (DemoBuildings.Level level : building.levels()) {
                if (level.level() >= 1
                        && stageOf.applyAsInt(level.scheme()) <= stage
                        && (best == null || level.level() > best.level())) {
                    best = level;
                }
            }
            if (best != null) {
                picks.add(new Pick(building, best));
            }
        }
        return List.copyOf(picks);
    }

    // Раскладка участков: чётные — слева от улицы, нечётные — справа; дверь каждой постройки смотрит на улицу.
    public static List<DemoPlacement> layout(DemoPlacement.Anchor start, DemoPlacement.Look look, List<Plot> plots) {
        Objects.requireNonNull(start, "Точка старта улицы не может быть null: неизвестно, откуда раскладывать");
        if (Math.abs(look.dx()) + Math.abs(look.dz()) != 1) {
            throw new IllegalArgumentException("Направление улицы (" + look.dx() + ", " + look.dz()
                    + ") не вдоль оси: улица кладётся только по сторонам света");
        }
        DemoPlacement.Look right = new DemoPlacement.Look(-look.dz(), look.dx());
        int half = BuildTuning.DEMO_PLATFORM_PADDING;
        int[] cursor = {1, 1};
        List<DemoPlacement> placements = new ArrayList<>();
        for (int i = 0; i < plots.size(); i++) {
            Plot plot = plots.get(i);
            int side = i % 2;
            int sign = side == 0 ? -1 : 1;
            DemoPlacement.Look toBuilding = new DemoPlacement.Look(sign * right.dx(), sign * right.dz());
            DemoPlacement probe = place(start, look, right, 0, sign * half, toBuilding, plot);
            int shift = cursor[side] - alongMin(start, look, probe.platform());
            DemoPlacement placed = place(start, look, right, shift, sign * half, toBuilding, plot);
            cursor[side] = alongMax(start, look, placed.platform()) + 1;
            placements.add(placed);
        }
        return List.copyOf(placements);
    }

    private static DemoPlacement place(
            DemoPlacement.Anchor start,
            DemoPlacement.Look look,
            DemoPlacement.Look right,
            int along,
            int across,
            DemoPlacement.Look toBuilding,
            Plot plot) {
        DemoPlacement.Anchor anchor = new DemoPlacement.Anchor(
                start.x() + along * look.dx() + across * right.dx(),
                start.y(),
                start.z() + along * look.dz() + across * right.dz());
        return DemoPlacement.compute(anchor, toBuilding, plot.door(), plot.size(), null);
    }

    private static int along(DemoPlacement.Anchor start, DemoPlacement.Look look, int x, int z) {
        return (x - start.x()) * look.dx() + (z - start.z()) * look.dz();
    }

    private static int alongMin(DemoPlacement.Anchor start, DemoPlacement.Look look, BuildBox box) {
        return Math.min(along(start, look, box.minX(), box.minZ()), along(start, look, box.maxX(), box.maxZ()));
    }

    private static int alongMax(DemoPlacement.Anchor start, DemoPlacement.Look look, BuildBox box) {
        return Math.max(along(start, look, box.minX(), box.minZ()), along(start, look, box.maxX(), box.maxZ()));
    }
}
