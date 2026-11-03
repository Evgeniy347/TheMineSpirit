package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BoardPostGuardTests {
    private static final Path BOARD = Path.of("src/main/java/com/theminespirit/neoforge/board");
    private static final List<String> MIGRATED =
            List.of("LeftOreWatch.java", "OrchardHarvest.java", "FenceWatch.java", "CraterWatch.java");

    @Test
    void migratedWatchers_SourcesScanned_PostNoTickets() {
        Map<Path, String> sources = new LinkedHashMap<>();
        for (String name : MIGRATED) {
            sources.put(BOARD.resolve(name), SourceFiles.javaUnder(BOARD).get(BOARD.resolve(name)));
        }

        assertEquals(
                List.of(),
                violations(sources),
                "Сторож безопасные-1: эти источники дают только факты графа. Работу выбирает узел графа "
                        + "(event:fence_broken, event:creeper_crater, harvest_orchard_*, mine_*), а билет ставит "
                        + "GraphPost. Постановка мимо графа — двойная работа и обход решения владельца 03.11. "
                        + "Нарушители");
    }

    @Test
    void watcherSource_SubmitCall_IsReported() {
        String sample = """
                final class CraterWatch {
                    void tick(BoardService service) {
                        service.submit(new BoardTicket("id", null, null, 0, 0, 1, "c", List.of(), Map.of()), null);
                    }
                }
                """;

        assertEquals(
                List.of("CraterWatch.java:3 постановка мимо графа"),
                violations(Map.of(Path.of("CraterWatch.java"), sample)),
                "Сторож ослеп: вызов service.submit в стороже обязан попадать в нарушители");
    }

    private static List<String> violations(Map<Path, String> sources) {
        List<String> found = new ArrayList<>();
        for (Map.Entry<Path, String> source : sources.entrySet()) {
            String text = SourceFiles.withoutTextBlocks(source.getValue());
            if (text.contains(".submit(") || text.contains("new BoardTicket(")) {
                found.add(source.getKey().getFileName() + ":" + SourceFiles.lineOf(text, postOffset(text))
                        + " постановка мимо графа");
            }
        }
        return found;
    }

    private static int postOffset(String text) {
        int submit = text.indexOf(".submit(");
        int ticket = text.indexOf("new BoardTicket(");
        if (submit < 0) {
            return ticket;
        }
        if (ticket < 0) {
            return submit;
        }
        return Math.min(submit, ticket);
    }
}
