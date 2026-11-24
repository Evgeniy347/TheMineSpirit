package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class TownMapWaterTests {
    private static final TownRules RULES = TownRules.load();

    @Test
    void check_PlotOverSmallPond_NoWaterFinding() {
        TownMap map = noWall();
        List<TownMapFinding> findings = TownMapCheck.validate(map, RULES, TownMapFixtures.REGION, pond(12, 62, 14, 64));

        assertEquals(
                List.of(),
                findings,
                "Небольшой замкнутый пруд под участком допустим: вода клетки-лужи не блокирует застройку; получено "
                        + findings
                        + " Иначе тест check_PlotOverSmallPond_NoWaterFinding перестанет ловить эту поломку. При смене check_PlotOverSmallPond_NoWaterFinding проверь это утверждение.");
    }

    @Test
    void check_PlotOverLargePond_WaterFinding() {
        TownMap map = noWall();
        List<TownMapFinding> findings = TownMapCheck.validate(map, RULES, TownMapFixtures.REGION, pond(12, 62, 27, 77));

        assertTrue(
                findings.stream().anyMatch(finding -> "water".equals(finding.kind()) && "hut".equals(finding.object())),
                "Пруд площадью 16×16 и больше под участком — ошибка: это уже озеро, а не лужа; получено "
                        + findings
                        + " Иначе тест check_PlotOverLargePond_WaterFinding перестанет ловить эту поломку. При смене check_PlotOverLargePond_WaterFinding проверь это утверждение.");
    }

    @Test
    void check_PlotOverPondAtMaskEdge_WaterFinding() {
        TownMap base = noWall();
        TownMap map = new TownMap(
                base.anchor(),
                List.of(new TownMap.MapPlot("hut", "hut", 0, 60, 15, 75, "north")),
                List.of(),
                List.of());
        List<TownMapFinding> findings = TownMapCheck.validate(map, RULES, TownMapFixtures.REGION, pond(0, 60, 2, 62));

        assertTrue(
                findings.stream().anyMatch(finding -> "water".equals(finding.kind()) && "hut".equals(finding.object())),
                "Вода, уходящая за край пробы, считается открытой: воду у берега не застраивают; получено "
                        + findings
                        + " Иначе тест check_PlotOverPondAtMaskEdge_WaterFinding перестанет ловить эту поломку. При смене check_PlotOverPondAtMaskEdge_WaterFinding проверь это утверждение.");
    }

    @Test
    void check_StreetOverSmallPond_WaterFinding() {
        TownMap base = noWall();
        TownMap map = new TownMap(
                base.anchor(),
                List.of(),
                List.of(new TownMap.MapStreet("pond_street", TownMapRules.SIDE, 9, 59, 15, 65)),
                List.of());

        List<TownMapFinding> findings = TownMapCheck.validate(map, RULES, TownMapFixtures.REGION, pond(12, 62, 14, 64));

        assertTrue(
                findings.stream()
                        .anyMatch(finding -> "water".equals(finding.kind()) && "pond_street".equals(finding.object())),
                "Улица поверх лужи недопустима: дорога не застраивает воду даже малого пруда; получено " + findings
                        + " Иначе тест check_StreetOverSmallPond_WaterFinding перестанет ловить эту поломку. При смене check_StreetOverSmallPond_WaterFinding проверь это утверждение.");
    }

    private static TownMap noWall() {
        TownMap map = TownMapFixtures.withoutRing();
        return new TownMap(map.anchor(), map.plots(), map.streets(), List.of());
    }

    private static ColumnMask pond(int x1, int z1, int x2, int z2) {
        int width = 200;
        BitSet bits = new BitSet(width * width);
        List<ColumnMask.Cell> cells = new ArrayList<>();
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                bits.set(z * width + x);
                cells.add(new ColumnMask.Cell(x, z));
            }
        }
        if (cells.isEmpty()) {
            throw new IllegalArgumentException("Пустой пруд: тесту нечего проверять");
        }
        return new ColumnMask(0, 0, width, width, bits);
    }
}
