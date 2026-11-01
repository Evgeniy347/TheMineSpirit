package com.theminespirit.neoforge;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.forestry.TreeGroup;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.live.LiveFloatingWorld;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class LiveFloatingTreeTests {
    private static final BlockCoord TRUNK_STUMP = new BlockCoord(-170, 85, -514);
    private static final BlockCoord CROWN_BASE = new BlockCoord(-163, 85, -507);

    @Test
    void liveFloatingCrown_Extend_IncludesUpperLog() throws IOException {
        LiveFloatingWorld world = LiveFloatingWorld.load();
        List<BlockCoord> cells = new ArrayList<>(List.of(TRUNK_STUMP));

        new TreeFelling(world).extend(cells);

        assertTrue(
                cells.contains(new BlockCoord(-170, 86, -514)),
                "Верх ствола -170,85,-514 остался без низа (y=71..84 пусто, аудит назвал пенёк -170,70,-514): "
                        + "расчистка обязана взять этот висящий обломок с листвой, иначе он висит вечно. Список: "
                        + cells);
    }

    @Test
    void liveFloatingJungleCrown_Extend_IncludesLeavesAndTrunk() throws IOException {
        LiveFloatingWorld world = LiveFloatingWorld.load();
        List<BlockCoord> cells = new ArrayList<>(List.of(CROWN_BASE));

        new TreeFelling(world).extend(cells);

        assertTrue(
                cells.contains(new BlockCoord(-163, 90, -507)),
                "У живого дерева -163,85..97,-507 срублен низ на y=69..84: верх ствола обязан попасть в "
                        + "расчистку целиком, а не по одному блоку. Список: " + cells);
        assertTrue(
                cells.contains(new BlockCoord(-163, 98, -507)),
                "Крона -163,98,-507 осталась после срубленного низа: дерево рубится вместе с листвой. Список: "
                        + cells);
        TreeGroup group = new TreeFelling(world).group(CROWN_BASE);
        assertFalse(group.grounded(), "У живого обломка нет опоры под нижним бревном: он висящий");
    }

    @Test
    void liveJungleCrown_Extend_IncludesFarLeaves() throws IOException {
        LiveFloatingWorld world = LiveFloatingWorld.load();
        List<BlockCoord> cells = new ArrayList<>(List.of(CROWN_BASE));

        new TreeFelling(world).extend(cells);

        assertTrue(
                cells.contains(new BlockCoord(-166, 96, -510)),
                "Крона живого дерева держится листвой за листву: без обхода листвы дальние листья "
                        + "(-167..-173,79..85,-516..-517 из observe15) остаются висеть. Список: " + cells);
    }

    @Test
    void liveFloatingJungleCrown_PartialGroup_IsDroppedWhole() throws IOException {
        LiveFloatingWorld world = LiveFloatingWorld.load();
        TreeFelling felling = new TreeFelling(world);
        TreeGroup group = felling.group(CROWN_BASE);
        List<BlockCoord> cells = new ArrayList<>(group.cells());
        BlockCoord lastLeaf = cells.remove(cells.size() - 1);

        int dropped = felling.dropPartial(cells);

        assertTrue(dropped > 0, "Неполное дерево нельзя рубить по частям: остаток обязан сниматься с уборки");
        assertFalse(
                cells.contains(CROWN_BASE),
                "Нижнее бревно висящего дерева нельзя ломать, если часть кроны недостижима: иначе снова "
                        + "останется висеть верх. Осталось: " + cells + ", выкинута клетка " + lastLeaf);
    }
}
