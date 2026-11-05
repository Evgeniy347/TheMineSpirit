package com.theminespirit.core.forestry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChopStandsTests {
    @Test
    void around_GroundNextToTrunk_ReturnsGroundStands() {
        Tree tree = trunk(64, 3);
        List<BlockCoord> stands = ChopStands.around(tree, feet -> feet.y() == 64);

        assertEquals(8, stands.size(), "Вокруг ствола на земле восемь стоянок: дух встаёт на землю, а не на дерево");
        assertTrue(
                stands.stream().allMatch(feet -> feet.y() == 64),
                "Стоянка у ствола — на земле основания: залезать на дерево дух не должен");
        assertFalse(stands.contains(new BlockCoord(0, 64, 0)), "Клетка самого ствола стоянкой не бывает");
    }

    @Test
    void around_ThickTrunk_NeverStandsOnLogs() {
        Tree tree = new Tree(
                new BlockCoord(0, 64, 0),
                List.of(new BlockCoord(0, 64, 0), new BlockCoord(1, 64, 0), new BlockCoord(0, 65, 0)));
        List<BlockCoord> stands = ChopStands.around(tree, feet -> true);

        assertFalse(
                stands.contains(new BlockCoord(1, 64, 0)),
                "Вторая клетка толстого ствола — не стоянка: дух не стоит в дереве");
        assertFalse(
                stands.contains(new BlockCoord(0, 65, 0)), "Бревно выше основания тоже не стоянка: на дерево не лезут");
        assertFalse(stands.contains(new BlockCoord(1, 65, 0)), "Стоять на бревне нельзя: низ стоянки — не бревно");
    }

    @Test
    void around_NoStandableGround_IsEmpty() {
        assertEquals(
                List.of(),
                ChopStands.around(trunk(64, 3), feet -> false),
                "Нет места на земле у ствола — нет стоянки: дух не подходит к стволу и не лезет на дерево");
    }

    @Test
    void stands_ForestPileByTrunk_StillFindsStandFartherWithinReach() {
        Tree tree = trunk(64, 4);
        List<BlockCoord> blocked = new ArrayList<>();
        for (int dy = 0; dy < 2; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx != 0 || dz != 0) {
                        blocked.add(new BlockCoord(dx, 64 + dy, dz));
                    }
                }
            }
        }
        assertTrue(
                ChopStands.around(tree, feet -> !blocked.contains(feet)).isEmpty(),
                "Условие теста: вплотную к стволу стоянки нет — ствол в куче деревьев");

        List<BlockCoord> stands = ChopStands.stands(tree, feet -> !blocked.contains(feet));

        assertFalse(
                stands.isEmpty(),
                "Лес вплотную к стволу не делает дерево недоступным: стоянку ищут дальше, но в пределах рубки");
        assertTrue(
                stands.stream().anyMatch(feet -> Math.abs(feet.x()) > 1 || Math.abs(feet.z()) > 1),
                "Дерево в куче рубится со стоянки дальше соседней клетки, а не бросается как недоступное");
        assertTrue(
                stands.stream()
                        .allMatch(feet -> Math.abs(feet.x()) <= ChopStands.FAR_RADIUS
                                && Math.abs(feet.z()) <= ChopStands.FAR_RADIUS),
                "Дальняя стоянка остаётся в пределах досягаемости рубки, иначе дух не достанет ствол");
    }

    @Test
    void stands_HangingTopWithoutTrunk_FindsGroundBelow() {
        List<BlockCoord> logs = List.of(new BlockCoord(0, 6, 0), new BlockCoord(0, 7, 0), new BlockCoord(0, 8, 0));
        Tree hanging = new Tree(new BlockCoord(0, 6, 0), logs);

        List<BlockCoord> stands = ChopStands.stands(hanging, feet -> feet.y() == 1);

        assertFalse(
                stands.isEmpty(),
                "Висящая верхушка без ствола рубится со стоянки на земле под ней, а не требует столба/лестницы");
        assertTrue(
                stands.stream().allMatch(feet -> feet.y() == 1),
                "Стоянка у висящей верхушки — земля, а не воздух рядом с брёвнами: " + stands);
        assertTrue(
                stands.contains(new BlockCoord(0, 1, 0)),
                "Дух встаёт под самой верхушкой (или вплотную к колонне), чтобы дорубить её снизу: " + stands);
    }

    private static Tree trunk(int baseY, int count) {
        List<BlockCoord> logs = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            logs.add(new BlockCoord(0, baseY + i, 0));
        }
        return new Tree(new BlockCoord(0, baseY, 0), logs);
    }
}
