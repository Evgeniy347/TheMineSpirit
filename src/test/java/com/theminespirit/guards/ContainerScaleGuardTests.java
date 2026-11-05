package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ContainerScaleGuardTests {
    private static final Path INVENTORY_ADAPTER = SourceFiles.path("neoforge/inventory");

    @Test
    void inventoryAdapter_NoEntityScans_KeepsTickBudget() {
        List<String> offenders = new ArrayList<>();
        SourceFiles.javaUnder(INVENTORY_ADAPTER).forEach((file, text) -> {
            if (text.contains("getEntities")) {
                offenders.add(file.toString());
            }
        });

        assertEquals(
                List.of(),
                offenders,
                "Адаптер инвентаря сканирует сущности мира (getEntities): поиск контейнера идёт по срезам блоков "
                        + "порциями, а перенос — по слотам, обход сущностей в серверном тике на каждого духа "
                        + "нагружает сервер зря. Сущности подбирает только DropCollector. Нарушители");
    }

    @Test
    void entityScanPattern_CallInAdapter_IsReported() {
        String sample = "nearby = level.getEntitiesOfClass(ItemEntity.class, area, drop -> true);\n";

        assertEquals(
                List.of("sample"),
                scanned(List.of(Map.entry(Path.of("sample"), sample))),
                "Сторож ослеп: вызов getEntities в адаптере инвентаря обязан находиться");
    }

    @Test
    void entityScanPattern_CleanAdapter_IsAccepted() {
        String sample = "candidates.addAll(search.step(SLICES_PER_TICK));\n";

        assertEquals(
                List.of(),
                scanned(List.of(Map.entry(Path.of("sample"), sample))),
                "Сторож ложно срабатывает: поиск по срезам блоков — не скан сущностей");
    }

    private static List<String> scanned(List<Map.Entry<Path, String>> sources) {
        List<String> offenders = new ArrayList<>();
        for (Map.Entry<Path, String> source : sources) {
            if (source.getValue().contains("getEntities")) {
                offenders.add(source.getKey().toString());
            }
        }
        return offenders;
    }
}
