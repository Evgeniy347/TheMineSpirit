package com.theminespirit.core.building;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntBinaryOperator;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

public final class ScaffoldPlan {
    public record Stalk(String id, List<BlockCoord> cells) {
        public Stalk {
            Objects.requireNonNull(id, "id столба не может быть null: по нему карточка находит свои блоки");
            cells = List.copyOf(Objects.requireNonNull(cells, "Клетки столба не могут быть null"));
            if (cells.isEmpty()) {
                throw new IllegalArgumentException("Столб без клеток: подмость строить не под что");
            }
        }

        public int x() {
            return cells.get(0).x();
        }

        public int z() {
            return cells.get(0).z();
        }

        public @Nullable Stalk without(Set<BlockCoord> taken) {
            List<BlockCoord> left = new ArrayList<>(cells);
            left.removeAll(taken);
            return left.isEmpty() ? null : new Stalk(id, left);
        }
    }

    public record Scaffold(String id, BlockCoord anchor, int height, List<String> stalks, List<BlockCoord> cells) {
        public Scaffold {
            Objects.requireNonNull(id, "id подмости не может быть null: карточка доски живёт по нему");
            Objects.requireNonNull(anchor, "Точка башни не может быть null: к ней идут духи");
            if (height < 0) {
                throw new IllegalArgumentException("Высота подмости " + height + " отрицательна");
            }
            stalks = List.copyOf(Objects.requireNonNull(stalks, "Столбы подмости не могут быть null"));
            cells = List.copyOf(Objects.requireNonNull(cells, "Клетки подмости не могут быть null"));
            if (cells.isEmpty()) {
                throw new IllegalArgumentException("Подмость без клеток: сносить нечего");
            }
        }

        public List<BlockCoord> towerCells() {
            List<BlockCoord> out = new ArrayList<>(height);
            for (int step = 0; step < height; step++) {
                out.add(new BlockCoord(anchor.x(), anchor.y() + step, anchor.z()));
            }
            return List.copyOf(out);
        }

        public List<BlockCoord> dismantle() {
            List<BlockCoord> topDown = new ArrayList<>(towerCells());
            topDown.sort(Comparator.comparingInt(BlockCoord::y).reversed());
            return List.copyOf(topDown);
        }

        public List<BlockCoord> targetsTopDown() {
            return cells.stream()
                    .sorted(Comparator.comparingInt(BlockCoord::y)
                            .reversed()
                            .thenComparingInt(BlockCoord::x)
                            .thenComparingInt(BlockCoord::z))
                    .toList();
        }

        public int blocks() {
            return cells.size();
        }
    }

    public record Plan(List<Scaffold> scaffolds, List<BlockCoord> unreachable) {
        public Plan {
            scaffolds = List.copyOf(Objects.requireNonNull(scaffolds, "Подмости не могут быть null"));
            unreachable = List.copyOf(Objects.requireNonNull(unreachable, "Недостижимое не может быть null"));
        }
    }

    private record Candidate(BlockCoord anchor, int height, Set<String> stalks, List<BlockCoord> cells) {}

    private ScaffoldPlan() {}

    public static String id(BlockCoord anchor) {
        return "scaffold:" + anchor.x() + "," + anchor.y() + "," + anchor.z();
    }

    public static Plan plan(
            List<Stalk> stalks, SpiritReach reach, IntBinaryOperator groundFeet, Predicate<BlockCoord> freeAnchor) {
        Objects.requireNonNull(stalks, "Столбы не могут быть null: пусто — пустой план");
        Objects.requireNonNull(reach, "Досягаемость не может быть null: без неё башня не знает высоту");
        Objects.requireNonNull(groundFeet, "Высоты стоянок не могут быть null");
        Objects.requireNonNull(freeAnchor, "Свобода точки башни не может быть null");
        List<Stalk> left = new ArrayList<>();
        for (Stalk stalk : stalks) {
            if (stalk != null) {
                left.add(stalk);
            }
        }
        List<Scaffold> scaffolds = new ArrayList<>();
        List<BlockCoord> unreachable = new ArrayList<>();
        while (!left.isEmpty()) {
            Candidate best = best(left, reach, groundFeet, freeAnchor);
            if (best == null) {
                left.forEach(stalk -> unreachable.addAll(stalk.cells()));
                break;
            }
            Set<BlockCoord> taken = new LinkedHashSet<>(best.cells());
            List<Stalk> next = new ArrayList<>();
            for (Stalk stalk : left) {
                Stalk rest = stalk.without(taken);
                if (rest != null) {
                    next.add(rest);
                }
            }
            left = next;
            scaffolds.add(new Scaffold(
                    id(best.anchor()),
                    best.anchor(),
                    best.height(),
                    best.stalks().stream().sorted().toList(),
                    best.cells()));
        }
        return new Plan(scaffolds, unreachable);
    }

    private static @Nullable Candidate best(
            List<Stalk> left, SpiritReach reach, IntBinaryOperator groundFeet, Predicate<BlockCoord> freeAnchor) {
        Candidate best = null;
        for (BlockCoord anchor : anchors(left, reach, groundFeet, freeAnchor)) {
            int ground = groundFeet.applyAsInt(anchor.x(), anchor.z());
            for (int height : heights(left, reach, anchor, ground)) {
                Candidate candidate = cover(left, reach, anchor, ground, height);
                if (candidate == null) {
                    continue;
                }
                if (best == null || stronger(candidate, best)) {
                    best = candidate;
                }
            }
        }
        return best;
    }

    private static boolean stronger(Candidate candidate, Candidate current) {
        if (candidate.stalks().size() != current.stalks().size()) {
            return candidate.stalks().size() > current.stalks().size();
        }
        if (candidate.cells().size() != current.cells().size()) {
            return candidate.cells().size() > current.cells().size();
        }
        if (candidate.height() != current.height()) {
            return candidate.height() < current.height();
        }
        return candidate.anchor().compareTo(current.anchor()) < 0;
    }

    private static List<Integer> heights(List<Stalk> left, SpiritReach reach, BlockCoord anchor, int ground) {
        Set<Integer> found = new LinkedHashSet<>();
        for (Stalk stalk : left) {
            for (BlockCoord cell : stalk.cells()) {
                if (Math.abs(cell.x() - anchor.x()) > reach.horizontal()
                        || Math.abs(cell.z() - anchor.z()) > reach.horizontal()) {
                    continue;
                }
                int height = cell.y() - ground - 1;
                if (height >= 1) {
                    found.add(height);
                }
            }
        }
        List<Integer> sorted = new ArrayList<>(found);
        sorted.sort(Integer::compareTo);
        return List.copyOf(sorted);
    }

    private static @Nullable Candidate cover(
            List<Stalk> left, SpiritReach reach, BlockCoord anchor, int ground, int height) {
        BlockCoord feet = new BlockCoord(anchor.x(), ground + height, anchor.z());
        Set<String> stalks = new LinkedHashSet<>();
        List<BlockCoord> cells = new ArrayList<>();
        for (Stalk stalk : left) {
            for (BlockCoord cell : stalk.cells()) {
                if (reach.reaches(feet, cell)
                        && !SpiritReach.forbiddenStand(feet, cell)
                        && !ReachPlanner.inBody(feet, cell)) {
                    stalks.add(stalk.id());
                    cells.add(cell);
                }
            }
        }
        if (cells.isEmpty()) {
            return null;
        }
        cells.sort(Comparator.comparingInt(BlockCoord::y)
                .reversed()
                .thenComparingInt(BlockCoord::x)
                .thenComparingInt(BlockCoord::z));
        return new Candidate(anchor, height, stalks, List.copyOf(cells));
    }

    private static Set<BlockCoord> anchors(
            List<Stalk> left, SpiritReach reach, IntBinaryOperator groundFeet, Predicate<BlockCoord> freeAnchor) {
        Set<BlockCoord> anchors = new LinkedHashSet<>();
        for (Stalk stalk : left) {
            for (int dx = -reach.horizontal(); dx <= reach.horizontal(); dx++) {
                for (int dz = -reach.horizontal(); dz <= reach.horizontal(); dz++) {
                    if (dx == 0 && dz == 0) {
                        continue;
                    }
                    int x = stalk.x() + dx;
                    int z = stalk.z() + dz;
                    BlockCoord anchor = new BlockCoord(x, groundFeet.applyAsInt(x, z), z);
                    if (freeAnchor.test(anchor)) {
                        anchors.add(anchor);
                    }
                }
            }
        }
        return anchors;
    }
}
