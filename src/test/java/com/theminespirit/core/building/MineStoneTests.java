package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MineStoneTests {
    private static final BlockCoord ENTRANCE = new BlockCoord(0, 64, 0);

    private static MineLayout layout(int floors, int dug) {
        List<BlockCoord> floor = new ArrayList<>();
        List<BlockCoord> air = new ArrayList<>();
        for (int i = 0; i < floors; i++) {
            floor.add(new BlockCoord(i, 60, 0));
        }
        for (int i = 0; i < dug; i++) {
            air.add(new BlockCoord(i, 61, 0));
        }
        return new MineLayout(floor, air, List.of(), ENTRANCE);
    }

    @Test
    void floorsToBring_DescentThroughStone_NothingToBring() {
        MineLayout mine = layout(89, 178);
        Map<BlockCoord, String> blocks = new HashMap<>();
        mine.air().forEach(cell -> blocks.put(cell, "minecraft:stone"));

        assertEquals(
                0,
                MineStone.floorsToBring(mine, blocks),
                "Проходка по камню даёт булыжник на весь пол (INC-83: шахта ждала 89 булыжника, а булыжник "
                        + "добывается только в шахте — тупик); проверь вычет камня проходки");
    }

    @Test
    void floorsToBring_DescentThroughDirtMostly_BringsOnlyShortage() {
        MineLayout mine = layout(10, 10);
        Map<BlockCoord, String> blocks = new HashMap<>();
        mine.air().forEach(cell -> blocks.put(cell, "minecraft:dirt"));
        blocks.put(mine.air().get(0), "minecraft:stone");
        blocks.put(mine.air().get(1), "minecraft:cobblestone");
        blocks.put(mine.floors().get(0), "minecraft:stone");

        assertEquals(
                7,
                MineStone.floorsToBring(mine, blocks),
                "9 пустых клеток пола минус 2 камня проходки — нести 7: стартовый материал нужен только на "
                        + "нехватку камня в проходке");
    }
}
