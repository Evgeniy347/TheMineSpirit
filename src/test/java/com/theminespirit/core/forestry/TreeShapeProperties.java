package com.theminespirit.core.forestry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

class TreeShapeProperties {
    @Property(tries = 200)
    void detect_AnyConnectedComponentFromRandomStart_ReturnsWholeComponentExactlyOnce(
            @ForAll @IntRange(min = 1, max = 40) int size, @ForAll long seed) {
        Random random = new Random(seed);
        Set<BlockCoord> component = randomComponent(random, size);
        TreeGrid grid = gridFor(component);
        List<BlockCoord> members = new ArrayList<>(component);
        BlockCoord start = members.get(random.nextInt(members.size()));

        Tree tree = assertInstanceOf(Tree.class, TreeShape.detect(start, grid),
                "Разбор обязана давать одно и то же дерево, с какого бы бревна его ни начали: стартовое бревно — "
                        + "ближайшее к духу, это может быть конец ветви акации, и дерево не должно становиться TOO_BIG "
                        + "из-за выбора начала обхода");
        assertEquals(Set.copyOf(component), Set.copyOf(tree.logs()),
                "Разбор обязан вернуть всю компоненту связности: пропущенное или лишнее бревно даёт "
                        + "неправильный план рубки");
        assertEquals(tree.logs().size(), Set.copyOf(tree.logs()).size(),
                "Каждое бревно обязано попасть в дерево один раз: дубль приведёт к повторной ломке одного блока");
        for (int i = 1; i < tree.logs().size(); i++) {
            assertTrue(tree.logs().get(i - 1).y() <= tree.logs().get(i).y(),
                    "Порядок брёвен обязан не убывать по y: рубка идёт снизу вверх, а нарушение порядка "
                            + "ломки заставляет духа прыгать по стволу вверх-вниз");
        }
        int neighbours = nonLogNeighbours(component);
        assertTrue(grid.logReads() <= component.size() + neighbours,
                "Разбор одного дерева обязан читать isLog не больше одного раза на клетку: без множества "
                        + "посещённых обход читает соседей каждого бревна повторно, и на плотных группах число "
                        + "чтений перерастает размер группы с её соседями");
        assertTrue(grid.leavesReads() <= neighbours,
                "Разбор одного дерева обязан вызывать isNaturalLeaves только для различных неповторяющихся "
                        + "соседей-не-брёвен: повторный вызов на одной клетке — симптом повторного обхода, который "
                        + "на 128 брёвен разрастается до тысяч чтений за тик");
    }

    private static Set<BlockCoord> randomComponent(Random random, int size) {
        Set<BlockCoord> component = new LinkedHashSet<>();
        List<BlockCoord> members = new ArrayList<>();
        int canopyY = 1 + random.nextInt(3);
        for (int x = -ChopTuning.MAX_SPREAD; x <= ChopTuning.MAX_SPREAD; x++) {
            BlockCoord cell = new BlockCoord(x, canopyY, 0);
            component.add(cell);
            members.add(cell);
        }
        for (int y = 0; y <= canopyY; y++) {
            BlockCoord cell = new BlockCoord(0, y, 0);
            component.add(cell);
            members.add(cell);
        }
        int attempts = 0;
        while (component.size() < size && attempts < 100_000) {
            attempts++;
            BlockCoord from = members.get(random.nextInt(members.size()));
            BlockCoord candidate = randomNeighbour(random, from);
            if (candidate.y() <= 0) {
                continue;
            }
            if (Math.abs(candidate.x()) > ChopTuning.MAX_SPREAD
                    || Math.abs(candidate.z()) > ChopTuning.MAX_SPREAD) {
                continue;
            }
            if (component.add(candidate)) {
                members.add(candidate);
            }
        }
        return component;
    }

    private static BlockCoord randomNeighbour(Random random, BlockCoord from) {
        while (true) {
            int dx = random.nextInt(3) - 1;
            int dy = random.nextInt(3) - 1;
            int dz = random.nextInt(3) - 1;
            if (dx != 0 || dy != 0 || dz != 0) {
                return new BlockCoord(from.x() + dx, from.y() + dy, from.z() + dz);
            }
        }
    }

    private static TreeGrid gridFor(Set<BlockCoord> component) {
        TreeGrid grid = new TreeGrid();
        Set<BlockCoord> soil = new HashSet<>();
        for (BlockCoord coord : component) {
            grid.log(coord.x(), coord.y(), coord.z());
        }
        int baseY = component.stream().mapToInt(BlockCoord::y).min().orElseThrow();
        for (BlockCoord coord : component) {
            if (coord.y() == baseY && soil.add(new BlockCoord(coord.x(), coord.y() - 1, coord.z()))) {
                grid.soil(coord.x(), coord.y() - 1, coord.z());
            }
        }
        Set<BlockCoord> leaves = new HashSet<>();
        for (BlockCoord coord : component) {
            for (BlockCoord neighbour : neighbours(coord)) {
                if (leaves.size() >= ChopTuning.MIN_NATURAL_LEAVES) {
                    break;
                }
                if (!component.contains(neighbour) && !soil.contains(neighbour) && leaves.add(neighbour)) {
                    grid.leaves(neighbour.x(), neighbour.y(), neighbour.z());
                }
            }
        }
        return grid;
    }

    private static int nonLogNeighbours(Set<BlockCoord> component) {
        Set<BlockCoord> neighbours = new HashSet<>();
        for (BlockCoord coord : component) {
            for (BlockCoord neighbour : neighbours(coord)) {
                if (!component.contains(neighbour)) {
                    neighbours.add(neighbour);
                }
            }
        }
        return neighbours.size();
    }

    private static List<BlockCoord> neighbours(BlockCoord coord) {
        List<BlockCoord> result = new ArrayList<>(26);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx != 0 || dy != 0 || dz != 0) {
                        result.add(new BlockCoord(coord.x() + dx, coord.y() + dy, coord.z() + dz));
                    }
                }
            }
        }
        return result;
    }
}