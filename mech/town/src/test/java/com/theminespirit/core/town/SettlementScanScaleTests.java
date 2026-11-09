package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import org.junit.jupiter.api.Test;

class SettlementScanScaleTests {
    @Test
    void scan_FlatField160x160_ReadsNoMoreThanHeightmapNeeds() {
        SettlementScanTests.Fake world = new SettlementScanTests.Fake();
        int side = 160;

        SettlementScan.scan(
                ColumnMask.rect(0, 0, side - 1, side - 1).cells(),
                SettlementScanTests.LEVEL,
                SettlementScanTests.rules(),
                world);

        long budget = (long) side * side * 10;
        assertTrue(
                world.reads <= budget,
                "Скан ровного поля 160×160 не читает блоки над площадкой, которых нет по heightmap: "
                        + world.reads + " чтений при бюджете " + budget
                        + "; иначе каждый пересчёт участка кладёт треть лишних чтений в тик сервера");
    }

    @Test
    void scan_NonMotionBlockAboveLevel_StillFoundBySurfaceHeightmap() {
        SettlementScanTests.Fake world = new SettlementScanTests.Fake();
        world.put(2, SettlementScanTests.LEVEL, 2, "minecraft:torch");

        SettlementScan.Strays found = SettlementScan.scan(
                ColumnMask.rect(0, 0, 6, 6).cells(), SettlementScanTests.LEVEL, SettlementScanTests.rules(), world);

        assertEquals(
                List.of(new BlockCoord(2, SettlementScanTests.LEVEL, 2)),
                found.strays().stream().map(StrayGroups.Cell::at).toList(),
                "Факел не перекрывает движение, но остаётся лишним над площадкой: граница чтения берётся по "
                        + "WORLD_SURFACE, а не по MOTION_BLOCKING, иначе скан перестанет видеть такие блоки");
    }

    @Test
    void scan_BlockAboveLevel_StillFoundWhenHeightmapSeesIt() {
        SettlementScanTests.Fake world = new SettlementScanTests.Fake();
        world.put(2, SettlementScanTests.LEVEL, 2, "minecraft:cobblestone");

        SettlementScan.Strays found = SettlementScan.scan(
                ColumnMask.rect(0, 0, 6, 6).cells(), SettlementScanTests.LEVEL, SettlementScanTests.rules(), world);

        assertEquals(
                List.of(new BlockCoord(2, SettlementScanTests.LEVEL, 2)),
                found.strays().stream().map(StrayGroups.Cell::at).toList(),
                "Heightmap подсказывает только границу чтения: блок над уровнем всё равно обязан попасть "
                        + "в лишнее, иначе скан перестанет видеть столбы и мусор");
    }
}
