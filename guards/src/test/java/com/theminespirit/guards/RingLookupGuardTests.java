package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RingLookupGuardTests {
    private static final Path TOWN = SourceFiles.path("core/town");

    @Test
    void townSources_NoLinearPostLookup_KeepsClearSweepWithinTick() {
        List<String> offenders = new ArrayList<>();
        SourceFiles.javaUnder(TOWN).forEach((file, text) -> {
            if (text.contains("posts().contains")) {
                offenders.add(file.toString());
            }
        });

        assertEquals(
                List.of(),
                offenders,
                "Поиск столба ограды идёт линейно по списку постов (posts().contains): на карте ревизии 11 около "
                        + "1150 столбов, а защита спрашивает столб на каждую клетку расчистки — иначе полный "
                        + "проход держал серверный тик 5 секунд (JFR 25.11: 42% сэмплов в StageSite.ringPost). "
                        + "Столб ищется индексом FenceLine.hasPost. При смене RingLookupGuardTests проверь "
                        + "StageSite.ringPost и FenceLineTests");
    }

    @Test
    void linearPostPattern_LookupCall_IsReported() {
        String sample = "        return ring.posts().contains(new ColumnMask.Cell(x, z));\n";

        assertEquals(
                List.of("sample"),
                scanned(List.of(Map.entry(Path.of("sample"), sample))),
                "Сторож ослеп: линейный поиск по столбам ограды обязан попасть в нарушители, иначе дорогая "
                        + "проверка вернётся в тик и снова остановит сервер. При смене проверки шаблона проверь "
                        + "пару образцов: линейный вызов краснеет, hasPost — зелёный");
    }

    @Test
    void indexedPostPattern_LookupCall_IsAccepted() {
        String sample = "        return ring.hasPost(x, z);\n";

        assertEquals(
                List.of(),
                scanned(List.of(Map.entry(Path.of("sample"), sample))),
                "Сторож ложно срабатывает: поиск по индексу hasPost — законный способ спросить столб, иначе "
                        + "каждый законный вызов пришлось бы отключать. При смене проверки шаблона проверь, что "
                        + "образец с hasPost остаётся зелёным");
    }

    private static List<String> scanned(List<Map.Entry<Path, String>> sources) {
        List<String> offenders = new ArrayList<>();
        for (Map.Entry<Path, String> source : sources) {
            if (source.getValue().contains("posts().contains")) {
                offenders.add(source.getKey().toString());
            }
        }
        return offenders;
    }
}
