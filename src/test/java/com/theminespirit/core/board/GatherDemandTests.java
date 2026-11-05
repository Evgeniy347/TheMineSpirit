package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import org.junit.jupiter.api.Test;

class GatherDemandTests {
    private static final BlockCoord AT = new BlockCoord(0, 64, 0);

    private static GatherDemand.Need need(String id, String item, int count, int priority) {
        return new GatherDemand.Need(id, item, id, count, priority, AT);
    }

    @Test
    void merge_ThreeCausesSameLog_OneRequestWithTotal() {
        List<GatherDemand.Need> merged = GatherDemand.merge(List.of(
                need("graph:chop_jungle_log", "jungle_log", 24, 80),
                need("graph:make:jungle_log", "jungle_log", 24, 90),
                need("graph:event:more2_stage_prep:more2_prepare_stage", "jungle_log", 16, 40)));

        assertEquals(1, merged.size(), "Три причины одного бревна — одна заявка, а не три: " + merged);
        assertEquals(64, merged.get(0).count(), "Количество заявки — сумма всех причин (24+24+16): " + merged);
        assertEquals("graph:chop_jungle_log", merged.get(0).id(), "Заявка держит графовый путь рубки: " + merged);
        assertEquals(90, merged.get(0).priority(), "Приоритет общей заявки — самый высокий из причин: " + merged);
    }

    @Test
    void merge_DifferentItems_StayApart() {
        List<GatherDemand.Need> merged = GatherDemand.merge(List.of(
                need("graph:chop_jungle_log", "jungle_log", 64, 80), need("graph:chop_oak_log", "oak_log", 32, 70)));

        assertEquals(2, merged.size(), "Дуб и джунгль — разные предметы, их заявки не сливаются: " + merged);
    }

    @Test
    void onePerWood_LogAndSaplingsSameSpecies_WaitForOneRequest() {
        List<GatherDemand.Need> merged = GatherDemand.onePerWood(List.of(
                need("graph:chop_jungle_log", "jungle_log", 64, 80),
                need("graph:gather_jungle_sapling", "jungle_sapling", 6, 90)));

        assertEquals(1, merged.size(), "Лес одной породы — одна заявка: саженцы ждут, пока рубят брёвна: " + merged);
        assertEquals("jungle_log", merged.get(0).item(), "Побеждает бревно, даже если саженцы важнее: " + merged);
    }

    @Test
    void onePerWood_PlanksSameSpecies_StaySeparateCraft() {
        List<GatherDemand.Need> merged = GatherDemand.onePerWood(List.of(
                need("graph:chop_jungle_log", "jungle_log", 64, 80),
                need("graph:craft_jungle_planks", "jungle_planks", 8, 90)));

        assertEquals(
                2,
                merged.size(),
                "Доски — крафт из брёвен, а не вторая рубка: заявка на доски остаётся, иначе стройка ждёт: " + merged);
    }

    @Test
    void onePerWood_DifferentSpecies_WorkInParallel() {
        List<GatherDemand.Need> merged = GatherDemand.onePerWood(List.of(
                need("graph:chop_jungle_log", "jungle_log", 64, 80), need("graph:chop_oak_log", "oak_log", 32, 70)));

        assertEquals(2, merged.size(), "Разные породы — разные заявки: " + merged);
    }

    @Test
    void wood_LogPlanksSapling_GivesSpecies() {
        assertEquals("jungle", GatherDemand.wood("jungle_log"), "Бревно называет породу");
        assertEquals("jungle", GatherDemand.wood("jungle_planks"), "Доски — та же порода");
        assertEquals("jungle", GatherDemand.wood("jungle_sapling"), "Саженец — та же порода");
        assertEquals("oak", GatherDemand.wood("stripped_oak_log"), "Ошкуренное бревно — та же порода");
        assertNull(GatherDemand.wood("cobblestone"), "Камень не порода дерева");
    }
}
