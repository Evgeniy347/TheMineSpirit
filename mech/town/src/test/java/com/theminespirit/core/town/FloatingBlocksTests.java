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
    static final int GROUND = 64;
    static final Set<String> NATURAL =
            Set.of("minecraft:air", "minecraft:stone", "minecraft:dirt", "minecraft:oak_leaves");

    @Test
    void classify_FenceAboveGround_IsFenceAtHeight() {
        Fake world = new Fake();
        world.put(1, 66, 1, "minecraft:oak_fence");

        FloatingBlocks.Finding finding = world.only(1, 66, 1);

        assertEquals(
                FloatingBlocks.Kind.FENCE,
                finding.kind(),
                "Забор без опоры — забор, а не «прочее»: его снимают"
                        + " Иначе тест classify_FenceAboveGround_IsFenceAtHeight перестанет ловить эту поломку. При смене classify_FenceAboveGround_IsFenceAtHeight проверь это утверждение.");
        assertEquals(
                1,
                finding.height(),
                "Высота считается от верхнего твёрдого блока под клеткой: 66 − 64 − 1"
                        + " Иначе тест classify_FenceAboveGround_IsFenceAtHeight перестанет ловить эту поломку. При смене classify_FenceAboveGround_IsFenceAtHeight проверь это утверждение.");
        assertTrue(
                finding.reason().contains("висит на высоте 1"),
                "Причина называет высоту: владелец видит, насколько объект оторвался от земли"
                        + " Иначе тест classify_FenceAboveGround_IsFenceAtHeight перестанет ловить эту поломку. При смене classify_FenceAboveGround_IsFenceAtHeight проверь это утверждение.");
    }

    @Test
    void classify_FenceOnGround_IsNotReported() {
        Fake world = new Fake();
        world.put(1, 65, 1, "minecraft:oak_fence");

        assertEquals(
                Optional.empty(),
                FloatingBlocks.classify(new BlockCoord(1, 65, 1), world),
                "Забор, стоящий на твёрдом блоке, не висит: его не трогают"
                        + " Иначе тест classify_FenceOnGround_IsNotReported перестанет ловить эту поломку. При смене classify_FenceOnGround_IsNotReported проверь это утверждение.");
    }

    @Test
    void classify_RegisteredChest_IsRegisteredChest() {
        Fake world = new Fake();
        world.put(2, 67, 2, "minecraft:chest");
        world.registered.add(new BlockCoord(2, 67, 2));

        FloatingBlocks.Finding finding = world.only(2, 67, 2);

        assertEquals(
                FloatingBlocks.Kind.REGISTERED_CHEST,
                finding.kind(),
                "Сундук из реестра поселения — отдельный вид"
                        + " Иначе тест classify_RegisteredChest_IsRegisteredChest перестанет ловить эту поломку. При смене classify_RegisteredChest_IsRegisteredChest проверь это утверждение.");
        assertTrue(
                finding.reason().contains("в реестре"),
                "Причина говорит про реестр: уборка сначала переносит содержимое"
                        + " Иначе тест classify_RegisteredChest_IsRegisteredChest перестанет ловить эту поломку. При смене classify_RegisteredChest_IsRegisteredChest проверь это утверждение.");
    }

    @Test
    void classify_UnregisteredChest_IsChest() {
        Fake world = new Fake();
        world.put(2, 67, 2, "minecraft:chest");

        assertEquals(
                FloatingBlocks.Kind.CHEST,
                world.only(2, 67, 2).kind(),
                "Сундук вне реестра — обычный сундук"
                        + " Иначе тест classify_UnregisteredChest_IsChest перестанет ловить эту поломку. При смене classify_UnregisteredChest_IsChest проверь это утверждение.");
    }

    @Test
    void classify_FurnaceAboveGround_IsStation() {
        Fake world = new Fake();
        world.put(3, 68, 3, "minecraft:furnace");

        assertEquals(
                FloatingBlocks.Kind.STATION,
                world.only(3, 68, 3).kind(),
                "Печь и верстак — станции: их снимают или переставляют по плану"
                        + " Иначе тест classify_FurnaceAboveGround_IsStation перестанет ловить эту поломку. При смене classify_FurnaceAboveGround_IsStation проверь это утверждение.");
    }

    @Test
    void classify_LogWithoutTree_IsStumpEvenOnGround() {
        Fake world = new Fake();
        world.put(4, 65, 4, "minecraft:oak_log");

        FloatingBlocks.Finding finding = world.only(4, 65, 4);

        assertEquals(
                FloatingBlocks.Kind.STUMP,
                finding.kind(),
                "Бревно без дерева — пенёк: его рубят, даже если он стоит на земле"
                        + " Иначе тест classify_LogWithoutTree_IsStumpEvenOnGround перестанет ловить эту поломку. При смене classify_LogWithoutTree_IsStumpEvenOnGround проверь это утверждение.");
        assertTrue(
                finding.reason().contains("бревно без дерева"),
                "Причина пенька называет его прямо"
                        + " Иначе тест classify_LogWithoutTree_IsStumpEvenOnGround перестанет ловить эту поломку. При смене classify_LogWithoutTree_IsStumpEvenOnGround проверь это утверждение.");
    }

    @Test
    void classify_LogInTree_IsNotReported() {
        Fake world = new Fake();
        world.put(4, 65, 4, "minecraft:oak_log");
        world.trees.add(new BlockCoord(4, 65, 4));

        assertEquals(
                Optional.empty(),
                FloatingBlocks.classify(new BlockCoord(4, 65, 4), world),
                "Бревно живого дерева — не пенёк: рубка деревьев остаётся своему заданию"
                        + " Иначе тест classify_LogInTree_IsNotReported перестанет ловить эту поломку. При смене classify_LogInTree_IsNotReported проверь это утверждение.");
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
                "Связная листва без брёвен и без опоры — остаток кроны: он обязан попасть в уборку, а не в OTHER"
                        + " Иначе тест classify_LeafCloudWithoutLogs_IsFloatingTree перестанет ловить эту поломку. При смене classify_LeafCloudWithoutLogs_IsFloatingTree проверь это утверждение.");
    }

    @Test
    void classify_PlannedFence_IsNotReported() {
        Fake world = new Fake();
        world.put(5, 69, 5, "minecraft:oak_fence");
        world.planned.add(new BlockCoord(5, 69, 5));

        assertEquals(
                Optional.empty(),
                FloatingBlocks.classify(new BlockCoord(5, 69, 5), world),
                "Блок схемы постройки — часть плана: аудит его не трогает"
                        + " Иначе тест classify_PlannedFence_IsNotReported перестанет ловить эту поломку. При смене classify_PlannedFence_IsNotReported проверь это утверждение.");
    }

    @Test
    void classify_NaturalStone_IsNotReported() {
        Fake world = new Fake();
        world.put(6, 72, 6, "minecraft:stone");

        assertEquals(
                Optional.empty(),
                FloatingBlocks.classify(new BlockCoord(6, 72, 6), world),
                "Природный блок без опоры — не искусственный объект, уборка его не касается"
                        + " Иначе тест classify_NaturalStone_IsNotReported перестанет ловить эту поломку. При смене classify_NaturalStone_IsNotReported проверь это утверждение.");
    }

    @Test
    void classify_SpiritPlacedDirtPillar_IsTemporaryAndRemoved() {
        Fake world = new Fake();
        world.put(6, 72, 6, "minecraft:dirt");
        world.temporary.add(new BlockCoord(6, 72, 6));

        FloatingBlocks.Finding finding = world.only(6, 72, 6);

        assertEquals(
                FloatingBlocks.Kind.OTHER,
                finding.kind(),
                "Земля, поставленная духом вне плана, — временный блок перемещения: аудит обязан её видеть, "
                        + "хотя обычная земля природная и в уборку не идёт"
                        + " Иначе тест classify_SpiritPlacedDirtPillar_IsTemporaryAndRemoved перестанет ловить эту поломку. При смене classify_SpiritPlacedDirtPillar_IsTemporaryAndRemoved проверь это утверждение.");
        assertTrue(
                finding.reason().contains("временный блок духа"),
                "Причина называет владельца и обязанность убрать: иначе след духа неотличим от рельефа"
                        + " При смене classify_SpiritPlacedDirtPillar_IsTemporaryAndRemoved проверь это утверждение.");
    }

    @Test
    void classify_SpiritPlacedDirtInsidePlan_IsNotReported() {
        Fake world = new Fake();
        world.put(6, 72, 6, "minecraft:dirt");
        world.temporary.add(new BlockCoord(6, 72, 6));
        world.planned.add(new BlockCoord(6, 72, 6));

        assertEquals(
                Optional.empty(),
                FloatingBlocks.classify(new BlockCoord(6, 72, 6), world),
                "Блок духа на клетке плана — часть постройки: аудит его не снимает"
                        + " Иначе тест classify_SpiritPlacedDirtInsidePlan_IsNotReported перестанет ловить эту поломку. При смене classify_SpiritPlacedDirtInsidePlan_IsNotReported проверь это утверждение.");
    }

    @Test
    void classify_OtherArtificialBlock_IsOther() {
        Fake world = new Fake();
        world.put(7, 70, 7, "minecraft:oak_planks");

        assertEquals(
                FloatingBlocks.Kind.OTHER,
                world.only(7, 70, 7).kind(),
                "Искусственный блок без своего вида — «прочее»: его тоже снимают"
                        + " Иначе тест classify_OtherArtificialBlock_IsOther перестанет ловить эту поломку. При смене classify_OtherArtificialBlock_IsOther проверь это утверждение.");
    }

    @Test
    void find_AreaOnly_FindsObjectsInsideColumns() {
        Fake world = new Fake();
        world.put(1, 66, 1, "minecraft:oak_fence");
        world.put(9, 66, 9, "minecraft:oak_fence");

        List<FloatingBlocks.Finding> found =
                FloatingBlocks.find(ColumnMask.rect(0, 0, 2, 2), GROUND + 1, GROUND + 5, world);

        assertEquals(
                1,
                found.size(),
                "Сканируются только колонки участка: чужой забор за границей не в списке"
                        + " Иначе тест find_AreaOnly_FindsObjectsInsideColumns перестанет ловить эту поломку. При смене find_AreaOnly_FindsObjectsInsideColumns проверь это утверждение.");
        assertEquals(
                new BlockCoord(1, 66, 1),
                found.get(0).at(),
                "Находка называет точные координаты"
                        + " Иначе тест find_AreaOnly_FindsObjectsInsideColumns перестанет ловить эту поломку. При смене find_AreaOnly_FindsObjectsInsideColumns проверь это утверждение.");
    }

    static final class Fake implements FloatingBlocks.World {
        private final Map<BlockCoord, String> blocks = new HashMap<>();
        private final Set<BlockCoord> planned = new HashSet<>();
        private final Set<BlockCoord> temporary = new HashSet<>();
        private final Set<BlockCoord> trees = new HashSet<>();
        private final Set<BlockCoord> registered = new HashSet<>();

        void put(int x, int y, int z, String id) {
            blocks.put(new BlockCoord(x, y, z), id);
        }

        void markTemporary(BlockCoord at) {
            temporary.add(at);
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
        public boolean temporary(BlockCoord at) {
            return temporary.contains(at);
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
