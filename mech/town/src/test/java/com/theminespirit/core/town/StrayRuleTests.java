package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.forestry.TreeView;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.policy.BlockProtection;
import com.theminespirit.core.reload.DataTables;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StrayRuleTests {
    private static final BlockProtection PROTECTION =
            DataTables.INSTANCE.protection().current();

    @Test
    void stray_OldFenceNotInRegistry_IsDemolished() {
        assertTrue(
                StrayRule.stray("minecraft:oak_fence", EnumSet.noneOf(StrayRule.Fact.class), PROTECTION),
                "Старый забор вне плана сносится и без записи в реестре духов: иначе обломки прошлых версий стоят "
                        + "вечно (INC-91)");
        assertTrue(
                StrayRule.stray("minecraft:spruce_planks", EnumSet.of(StrayRule.Fact.UNSUPPORTED), PROTECTION),
                "Доски в воздухе вне плана — искусственный обломок, снос");
    }

    @Test
    void stray_PlayerPlannedOrNatural_IsKept() {
        assertFalse(
                StrayRule.stray("minecraft:oak_planks", EnumSet.of(StrayRule.Fact.PLAYER), PROTECTION),
                "Постройку игрока снос не трогает (PLAYER_BUILDINGS)");
        assertFalse(
                StrayRule.stray("minecraft:oak_fence", EnumSet.of(StrayRule.Fact.PLANNED), PROTECTION),
                "Блок плана не сносится: это ограда этапа");
        assertFalse(
                StrayRule.stray("minecraft:stone", EnumSet.noneOf(StrayRule.Fact.class), PROTECTION),
                "Природный камень — не постройка, не сносится");
        assertFalse(
                StrayRule.stray("minecraft:poppy", EnumSet.of(StrayRule.Fact.PLANT), PROTECTION),
                "Цветы и саженцы — природа, не обломок");
    }

    @Test
    void stray_MasterStation_IsKept() {
        assertFalse(
                StrayRule.stray("minecraft:crafting_table", EnumSet.noneOf(StrayRule.Fact.class), PROTECTION),
                "Стол мастера не лишняя постройка: без него духи не скрафтят материалы и хижина встанет");
        assertFalse(
                StrayRule.stray("minecraft:furnace", EnumSet.noneOf(StrayRule.Fact.class), PROTECTION),
                "Печь двора — станция, а не обломок: её снос отнимает у поселения варку");
    }

    @Test
    void stray_LogOutsideTree_IsDemolishedButTreeStays() {
        assertTrue(
                StrayRule.stray("minecraft:oak_log", EnumSet.of(StrayRule.Fact.LOG), PROTECTION),
                "Бревно без листвы вне дерева — столб старой хижины, снос");
        assertFalse(
                StrayRule.stray(
                        "minecraft:oak_log", EnumSet.of(StrayRule.Fact.LOG, StrayRule.Fact.IN_TREE), PROTECTION),
                "Бревно живого дерева снос не трогает: деревья рубит рубка, а не снос");
    }

    @Test
    void treeLogs_PostWithoutLeavesAndTree_Differ() {
        Set<BlockCoord> leaves = Set.of(new BlockCoord(0, 3, 1), new BlockCoord(1, 3, 0), new BlockCoord(0, 4, 0));
        Set<BlockCoord> logs = Set.of(new BlockCoord(0, 1, 0), new BlockCoord(0, 2, 0), new BlockCoord(0, 3, 0));
        TreeView tree = view(logs, leaves);
        assertEquals(
                logs,
                StrayRule.treeLogs(new BlockCoord(0, 1, 0), tree),
                "Брёвна с листвой вокруг — дерево целиком: снос не трогает ни одно из них");
        assertEquals(
                Set.of(),
                StrayRule.treeLogs(new BlockCoord(0, 1, 0), view(logs, Set.of())),
                "Столб брёвен без листвы — не дерево, а остаток постройки");
    }

    private static TreeView view(Set<BlockCoord> logs, Set<BlockCoord> leaves) {
        return new TreeView() {
            @Override
            public boolean isLog(BlockCoord coord) {
                return logs.contains(coord);
            }

            @Override
            public boolean isNaturalLeaves(BlockCoord coord) {
                return leaves.contains(coord);
            }

            @Override
            public boolean isSoil(BlockCoord coord) {
                return false;
            }

            @Override
            public boolean isArtificial(BlockCoord coord) {
                return false;
            }

            @Override
            public boolean isEmpty(BlockCoord coord) {
                return !logs.contains(coord) && !leaves.contains(coord);
            }
        };
    }
}
