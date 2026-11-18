package com.theminespirit.core.forestry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OrchardTreeSightTests {
    private static final BlockCoord BASE = new BlockCoord(-165, 68, -438);
    private static final BlockCoord STRAY = new BlockCoord(-165, 79, -438);

    @Test
    void treeSight_OrchardJungleTrunkWithGapCrown_IsForestOnlyInOrchard() {
        Map<BlockCoord, String> blocks = orchardBlocks();
        List<BlockCoord> logs = logs(blocks);

        List<TreeSight.Stand> inOrchard = TreeSight.detect(
                Map.of("jungle", logs),
                species -> new ForestryScenarioTests.SnapshotView(blocks, species),
                cell -> Math.abs(cell.x() + 165) <= 2 && Math.abs(cell.z() + 438) <= 2);
        assertTrue(
                inOrchard.stream().anyMatch(stand -> BASE.equals(stand.base())),
                "В саду недосрубленный jungle-ствол (-165,68..74,-438) с кроной через разрыв обязан найтись: "
                        + "без метки tree:jungle граф не планирует сбор саженцев и repair:build_orchard "
                        + "навсегда ждёт 'нужно 4, есть 0'. Найдено: " + inOrchard);
        assertEquals(
                1,
                inOrchard.size(),
                "Одиночное бревно y=79 в кроне не сливается со стволом и не даёт второго дерева: " + inOrchard);

        List<TreeSight.Stand> outside = TreeSight.detect(
                Map.of("jungle", logs),
                species -> new ForestryScenarioTests.SnapshotView(blocks, species),
                cell -> false);
        assertTrue(
                outside.isEmpty(),
                "Вне сада та же форма остаётся отвергнутой: послабление кроны через разрыв — "
                        + "только для территории сада, иначе рубка леса начинает брать обломки. Найдено: " + outside);
    }

    @Test
    void treeSight_OrchardIsolatedCrownLog_IsNotForest() {
        Map<BlockCoord, String> blocks = new HashMap<>();
        blocks.put(STRAY, "minecraft:jungle_log");
        for (int dy = -1; dy <= 1; dy += 2) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    blocks.put(
                            new BlockCoord(STRAY.x() + dx, STRAY.y() + dy, STRAY.z() + dz), "minecraft:jungle_leaves");
                }
            }
        }

        List<TreeSight.Stand> found = TreeSight.detect(
                Map.of("jungle", List.of(STRAY)),
                species -> new ForestryScenarioTests.SnapshotView(blocks, species),
                cell -> true);
        assertTrue(
                found.isEmpty(),
                "Одиночное бревно в кроне сада остаётся обломком и деревом не становится даже с послаблением: "
                        + "дух обязан рубить ствол, а не висеть на кроне. Найдено: " + found);
    }

    @Test
    void treeShape_OrchardTrunkWithSoil_AcceptedWithoutNoSoilFlag() {
        Map<BlockCoord, String> blocks = orchardBlocks();
        ForestryScenarioTests.SnapshotView view = new ForestryScenarioTests.SnapshotView(blocks, "jungle");

        Tree tree = assertInstanceOf(
                Tree.class,
                TreeShape.detect(BASE, view, false, true),
                "Точная рубка сада зовёт детектор без послабления почвы: ствол сада на земле обязан разобраться "
                        + "как дерево, иначе chop_target harvest_orchard падает «не дерево» на здоровом стволе");
        assertEquals(
                7, tree.logs().size(), "Ствол сада — семь брёвен; бревно кроны выше разрыва не сосед: " + tree.logs());
    }

    @Test
    void treeShape_OrchardBareTrunk_AcceptedWithoutLeaves() {
        Map<BlockCoord, String> blocks = new HashMap<>();
        for (int y = 68; y <= 74; y++) {
            blocks.put(new BlockCoord(BASE.x(), y, BASE.z()), "minecraft:jungle_log");
        }
        blocks.put(new BlockCoord(BASE.x(), BASE.y() - 1, BASE.z()), "minecraft:dirt");
        ForestryScenarioTests.SnapshotView view = new ForestryScenarioTests.SnapshotView(blocks, "jungle");

        Tree tree = assertInstanceOf(
                Tree.class,
                TreeShape.detect(BASE, view, false, true),
                "Голый ствол сада без листвы — дерево: прошлая рубка сняла крону, столб обязан дочиститься");
        assertEquals(7, tree.logs().size(), "Ствол сада — семь брёвен и без кроны: " + tree.logs());
        assertInstanceOf(
                NotATree.class,
                TreeShape.detect(BASE, view, false, false),
                "Вне сада тот же голый ствол отвергается: послабление листвы — только для территории сада");
    }

    private static Map<BlockCoord, String> orchardBlocks() {
        Map<BlockCoord, String> blocks = new HashMap<>();
        for (int y = 68; y <= 74; y++) {
            blocks.put(new BlockCoord(BASE.x(), y, BASE.z()), "minecraft:jungle_log");
        }
        blocks.put(STRAY, "minecraft:jungle_log");
        blocks.put(new BlockCoord(BASE.x(), BASE.y() - 1, BASE.z()), "minecraft:dirt");
        for (int dy = 3; dy <= 6; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    blocks.put(new BlockCoord(BASE.x() + dx, 74 + dy, BASE.z() + dz), "minecraft:jungle_leaves");
                }
            }
        }
        return blocks;
    }

    private static List<BlockCoord> logs(Map<BlockCoord, String> blocks) {
        List<BlockCoord> cells = new ArrayList<>();
        blocks.forEach((cell, block) -> {
            if ("minecraft:jungle_log".equals(block)) {
                cells.add(cell);
            }
        });
        return cells;
    }
}
