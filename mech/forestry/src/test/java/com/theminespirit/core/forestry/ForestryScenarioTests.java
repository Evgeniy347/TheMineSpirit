package com.theminespirit.core.forestry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ForestryScenarioTests {
    private static final int TRUNK_HEIGHT = 4;
    private static final int GROUND = 0;

    @Test
    void forest_ThreeSpiritsThreeTrees_EachChopsOwnTreeAndReplantsWithoutLeftovers() {
        TreeGrid world = forest();
        List<BlockCoord> bases = List.of(base(2), base(12), base(22));
        List<BlockCoord> spirits = List.of(new BlockCoord(2, 1, 3), new BlockCoord(12, 1, 3), new BlockCoord(22, 1, 3));

        Map<BlockCoord, BlockCoord> assigned = assign(spirits, bases);
        assertEquals(
                new HashSet<>(bases),
                new HashSet<>(assigned.values()),
                "Три духа обязаны разобрать три разных дерева, а не встать в очередь к одному: " + assigned);

        for (Map.Entry<BlockCoord, BlockCoord> entry : assigned.entrySet()) {
            BlockCoord spirit = entry.getKey();
            BlockCoord base = entry.getValue();
            Tree tree = assertTree(base, world);
            chopAndReplant(tree, spirit, world);
        }

        assertEquals(Set.of(), world.logs(), "После рубки в лесу не осталось брёвен: " + world.logs());
        for (BlockCoord base : bases) {
            for (int y = base.y(); y < base.y() + TRUNK_HEIGHT; y++) {
                assertFalse(world.isLog(new BlockCoord(base.x(), y, base.z())), "Ствол " + base + " не дорублен");
            }
            for (BlockCoord cell : leafCells(base)) {
                assertFalse(
                        world.isNaturalLeaves(cell),
                        "Листва " + cell + " осталась висеть после рубки: дух обязан её убрать");
            }
            assertTrue(
                    world.isSoil(new BlockCoord(base.x(), GROUND, base.z())),
                    "Почва под стволом " + base + " цела: рубка не выкапывает дёрн");
        }
        assertTrue(
                world.isArtificial(new BlockCoord(28, 1, 0)),
                "Постройка рядом с лесом цела: чужой блок не срублен вместе с деревом");
    }

    @Test
    void forest_TwoTreesCloseTwoSpirits_NearestTreeIsClaimedByEachAndNotTwice() {
        TreeGrid world = forest();
        List<BlockCoord> bases = List.of(base(2), base(12), base(22));
        BlockCoord first = new BlockCoord(3, 1, 2);
        BlockCoord second = new BlockCoord(11, 1, 2);

        Optional<BlockCoord> firstTree = TreeChooser.next(first.x(), first.y(), first.z(), bases, Set.of());
        Set<BlockCoord> claimed = new HashSet<>(firstTree.stream().toList());
        Optional<BlockCoord> secondTree = TreeChooser.next(second.x(), second.y(), second.z(), bases, claimed);

        assertEquals(Optional.of(base(2)), firstTree, "Дух у ближнего дерева берёт его, а не дальнее");
        assertEquals(
                Optional.of(base(12)),
                secondTree,
                "Второй дух не претендует на уже выбранное дерево и берёт следующее ближнее: первый " + firstTree
                        + ", второй " + secondTree);
        assertFalse(
                claimed.contains(base(22)),
                "Третье дерево остаётся свободным: два духа не разбирают весь лес, доска даст его другому");

        chopAndReplant(assertTree(firstTree.orElseThrow(), world), first, world);
        chopAndReplant(assertTree(secondTree.orElseThrow(), world), second, world);

        assertTrue(world.isLog(base(22)), "Невыбранное дерево не тронуто: его ствол обязан остаться на месте");
        assertTrue(
                world.isLog(new BlockCoord(base(22).x(), base(22).y() + TRUNK_HEIGHT - 1, base(22).z())),
                "Невыбранное дерево осталось целым до своей задачи");
    }

    private static Map<BlockCoord, BlockCoord> assign(List<BlockCoord> spirits, List<BlockCoord> bases) {
        Map<BlockCoord, BlockCoord> assigned = new LinkedHashMap<>();
        Set<BlockCoord> claimed = new HashSet<>();
        for (BlockCoord spirit : spirits) {
            Optional<BlockCoord> next = TreeChooser.next(spirit.x(), spirit.y(), spirit.z(), bases, claimed);
            assertTrue(next.isPresent(), "Дух " + spirit + " без дерева: на доске три свободных ствола");
            assertTrue(claimed.add(next.orElseThrow()), "Два духа выбрали одно дерево " + next);
            assigned.put(spirit, next.orElseThrow());
        }
        return assigned;
    }

    private static void chopAndReplant(Tree tree, BlockCoord spirit, TreeGrid world) {
        List<BlockCoord> clearing = LeafObstacles.blockingStand(tree, world, spirit);
        clearing.forEach(cell -> world.air(cell.x(), cell.y(), cell.z()));

        List<BlockCoord> stands = ChopStands.stands(tree, feet -> standable(world, clearing, feet));
        assertFalse(
                stands.isEmpty(), "У дерева " + tree.base() + " нет стоянки: дух не начнёт рубку, хотя дерево найдено");

        TreeGroup group = TreeGroup.of(tree.base(), world)
                .orElseThrow(() -> new AssertionError("Дерево " + tree.base() + " не собралось в группу"));
        ChopPlan plan = ChopPlan.of(tree, spirit);

        assertEquals(
                tree.logs().stream().mapToInt(BlockCoord::y).max().orElseThrow(),
                plan.order().get(0).y(),
                "Рубка начинается с верхнего бревна: иначе ствол падает на духа");
        int previous = Integer.MAX_VALUE;
        for (BlockCoord log : plan.order()) {
            assertTrue(world.isLog(log), "План рубит уже убранное бревно " + log);
            assertTrue(log.y() <= previous, "Бревно " + log + " идёт после более низкого: порядок снизу вверх");
            previous = log.y();
            world.air(log.x(), log.y(), log.z());
        }
        group.leaves().forEach(cell -> world.air(cell.x(), cell.y(), cell.z()));

        Set<BlockCoord> excluded = new HashSet<>(group.logs());
        excluded.addAll(group.leaves());
        List<BlockCoord> saplings = AreaReplant.plan(tree.base(), excluded, ReplantData.defaults(), world);
        assertTrue(
                saplings.size() >= ReplantData.defaults().squareSaplings(),
                "После рубки " + tree.base() + " не хватает саженцев: " + saplings.size()
                        + ", досадка обязана продолжить лес");
        for (BlockCoord sapling : saplings) {
            assertTrue(
                    world.isSoil(new BlockCoord(sapling.x(), sapling.y() - 1, sapling.z())),
                    "Саженец " + sapling + " стоит не на почве, а на "
                            + world.getClass().getSimpleName());
            assertFalse(
                    group.logs().contains(sapling) || group.leaves().contains(sapling),
                    "Саженец " + sapling + " посажен в клетку ствола или кроны: там ещё дерево");
        }
    }

    private static boolean standable(TreeGrid world, List<BlockCoord> cleared, BlockCoord feet) {
        BlockCoord head = new BlockCoord(feet.x(), feet.y() + 1, feet.z());
        BlockCoord below = new BlockCoord(feet.x(), feet.y() - 1, feet.z());
        return free(world, cleared, feet) && free(world, cleared, head) && world.isSoil(below);
    }

    private static boolean free(TreeGrid world, List<BlockCoord> cleared, BlockCoord cell) {
        return cleared.contains(cell) || world.isEmpty(cell);
    }

    private static Tree assertTree(BlockCoord base, TreeView view) {
        return assertInstanceOf(
                Tree.class, TreeShape.detect(base, view), "Дерево " + base + " не разобралось: рубка не начнётся");
    }

    private static List<BlockCoord> leafCells(BlockCoord base) {
        List<BlockCoord> leaves = new ArrayList<>();
        leaves.add(new BlockCoord(base.x() - 1, base.y() + 1, base.z()));
        leaves.add(new BlockCoord(base.x() + 1, base.y() + 1, base.z()));
        leaves.add(new BlockCoord(base.x(), base.y() + 2, base.z() - 1));
        leaves.add(new BlockCoord(base.x(), base.y() + 2, base.z() + 1));
        leaves.add(new BlockCoord(base.x(), base.y() + 3, base.z() - 1));
        leaves.add(new BlockCoord(base.x() + 1, base.y() + 3, base.z()));
        return leaves;
    }

    private static BlockCoord base(int x) {
        return new BlockCoord(x, 1, 2);
    }

    private static TreeGrid forest() {
        TreeGrid world = new TreeGrid();
        for (int x = 0; x <= 28; x++) {
            for (int z = 0; z <= 5; z++) {
                world.soil(x, GROUND, z);
            }
        }
        for (int baseX : List.of(2, 12, 22)) {
            for (int y = 1; y <= TRUNK_HEIGHT; y++) {
                world.log(baseX, y, 2);
            }
            leafCells(base(baseX)).forEach(cell -> world.leaves(cell.x(), cell.y(), cell.z()));
        }
        world.planks(28, 1, 0);
        return world;
    }
}
