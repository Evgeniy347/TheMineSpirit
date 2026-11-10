package com.theminespirit.core.mining;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class ClearScaffoldWorld {
    record Pillar(int x, int z, int height) {
        List<BlockCoord> cells() {
            List<BlockCoord> cells = new ArrayList<>();
            for (int y = 1; y <= height; y++) {
                cells.add(new BlockCoord(x, y, z));
            }
            return cells;
        }

        int rise() {
            return height;
        }
    }

    static final class Spirit {
        final Pillar pillar;
        final Set<Integer> columns = new HashSet<>();
        final List<BlockCoord> targets;
        final Set<BlockCoord> scaffold = new HashSet<>();
        BlockCoord feet;
        int climbs;
        int works;

        Spirit(Pillar pillar) {
            this.pillar = pillar;
            this.targets = new ArrayList<>(pillar.cells());
            this.feet = new BlockCoord(pillar.x() + 1, 0, pillar.z());
        }

        boolean done() {
            return targets.isEmpty() && scaffold.isEmpty();
        }

        void act(Set<BlockCoord> world) {
            ClearScaffold.Decision decision = ClearScaffold.decide(
                    feet,
                    ClearScaffold.highest(targets),
                    3,
                    scaffold.contains(new BlockCoord(feet.x(), feet.y() - 1, feet.z())));
            switch (decision.action()) {
                case CLIMB -> {
                    climbs++;
                    assertTrue(scaffold.isEmpty(), "Подмости ставятся с земли, а не поверх прошлых: " + scaffold);
                    for (int y = feet.y(); y < feet.y() + decision.height(); y++) {
                        BlockCoord cell = new BlockCoord(feet.x(), y, feet.z());
                        assertTrue(world.add(cell), "Клетка подмостей " + cell + " уже занята другим духом");
                        scaffold.add(cell);
                    }
                    columns.add(feet.x() * 100_000 + feet.z());
                    feet = new BlockCoord(feet.x(), feet.y() + decision.height(), feet.z());
                }
                case WORK -> {
                    BlockCoord top = ClearScaffold.highest(targets);
                    assertTrue(
                            top != null && Math.abs(top.y() - feet.y()) <= 3,
                            "С места бьют только достижимую клетку, а не любую: " + top + " при ногах " + feet);
                    targets.remove(top);
                    works++;
                }
                case DESCEND, DONE -> {
                    BlockCoord below = new BlockCoord(feet.x(), feet.y() - 1, feet.z());
                    if (scaffold.remove(below)) {
                        world.remove(below);
                        feet = below;
                    }
                }
            }
        }
    }

    private ClearScaffoldWorld() {}

    static List<Spirit> spirits(List<Pillar> pillars) {
        return pillars.stream().map(Spirit::new).toList();
    }

    static void clearAll(List<Spirit> spirits, Set<BlockCoord> world) {
        int guard = 0;
        while (spirits.stream().anyMatch(spirit -> !spirit.done()) && guard++ < 400) {
            for (Spirit spirit : spirits) {
                if (!spirit.done()) {
                    spirit.act(world);
                }
            }
        }
    }

    static String summary(List<Spirit> spirits) {
        List<String> parts = new ArrayList<>();
        spirits.forEach(spirit -> parts.add(
                spirit.pillar + " climbs=" + spirit.climbs + " works=" + spirit.works + " feet=" + spirit.feet));
        return String.join("; ", parts);
    }
}
