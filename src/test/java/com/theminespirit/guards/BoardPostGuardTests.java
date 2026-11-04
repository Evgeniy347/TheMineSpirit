package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BoardPostGuardTests {
    private static final Path BOARD = Path.of("src/main/java/com/theminespirit/neoforge/board");
    private static final Path REST = Path.of("src/main/java/com/theminespirit/neoforge/rest");

    private static final List<String> OFFENCES = List.of(
            "service.submit(", "board.submit(", "new BoardTicket(", "StageRepair.submit(", "DemolitionTickets.submit(");

    private static final Set<String> INFRASTRUCTURE =
            Set.of("GraphPost.java", "BoardService.java", "BoardStateStore.java");

    private static final Set<String> EXECUTORS = Set.of("ExtraOrchardPost.java", "PlanDemolition.java");

    private static final Map<String, String> TEMPORARY = temporary();

    @Test
    void board_PostsOnlyThroughGraphPost_ExceptExplicitTemporaryList() {
        Map<Path, String> sources = new LinkedHashMap<>(SourceFiles.javaUnder(BOARD));
        sources.putAll(SourceFiles.javaUnder(REST));

        assertEquals(
                List.of(),
                violations(sources),
                "Задание ставится на доску мимо графа: работа выбирается сторожем/порогом, а не узлом графа, "
                        + "поэтому вес и приоритет не участвуют. Цена: дух получает работу, которую граф не "
                        + "выбирал, и следующий пересчёт спорит с ней. Как надо: источник отдаёт факт во вход "
                        + "графа, а билет ставит GraphPost; временное исключение — только с TODO в списке "
                        + "TEMPORARY. Нарушители");
    }

    @Test
    void demolition_IsExecutedByGraphNode_NotByScheduler() {
        Map<Path, String> board = SourceFiles.javaUnder(BOARD);
        String post = board.get(BOARD.resolve("GraphEventPost.java"));
        String lifecycle = board.get(BOARD.resolve("BoardLifecycle.java"));

        assertTrue(
                post.contains("DEMOLITION.contains(ticket.node())") && post.contains("PlanDemolition.tick("),
                "Снос обязан исполняться по узлу графа demolish_stray/clear_floating через GraphPost");
        assertFalse(
                lifecycle.contains("PlanDemolition.tick("),
                "Снос не выбирается расписанием: уборку запускает событие графа, иначе вес и этап не участвуют");
    }

    @Test
    void removedBypassSources_AfterGraphMigration_DoNotComeBack() {
        Set<String> names = new HashSet<>();
        SourceFiles.javaUnder(BOARD)
                .keySet()
                .forEach(path -> names.add(path.getFileName().toString()));

        assertFalse(names.contains("RescueWatch.java"), "Спасение выбирает граф, а не RescueWatch");
        assertFalse(names.contains("DigOutWatch.java"), "Замуровка входит в граф фактом trapped, а не сторожем");
        assertFalse(names.contains("IdleWatch.java"), "Простой входит в граф как more6_spirit_idle");
    }

    @Test
    void patternSource_DirectSubmitOutsideGraphPost_IsReported() {
        String old = "final class RescueWatch { void tick(BoardService service) { service.submit(ticket, target); } }";

        assertEquals(
                List.of("RescueWatch.java: постановка мимо GraphPost"),
                violations(Map.of(BOARD.resolve("RescueWatch.java"), old)),
                "Сторож ослеп: прямой service.submit в стороже обязан ловиться");
    }

    @Test
    void patternSource_GraphPostSubmit_IsAccepted() {
        String fixed = "final class GraphPost { void post(BoardTicket ticket) { service.submit(ticket, target); } }";

        assertEquals(
                List.of(),
                violations(Map.of(BOARD.resolve("GraphPost.java"), fixed)),
                "Сторож ложно срабатывает: единственный законный постановщик — GraphPost");
    }

    private static List<String> violations(Map<Path, String> sources) {
        List<String> found = new ArrayList<>();
        for (Map.Entry<Path, String> source : sources.entrySet()) {
            String name = source.getKey().getFileName().toString();
            if (INFRASTRUCTURE.contains(name) || EXECUTORS.contains(name) || TEMPORARY.containsKey(name)) {
                continue;
            }
            String text = SourceFiles.withoutTextBlocks(source.getValue());
            boolean posts = OFFENCES.stream().anyMatch(text::contains);
            if (posts) {
                found.add(name + ": постановка мимо GraphPost");
            }
        }
        return found;
    }

    private static Map<String, String> temporary() {
        Map<String, String> temporary = new LinkedHashMap<>();
        for (String watch : List.of(
                "ChestSpaceWatch.java",
                "CraterWatch.java",
                "EnchantRoomWatch.java",
                "EvergreenWatch.java",
                "FenceWatch.java",
                "FishingWatch.java",
                "HerdWatch.java",
                "HiveWatch.java",
                "HouseLevels.java",
                "LeftOreWatch.java",
                "MineLevels.java",
                "OldChests.java",
                "OrchardHarvest.java",
                "PenBuild.java",
                "SupplyTickets.java",
                "TransferWatch.java",
                "WarehouseWatch.java",
                "WoolWatch.java")) {
            temporary.put(watch, "TODO B5: сторож ставит заявку по своему порогу — заменить событием графа");
        }
        for (String stage : List.of(
                "FirstStepsRunner.java",
                "PlotAudit.java",
                "RepairAuditWatch.java",
                "RepairWatch.java",
                "StageRepair.java",
                "YardFloorWatch.java")) {
            temporary.put(stage, "TODO B6: этап и ремонт ставят работу мимо узла");
        }
        temporary.put("GraphCollect.java", "TODO B8: исполнитель графа обязан ставить билет через GraphPost");
        temporary.put("GraphFood.java", "TODO B8: исполнитель графа обязан ставить билет через GraphPost");
        temporary.put(
                "SpiritBoardGateway.java",
                "TODO B8: REST POST /v1/board/tasks — заказ обязан стать входом графа (GoalFact/orders)");
        return Map.copyOf(temporary);
    }
}
