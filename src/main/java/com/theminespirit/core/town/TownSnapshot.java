package com.theminespirit.core.town;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record TownSnapshot(
        int stage,
        BlockCoord anchor,
        int area,
        List<ColumnMask.Cell> contour,
        List<ColumnMask.Cell> contourGates,
        List<ColumnMask.Cell> stageFence,
        List<ColumnMask.Cell> stageGates,
        Progress stageFenceProgress,
        List<Street> streets,
        List<Lot> lots,
        List<List<BlockCoord>> paths) {
    private static final int PERCENT = 100;

    public TownSnapshot {
        Objects.requireNonNull(anchor, "Якорь снимка не может быть null");
        Objects.requireNonNull(stageFenceProgress, "Готовность ограды этапа не может быть null");
        contour = List.copyOf(contour);
        contourGates = List.copyOf(contourGates);
        stageFence = List.copyOf(stageFence);
        stageGates = List.copyOf(stageGates);
        streets = List.copyOf(streets);
        lots = List.copyOf(lots);
        paths = paths.stream().map(List::copyOf).toList();
    }

    public record Progress(int done, int total, boolean damaged) {
        public static final Progress NONE = new Progress(0, 0, false);

        public Progress {
            if (done < 0 || total < done) {
                throw new IllegalArgumentException("Готовность " + done + " из " + total
                        + ": сделанных клеток не меньше нуля и не больше всех клеток схемы");
            }
        }

        public int percent() {
            return total == 0 ? 0 : done * PERCENT / total;
        }
    }

    public record Street(String kind, int x1, int z1, int x2, int z2) {}

    public record Lot(
            String id, String name, int stage, int x, int z, int width, int depth, String facing, Progress progress) {}

    public static TownSnapshot of(
            TownPlan plan, StageSite site, Map<String, Progress> progress, List<List<BlockCoord>> paths) {
        Objects.requireNonNull(plan, "План не может быть null: снимок карты берётся из него");
        Objects.requireNonNull(site, "Место этапа не может быть null: ограда этапа — на карте");
        Objects.requireNonNull(progress, "Готовность построек не может быть null: пусто — пустая карта");
        List<Street> streets = new ArrayList<>();
        streets(streets, "main", plan.streets().main());
        streets(streets, "side", plan.streets().side());
        streets(streets, "ring", plan.streets().ring());
        List<Lot> lots = plan.placements().stream()
                .map(place -> lot(place, site, progress.getOrDefault(place.id(), Progress.NONE)))
                .toList();
        return new TownSnapshot(
                site.stage(),
                plan.anchor(),
                plan.area(),
                plan.fence().posts(),
                gateCells(plan.fence()),
                site.ring().posts(),
                gateCells(site.ring()),
                progress.getOrDefault(TownPlanner.FENCE, Progress.NONE),
                streets,
                lots,
                paths);
    }

    private static Lot lot(Placement place, StageSite site, Progress progress) {
        if (StageSites.MINE.equals(place.id())) {
            MinePlot mine = site.minePlot();
            return new Lot(
                    place.id(),
                    place.name(),
                    place.stage(),
                    mine.x1(),
                    mine.z1(),
                    mine.width(),
                    mine.depth(),
                    place.facing().id(),
                    progress);
        }
        return new Lot(
                place.id(),
                place.name(),
                place.stage(),
                place.x(),
                place.z(),
                place.width(),
                place.depth(),
                place.facing().id(),
                progress);
    }

    private static List<ColumnMask.Cell> gateCells(FenceLine line) {
        return line.gates().stream().map(line.posts()::get).toList();
    }

    private static void streets(List<Street> out, String kind, ColumnMask mask) {
        Street open = null;
        for (ColumnMask.Cell cell : mask.cells()) {
            if (open != null && open.z1() == cell.z() && open.x2() + 1 == cell.x()) {
                open = new Street(kind, open.x1(), open.z1(), cell.x(), open.z2());
                continue;
            }
            if (open != null) {
                out.add(open);
            }
            open = new Street(kind, cell.x(), cell.z(), cell.x(), cell.z());
        }
        if (open != null) {
            out.add(open);
        }
    }
}
