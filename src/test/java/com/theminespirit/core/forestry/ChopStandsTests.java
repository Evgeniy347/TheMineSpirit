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

    private static Tree trunk(int baseY, int count) {
        List<BlockCoord> logs = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            logs.add(new BlockCoord(0, baseY + i, 0));
        }
        return new Tree(new BlockCoord(0, baseY, 0), logs);
    }
}
