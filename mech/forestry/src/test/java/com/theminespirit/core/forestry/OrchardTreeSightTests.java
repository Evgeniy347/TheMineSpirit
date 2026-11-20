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
                "В саду недосрубленный jungle-ствол (-165,68..74,-438) с кроной через разрыв обязан найтись: без "
                        + "метки tree:jungle граф не планирует сбор саженцев и repair:build_orchard навсегда ждёт "
                        + "«нужно 4, есть 0», иначе сад не восстановить. При смене TreeSight.detect проверь строку "
                        + "размера ниже в тесте. Найдено: " + inOrchard);
        assertEquals(
                1,
                inOrchard.size(),
                "Одиночное бревно y=79 в кроне не сливается со стволом и не даёт второго дерева: " + inOrchard
                        + ", иначе дух считает два дерева и рубит обломок. При смене проверки кроны проверь строку "
                        + "вне сада ниже в тесте");
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
                "Одиночное бревно в кроне сада остаётся обломком и деревом не становится даже с послаблением: дух "
                        + "обязан рубить ствол, а не висеть на кроне. Найдено: " + found + ", иначе обломок "
                        + "считается деревом. При смене послабления проверь строку сада выше в тесте");
    }

    @Test
    void treeShape_OrchardTrunkWithSoil_AcceptedWithoutNoSoilFlag() {
        Map<BlockCoord, String> blocks = orchardBlocks();
        ForestryScenarioTests.SnapshotView view = new ForestryScenarioTests.SnapshotView(blocks, "jungle");

        Tree tree = assertInstanceOf(
                Tree.class,
                TreeShape.detect(BASE, view, false, true),
                "Точная рубка сада зовёт детектор без послабления почвы: ствол сада на земле обязан разобраться как "
                        + "дерево, иначе chop_target harvest_orchard падает «не дерево» на здоровом стволе. При "
                        + "смене флагов TreeShape.detect проверь размер ниже в тесте");
        assertEquals(
                7,
                tree.logs().size(),
                "Ствол сада — семь брёвен; бревно кроны выше разрыва не сосед: " + tree.logs() + ", иначе крона "
                        + "прирастает к стволу. При смене проверки соседства проверь строку голого ствола ниже в "
                        + "тесте");
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
                "Голый ствол сада без листвы — дерево: прошлая рубка сняла крону, столб обязан дочиститься, иначе "
                        + "недосруб остаётся навсегда. При смене флага листвы проверь размер ниже в тесте");
        assertEquals(
                7,
                tree.logs().size(),
                "Ствол сада — семь брёвен и без кроны: " + tree.logs() + ", иначе листва добавляет клетки. При "
                        + "смене TreeShape.detect проверь строку вне сада ниже в тесте");
        assertInstanceOf(
                NotATree.class,
                TreeShape.detect(BASE, view, false, false),
                "Вне сада тот же голый ствол отвергается: послабление листвы — только для территории сада, иначе "
                        + "голый столб рубится в лесу. При смене флага разрешения проверь строку сада выше в тесте");
    }

    @Test
    void treeShape_OrchardTrunkBesideFence_AcceptedOnlyInOrchard() {
        Map<BlockCoord, String> blocks = orchardBlocks();
        blocks.put(new BlockCoord(BASE.x(), BASE.y(), BASE.z() + 1), "minecraft:jungle_fence");
        ForestryScenarioTests.SnapshotView view = new ForestryScenarioTests.SnapshotView(blocks, "jungle");

        assertInstanceOf(
                Tree.class,
                TreeShape.detect(BASE, view, false, true),
                "Ствол сада вплотную к ограде — дерево: правило «рядом постройка» в саду не работает, иначе деревья "
                        + "вдоль забора не рубятся никогда. При смене проверки построек проверь строку вне сада ниже "
                        + "в тесте");
        assertInstanceOf(
                NotATree.class,
                TreeShape.detect(BASE, view, false, false),
                "Вне сада та же картина остаётся постройкой: послабление не открывает снос чужих стен, иначе рубка "
                        + "сада ломает заборы. При смене флага сада проверь строку выше в тесте");
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
