package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FloatingBlocksTests {
    private static final int GROUND = 64;
    private static final Set<String> NATURAL =
            Set.of("minecraft:air", "minecraft:stone", "minecraft:dirt", "minecraft:oak_leaves");

    @Test
    void classify_FenceAboveGround_IsFenceAtHeight() {
        Fake world = new Fake();
        world.put(1, 66, 1, "minecraft:oak_fence");

        FloatingBlocks.Finding finding = world.only(1, 66, 1);

        assertEquals(FloatingBlocks.Kind.FENCE, finding.kind(), "Забор без опоры — забор, а не «прочее»: его снимают");
        assertEquals(1, finding.height(), "Высота считается от верхнего твёрдого блока под клеткой: 66 − 64 − 1");
        assertTrue(
                finding.reason().contains("висит на высоте 1"),
                "Причина называет высоту: владелец видит, насколько объект оторвался от земли");
    }

    @Test
    void classify_FenceOnGround_IsNotReported() {
        Fake world = new Fake();
        world.put(1, 65, 1, "minecraft:oak_fence");

        assertEquals(
                Optional.empty(),
                FloatingBlocks.classify(new BlockCoord(1, 65, 1), world),
                "Забор, стоящий на твёрдом блоке, не висит: его не трогают");
    }

    @Test
    void classify_RegisteredChest_IsRegisteredChest() {
        Fake world = new Fake();
        world.put(2, 67, 2, "minecraft:chest");
        world.registered.add(new BlockCoord(2, 67, 2));

        FloatingBlocks.Finding finding = world.only(2, 67, 2);

        assertEquals(
                FloatingBlocks.Kind.REGISTERED_CHEST, finding.kind(), "Сундук из реестра поселения — отдельный вид");
        assertTrue(
                finding.reason().contains("в реестре"),
                "Причина говорит про реестр: уборка сначала переносит содержимое");
    }

    @Test
    void classify_UnregisteredChest_IsChest() {
        Fake world = new Fake();
        world.put(2, 67, 2, "minecraft:chest");

        assertEquals(FloatingBlocks.Kind.CHEST, world.only(2, 67, 2).kind(), "Сундук вне реестра — обычный сундук");
    }

    @Test
    void classify_FurnaceAboveGround_IsStation() {
        Fake world = new Fake();
        world.put(3, 68, 3, "minecraft:furnace");

        assertEquals(
                FloatingBlocks.Kind.STATION,
                world.only(3, 68, 3).kind(),
                "Печь и верстак — станции: их снимают или переставляют по плану");
    }

    @Test
    void classify_LogWithoutTree_IsStumpEvenOnGround() {
        Fake world = new Fake();
        world.put(4, 65, 4, "minecraft:oak_log");

        FloatingBlocks.Finding finding = world.only(4, 65, 4);

        assertEquals(
                FloatingBlocks.Kind.STUMP,
                finding.kind(),
                "Бревно без дерева — пенёк: его рубят, даже если он стоит на земле");
        assertTrue(finding.reason().contains("бревно без дерева"), "Причина пенька называет его прямо");
    }

    @Test
    void classify_LogInTree_IsNotReported() {
        Fake world = new Fake();
        world.put(4, 65, 4, "minecraft:oak_log");
        world.trees.add(new BlockCoord(4, 65, 4));

        assertEquals(
                Optional.empty(),
                FloatingBlocks.classify(new BlockCoord(4, 65, 4), world),
                "Бревно живого дерева — не пенёк: рубка деревьев остаётся своему заданию");
    }

    @Test
    void classify_LeafCloudWithoutLogs_IsFloatingTree() {
        Fake world = new Fake();
        world.put(9, 70, 9, "minecraft:oak_leaves");
        world.put(10, 70, 9, "minecraft:oak_leaves");

        FloatingBlocks.Finding finding = world.only(9, 70, 9);

        assertEquals(
                FloatingBlocks.Kind.TREE,
                finding.kind(),
                "Связная листва без брёвен и без опоры — остаток кроны: он обязан попасть в уборку, а не в OTHER");
    }

    @Test
    void classify_PlannedFence_IsNotReported() {
        Fake world = new Fake();
        world.put(5, 69, 5, "minecraft:oak_fence");
        world.planned.add(new BlockCoord(5, 69, 5));

        assertEquals(
                Optional.empty(),
                FloatingBlocks.classify(new BlockCoord(5, 69, 5), world),
                "Блок схемы постройки — часть плана: аудит его не трогает");
    }

    @Test
    void classify_NaturalStone_IsNotReported() {
        Fake world = new Fake();
        world.put(6, 72, 6, "minecraft:stone");

        assertEquals(
                Optional.empty(),
                FloatingBlocks.classify(new BlockCoord(6, 72, 6), world),
                "Природный блок без опоры — не искусственный объект, уборка его не касается");
    }

    @Test
    void classify_OtherArtificialBlock_IsOther() {
        Fake world = new Fake();
        world.put(7, 70, 7, "minecraft:oak_planks");

        assertEquals(
                FloatingBlocks.Kind.OTHER,
                world.only(7, 70, 7).kind(),
                "Искусственный блок без своего вида — «прочее»: его тоже снимают");
    }

    @Test
    void find_AreaOnly_FindsObjectsInsideColumns() {
        Fake world = new Fake();
        world.put(1, 66, 1, "minecraft:oak_fence");
        world.put(9, 66, 9, "minecraft:oak_fence");

        List<FloatingBlocks.Finding> found =
                FloatingBlocks.find(ColumnMask.rect(0, 0, 2, 2), GROUND + 1, GROUND + 5, world);

        assertEquals(1, found.size(), "Сканируются только колонки участка: чужой забор за границей не в списке");
        assertEquals(new BlockCoord(1, 66, 1), found.get(0).at(), "Находка называет точные координаты");
    }

    private static final class Fake implements FloatingBlocks.World {
        private final Map<BlockCoord, String> blocks = new HashMap<>();
        private final Set<BlockCoord> planned = new HashSet<>();
        private final Set<BlockCoord> trees = new HashSet<>();
        private final Set<BlockCoord> registered = new HashSet<>();

        void put(int x, int y, int z, String id) {
            blocks.put(new BlockCoord(x, y, z), id);
        }

        FloatingBlocks.Finding only(int x, int y, int z) {
            return FloatingBlocks.classify(new BlockCoord(x, y, z), this).orElseThrow();
        }

        @Override
        public String blockId(BlockCoord at) {
            return blocks.getOrDefault(at, "minecraft:air");
        }

        @Override
        public boolean natural(String blockId) {
            return NATURAL.contains(blockId);
        }

        @Override
        public boolean planned(BlockCoord at) {
            return planned.contains(at);
        }

        @Override
        public boolean log(BlockCoord at) {
            return blockId(at).endsWith("_log");
        }

        @Override
        public boolean treeLog(BlockCoord at) {
            return trees.contains(at);
        }

        @Override
        public boolean supported(BlockCoord at) {
            return at.y() == GROUND + 1;
        }

        @Override
        public boolean registeredChest(BlockCoord at) {
            return registered.contains(at);
        }

        @Override
        public int groundY(int x, int z) {
            return GROUND;
        }
    }
}
