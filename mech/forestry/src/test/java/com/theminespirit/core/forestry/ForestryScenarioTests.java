package com.theminespirit.core.forestry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theminespirit.core.forestry.api.TreeView;
import com.theminespirit.core.observation.BlockCoord;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ForestryScenarioTests {
    static final int TRUNK_HEIGHT = 4;
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
                "Три духа обязаны разобрать три разных дерева, а не встать в очередь к одному: " + assigned
                        + ", иначе два духа рубят одно, третье стоит. При смене assign проверь строку ниже в тесте");

        for (Map.Entry<BlockCoord, BlockCoord> entry : assigned.entrySet()) {
            BlockCoord spirit = entry.getKey();
            BlockCoord base = entry.getValue();
            Tree tree = assertTree(base, world);
            chopAndReplant(tree, spirit, world);
        }

        assertEquals(
                Set.of(),
                world.logs(),
                "После рубки в лесу не осталось брёвен: " + world.logs() + ", иначе дерево недорубано. При смене "
                        + "chopAndReplant проверь строку ствола ниже в тесте");
        for (BlockCoord base : bases) {
            for (int y = base.y(); y < base.y() + TRUNK_HEIGHT; y++) {
                assertFalse(
                        world.isLog(new BlockCoord(base.x(), y, base.z())),
                        "Ствол " + base + " не дорублен, иначе пенёк висит и рубка считается незавершённой. При "
                                + "смене chopAndReplant проверь строку листвы ниже в тесте");
            }
            for (BlockCoord cell : leafCells(base)) {
                assertFalse(
                        world.isNaturalLeaves(cell),
                        "Листва " + cell + " осталась висеть после рубки: дух обязан её убрать, иначе крона висит над "
                                + "пнём. При смене группового сноса проверь строку почвы ниже в тесте");
            }
            assertTrue(
                    world.isSoil(new BlockCoord(base.x(), GROUND, base.z())),
                    "Почва под стволом " + base + " цела: рубка не выкапывает дёрн, иначе участок остаётся без земли "
                            + "и досадка невозможна. При смене сноса проверь строку постройки ниже в тесте");
        }
        assertTrue(
                world.isArtificial(new BlockCoord(28, 1, 0)),
                "Постройка рядом с лесом цела: чужой блок не срублен вместе с деревом, иначе дух рушит постройку. "
                        + "При смене SnapshotView проверь строку листвы выше в тесте");
    }

    static List<BlockCoord> cells(Map<BlockCoord, String> blocks, String species) {
        List<BlockCoord> cells = new ArrayList<>();
        blocks.forEach((cell, block) -> {
            if (("minecraft:" + species + "_log").equals(block)) {
                cells.add(cell);
            }
        });
        return cells;
    }

    static Map<BlockCoord, String> blocks(Path path) throws IOException {
        JsonNode incident = new ObjectMapper().readTree(Files.readString(path, StandardCharsets.UTF_8));
        Map<BlockCoord, String> blocks = new HashMap<>();
        for (JsonNode entry : incident.get("blocks")) {
            blocks.put(
                    new BlockCoord(
                            entry.get("x").asInt(),
                            entry.get("y").asInt(),
                            entry.get("z").asInt()),
                    entry.get("block").asText());
        }
        return blocks;
    }

    record SnapshotView(Map<BlockCoord, String> blocks, String species) implements TreeView {
        @Override
        public boolean isLog(BlockCoord coord) {
            return ("minecraft:" + species + "_log").equals(blocks.get(coord));
        }

        @Override
        public boolean isNaturalLeaves(BlockCoord coord) {
            String block = blocks.get(coord);
            return block != null && block.endsWith("_leaves");
        }

        @Override
        public boolean isFoliageObstacle(BlockCoord coord) {
            String block = blocks.get(coord);
            return block != null && (block.endsWith("_leaves") || block.endsWith(":vine") || block.endsWith("_vines"));
        }

        @Override
        public boolean isSoil(BlockCoord coord) {
            String block = blocks.get(coord);
            return block != null
                    && (block.endsWith("_dirt")
                            || "minecraft:dirt".equals(block)
                            || "minecraft:grass_block".equals(block));
        }

        @Override
        public boolean isArtificial(BlockCoord coord) {
            String block = blocks.get(coord);
            return block != null
                    && (block.endsWith("_planks")
                            || block.endsWith("_fence")
                            || block.endsWith("_stairs")
                            || block.endsWith("_door")
                            || block.endsWith("_torch")
                            || "minecraft:crafting_table".equals(block));
        }

        @Override
        public boolean isEmpty(BlockCoord coord) {
            String block = blocks.get(coord);
            return block == null
                    || "minecraft:air".equals(block)
                    || "minecraft:short_grass".equals(block)
                    || "minecraft:fern".equals(block);
        }
    }

    private static Map<BlockCoord, BlockCoord> assign(List<BlockCoord> spirits, List<BlockCoord> bases) {
        Map<BlockCoord, BlockCoord> assigned = new LinkedHashMap<>();
        Set<BlockCoord> claimed = new HashSet<>();
        for (BlockCoord spirit : spirits) {
            Optional<BlockCoord> next = TreeChooser.next(spirit.x(), spirit.y(), spirit.z(), bases, claimed);
            assertTrue(
                    next.isPresent(),
                    "Дух " + spirit + " без дерева: на доске три свободных ствола, иначе задание висит без цели. "
                            + "При смене assign проверь строку claim ниже в тесте");
            assertTrue(
                    claimed.add(next.orElseThrow()),
                    "Два духа выбрали одно дерево, иначе один ствол рубится дважды и второй дух ждёт: claimed "
                            + "обязан исключать занятое дерево. При смене TreeChooser.next проверь строку выше в "
                            + "тесте");
            assigned.put(spirit, next.orElseThrow());
        }
        return assigned;
    }

    static void chopAndReplant(Tree tree, BlockCoord spirit, TreeGrid world) {
        List<BlockCoord> clearing = LeafObstacles.blockingStand(tree, world, spirit);
        clearing.forEach(cell -> world.air(cell.x(), cell.y(), cell.z()));

        List<BlockCoord> stands = ChopStands.stands(tree, feet -> standable(world, clearing, feet));
        assertFalse(
                stands.isEmpty(),
                "У дерева " + tree.base() + " нет стоянки: дух не начнёт рубку, хотя дерево найдено, иначе задание "
                        + "висит. При смене ChopStands.stands проверь строку группы ниже в тесте");

        TreeGroup group = TreeGroup.of(tree.base(), world)
                .orElseThrow(() -> new AssertionError("Дерево " + tree.base() + " не собралось в группу"));
        ChopPlan plan = ChopPlan.of(tree, spirit);

        assertEquals(
                tree.logs().stream().mapToInt(BlockCoord::y).max().orElseThrow(),
                plan.order().get(0).y(),
                "Рубка начинается с верхнего бревна, иначе ствол падает на духа. При смене ChopPlan.of проверь "
                        + "строку порядка ниже в тесте");
        int previous = Integer.MAX_VALUE;
        for (BlockCoord log : plan.order()) {
            assertTrue(
                    world.isLog(log),
                    "План рубит уже убранное бревно, иначе дух бьёт воздух и задание висит до тайм-аута. При смене "
                            + "chopAndReplant проверь строку порядка ниже в тесте");
            assertTrue(
                    log.y() <= previous,
                    "Бревно " + log + " идёт после более низкого: порядок сверху вниз, иначе верхнее падает на духа "
                            + "(INC-49). При смене ChopPlan.of проверь строку выше в тесте");
            previous = log.y();
            world.air(log.x(), log.y(), log.z());
        }
        group.leaves().forEach(cell -> world.air(cell.x(), cell.y(), cell.z()));

        Set<BlockCoord> excluded = new HashSet<>(group.logs());
        excluded.addAll(group.leaves());
        List<BlockCoord> saplings = AreaReplant.plan(tree.base(), excluded, ReplantData.defaults(), world);
        assertTrue(
                saplings.size() >= ReplantData.defaults().squareSaplings(),
                "После рубки " + tree.base() + " не хватает саженцев: " + saplings.size() + ", досадка обязана "
                        + "продолжить лес, иначе участок пустеет. При смене AreaReplant.plan проверь строку почвы "
                        + "ниже в тесте");
        for (BlockCoord sapling : saplings) {
            assertTrue(
                    world.isSoil(new BlockCoord(sapling.x(), sapling.y() - 1, sapling.z())),
                    "Саженец " + sapling + " стоит не на почве, а на "
                            + world.getClass().getSimpleName()
                            + ", иначе не прорастёт и саженец пропадёт. При смене AreaReplant.plan проверь строку клетки ниже в тесте");
            assertFalse(
                    group.logs().contains(sapling) || group.leaves().contains(sapling),
                    "Саженец " + sapling + " посажен в клетку ствола или кроны: там ещё дерево, иначе запас "
                            + "тратится впустую. При смене excluded проверь строку выше в тесте");
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

    static Tree assertTree(BlockCoord base, TreeView view) {
        return assertInstanceOf(
                Tree.class,
                TreeShape.detect(base, view),
                "Дерево " + base + " не разобралось: рубка не начнётся, иначе сценарий врёт и тест слеп. При смене "
                        + "TreeShape.detect проверь строку выше в тесте");
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

    static BlockCoord base(int x) {
        return new BlockCoord(x, 1, 2);
    }

    static TreeGrid forest() {
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
