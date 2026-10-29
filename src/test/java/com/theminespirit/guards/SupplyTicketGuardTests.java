package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SupplyTicketGuardTests {
    private static final Path BOARD = Path.of("src/main/java/com/theminespirit/neoforge/board");
    private static final Path SUPPLY_TICKETS = BOARD.resolve("SupplyTickets.java");
    private static final String WITHOUT_DEPOSIT = "SupplyGate.needsDepositPoint(boardCase)";
    private static final String BRANCH_START = "StorageFinder.depositPoint(";
    private static final String BRANCH_END = "service.submit(";

    @Test
    void supplyTickets_NoContainer_StillIssuesGather() {
        Map<Path, String> sources = new LinkedHashMap<>();
        sources.put(SUPPLY_TICKETS, SourceFiles.javaUnder(BOARD).get(SUPPLY_TICKETS));

        assertEquals(
                List.of(),
                violations(sources),
                "Добыча без контейнера снова бросается: при пустом складе GATHER_MATERIALS не выходит на доску, "
                        + "стройка висит в WAITING_RESOURCES навсегда, а сцена снабжения краснеет. Как надо: без "
                        + "точки сдачи задание пропускает только снабжение со склада (SupplyGate.needsDepositPoint), "
                        + "а добыча выдаётся и сдаёт ближайшему сундуку. Нарушители");
    }

    @Test
    void patternSource_NoContainerReturnsBeforeGather_IsReported() {
        String old = """
                Optional<BlockCoord> point = StorageFinder.depositPoint(level, target.target(), item, count);
                if (point.isEmpty()) {
                    LOGGER.info("нет контейнера");
                    return;
                }
                service.submit(ticket, target);
                """;

        assertEquals(
                List.of("SupplyTickets.java: добыча бросается без контейнера"),
                violations(Map.of(SUPPLY_TICKETS, old)),
                "Сторож ослеп: старый код, который возвращается до выдачи добычи без контейнера, обязан ловиться");
    }

    @Test
    void patternSource_DepositGuardBeforeReturn_IsAccepted() {
        String fixed = """
                Optional<BlockCoord> point = StorageFinder.depositPoint(level, target.target(), item, count);
                if (point.isEmpty()) {
                    if (SupplyGate.needsDepositPoint(boardCase)) {
                        return;
                    }
                }
                service.submit(ticket, target);
                """;

        assertEquals(
                List.of(),
                violations(Map.of(SUPPLY_TICKETS, fixed)),
                "Сторож ложно срабатывает: проверка needsDepositPoint перед возвратом — правильный код");
    }

    private static List<String> violations(Map<Path, String> sources) {
        List<String> found = new ArrayList<>();
        for (Map.Entry<Path, String> source : sources.entrySet()) {
            String name = source.getKey().getFileName().toString();
            String text = source.getValue();
            int start = text.indexOf(BRANCH_START);
            int end = text.indexOf(BRANCH_END, Math.max(start, 0));
            if (start < 0 || end < 0 || !text.substring(start, end).contains(WITHOUT_DEPOSIT)) {
                found.add(name + ": добыча бросается без контейнера");
            }
        }
        return found;
    }
}
