package com.theminespirit.core.forestry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ForestryWorldTests {
    @Test
    void forest_TwoTreesCloseTwoSpirits_NearestTreeIsClaimedByEachAndNotTwice() {
        TreeGrid world = ForestryScenarioTests.forest();
        List<BlockCoord> bases =
                List.of(ForestryScenarioTests.base(2), ForestryScenarioTests.base(12), ForestryScenarioTests.base(22));
        BlockCoord first = new BlockCoord(3, 1, 2);
        BlockCoord second = new BlockCoord(11, 1, 2);

        Optional<BlockCoord> firstTree = TreeChooser.next(first.x(), first.y(), first.z(), bases, Set.of());
        Set<BlockCoord> claimed = new HashSet<>(firstTree.stream().toList());
        Optional<BlockCoord> secondTree = TreeChooser.next(second.x(), second.y(), second.z(), bases, claimed);

        assertEquals(
                Optional.of(ForestryScenarioTests.base(2)),
                firstTree,
                "Дух у ближнего дерева берёт его, а не дальнее, иначе он идёт через лес. При смене TreeChooser.next "
                        + "проверь строку второго ниже в тесте");
        assertEquals(
                Optional.of(ForestryScenarioTests.base(12)),
                secondTree,
                "Второй дух не претендует на уже выбранное дерево и берёт следующее ближнее: первый " + firstTree
                        + ", второй " + secondTree + ", иначе духи претендуют на одно. При смене claimed проверь "
                        + "строку ниже в тесте");
        assertFalse(
                claimed.contains(ForestryScenarioTests.base(22)),
                "Третье дерево остаётся свободным: два духа не разбирают весь лес, доска даст его другому, иначе "
                        + "лес вырубается двумя задачами. При смене assign проверь строку выше в тесте");

        ForestryScenarioTests.chopAndReplant(
                ForestryScenarioTests.assertTree(firstTree.orElseThrow(), world), first, world);
        ForestryScenarioTests.chopAndReplant(
                ForestryScenarioTests.assertTree(secondTree.orElseThrow(), world), second, world);

        assertTrue(
                world.isLog(ForestryScenarioTests.base(22)),
                "Невыбранное дерево не тронуто: его ствол обязан остаться на месте, иначе рубка задевает соседа. "
                        + "При смене chopAndReplant проверь строку верхнего ниже в тесте");
        assertTrue(
                world.isLog(new BlockCoord(
                        ForestryScenarioTests.base(22).x(),
                        ForestryScenarioTests.base(22).y() + ForestryScenarioTests.TRUNK_HEIGHT - 1,
                        ForestryScenarioTests.base(22).z())),
                "Невыбранное дерево осталось целым до своей задачи, иначе верх срублен случайно. При смене "
                        + "chopAndReplant проверь строку выше в тесте");
    }

    @Test
    void treeSight_LiveJungleTrunk_IsForest() throws IOException {
        Map<BlockCoord, String> blocks =
                ForestryScenarioTests.blocks(Path.of("src/test/resources/live/observe11-jungle-tree.json"));

        List<TreeSight.Stand> found = TreeSight.detect(
                Map.of("jungle", ForestryScenarioTests.cells(blocks, "jungle")),
                species -> new ForestryScenarioTests.SnapshotView(blocks, species));

        assertTrue(
                found.stream().anyMatch(stand -> new BlockCoord(-203, 70, -565).equals(stand.base())),
                "Живой ствол jungle обязан найтись: метка мира tree:jungle ставится по базе ствола, иначе граф "
                        + "рубит не то место. Найдено: " + found + ". При смене TreeSight.detect проверь строку дома "
                        + "ниже в тесте");
    }

    @Test
    void treeSight_LiveHouseOfJungleLogs_IsNotForest() throws IOException {
        Map<BlockCoord, String> blocks =
                ForestryScenarioTests.blocks(Path.of("src/test/resources/live/woodchain-house-1111.json"));

        List<TreeSight.Stand> found = TreeSight.detect(
                Map.of("jungle", ForestryScenarioTests.cells(blocks, "jungle")),
                species -> new ForestryScenarioTests.SnapshotView(blocks, species));

        assertTrue(
                found.isEmpty(),
                "Постройка поселения из jungle-брёвен не делает породу доступной, иначе граф ставит рубку стен у "
                        + "(-180,70,-505) и повторяет её каждый час. Найдено: " + found + ". При смене "
                        + "TreeSight.detect проверь строку живого ствола выше в тесте");
    }
}
