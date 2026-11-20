package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RepairNeedTests {
    @Test
    void worldMissing_AddedToTicketNeed_AppearsInMerge() {
        Map<String, Integer> need = RepairNeed.merge(Map.of("cobblestone", 2), Map.of("dirt", 430));

        assertEquals(
                Map.of("cobblestone", 2, "dirt", 430),
                need,
                "Недостача мира попадает в потребности ремонта: иначе билет DONE не переоткроется, "
                        + "мир останется без 430 блоков улиц, а духи будут ждать впустую; при смене "
                        + "repairNeed проверь слияние с consumes");
    }

    @Test
    void consumption_WorldNeedRepeatsItem_ConsumesWin() {
        Map<String, Integer> need = RepairNeed.merge(Map.of("dirt", 2), Map.of("dirt", 430));

        assertEquals(
                Map.of("dirt", 2),
                need,
                "Потребление билета не перетирается недостачей мира: без этого материалы считаются дважды, "
                        + "и запас уходит в минус; при смене слияния проверь putIfAbsent");
    }

    @Test
    void digKey_NamespacedItems_AreNormalized() {
        Map<String, Integer> missing = new LinkedHashMap<>();
        missing.put(BuildGap.DIG, 5);
        missing.put("minecraft:dirt", 7);

        assertEquals(
                Map.of("dirt", 7),
                RepairNeed.merge(Map.of(), missing),
                "«проходка» не материал, а namespace снимается: иначе требуют несуществующий предмет "
                        + "и ремонт блокируется; при смене ключей BuildGap.mine проверь нормализацию");
    }
}
