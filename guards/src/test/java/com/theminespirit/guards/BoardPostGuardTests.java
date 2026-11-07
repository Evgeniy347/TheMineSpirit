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
    private static final Path BOARD = SourceFiles.path("neoforge/board");
    private static final Path REST = SourceFiles.path("neoforge/rest");
    private static final Path INTELLECT = SourceFiles.path("neoforge/intellect");

    private static final List<String> OFFENCES = List.of(
            "service.submit(",
            "board.submit(",
            "new BoardTicket(",
            "StageRepair.submit(",
            "DemolitionTickets.submit(",
            "service.launch(",
            "host.submit(");

    private static final String GRAPH_POST = "GraphPost.java";

    private static final Map<String, Set<String>> INFRASTRUCTURE = Map.of(
            GRAPH_POST,
            Set.copyOf(OFFENCES),
            "GraphTicketFactory.java",
            Set.of("new BoardTicket("),
            "BoardStateStore.java",
            Set.of("new BoardTicket("),
            "BoardService.java",
            Set.of("board.submit("));

    private static final Map<String, String> EXECUTORS = Map.of(
            "ExtraOrchardPost.java",
            "исполнитель билета графа: сад по узлу harvest_orchard",
            "PlanDemolition.java",
            "исполнитель билета графа: снос по узлу demolish_stray/clear_floating",
            "TransferWatch.java",
            "доставка материалов по билету графа: причина под-задачи — id задания графа, work выбирает граф",
            "SupplyTickets.java",
            "снабжение по requiredItems уже стоящих билетов: работа выводится из потребности графа, не из порога",
            "SpiritBoardGateway.java",
            "REST POST /v1/board/tasks — внешний вход стратега; задача приходит от игрока, а не от сторожа",
            "BoardActivityDesk.java",
            "занятие графа: id узла, команда и план приходят из GraphPost, доска хранит его как задание");

    @Test
    void board_PostsOnlyThroughGraphPost_ExceptDocumentedExecutors() {
        Map<Path, String> sources = new LinkedHashMap<>(SourceFiles.javaUnder(BOARD));
        sources.putAll(SourceFiles.javaUnder(REST));

        assertEquals(
                List.of(),
                violations(sources),
                "Задание ставится на доску мимо графа: работа выбирается сторожем/порогом, а не узлом графа, "
                        + "поэтому вес и приоритет не участвуют. Цена: дух получает работу, которую граф не "
                        + "выбирал, и следующий пересчёт спорит с ней. Как надо: источник отдаёт факт во вход "
                        + "графа, а билет ставит GraphPost; исполнитель билета получает GraphPost и зовёт его "
                        + "submit. Нарушители");
    }

    @Test
    void intellect_OffersNoWork_OnlyFactsOfTheWorld() {
        String source = SourceFiles.javaUnder(INTELLECT).get(INTELLECT.resolve("SettlementIntellect.java"));
        List<String> found = new ArrayList<>();
        for (String pattern : List.of("offerActivities(", "board.launch(", "service.launch(", "ActivityOffer")) {
            if (source.contains(pattern)) {
                found.add(pattern);
            }
        }

        assertEquals(
                List.of(),
                found,
                "Интеллект снова выбирает работу мимо графа: занятие идёт на доску без веса и узла, "
                        + "а следующий пересчёт спорит с ним. Как надо: интеллект отдаёт факты (idle/hungry/"
                        + "угрозы/отдых) во вход графа, работу выбирает GraphPlanner. Нарушители");
    }

    @Test
    void graphPostTicket_PriorityOutsideBoardRange_Clamped() {
        String source = SourceFiles.javaUnder(BOARD).get(BOARD.resolve("GraphTicketFactory.java"));

        assertTrue(
                source.contains("Math.min(100, Math.max(0, request.priority()))"),
                "Билет графа с весом события выше 100 обязан зажиматься к диапазону BoardTicket: иначе источник "
                        + "доски падает с IllegalArgumentException и задача (оборона/еда) не ставится");
    }

    @Test
    void tasksAndDefenseOrders_OutsideGraph_StartNothing() {
        Map<Path, String> sources = new LinkedHashMap<>(SourceFiles.javaUnder(INTELLECT));
        sources.putAll(SourceFiles.javaUnder(BOARD));
        sources.putAll(SourceFiles.javaUnder(Path.of("mod/src/main/java/com/theminespirit/neoforge/coordinator")));
        sources.putAll(SourceFiles.javaUnder(Path.of("mod/src/main/java/com/theminespirit/neoforge/defense")));
        List<String> found = new ArrayList<>();
        for (Map.Entry<Path, String> source : sources.entrySet()) {
            String text = SourceFiles.withoutTextBlocks(source.getValue());
            for (String pattern : List.of(
                    "new TaskStarter(",
                    "starter.start(",
                    "DefenseIssuedOrder",
                    "defense.tick(",
                    "expeditions.update(")) {
                if (text.contains(pattern)) {
                    found.add(source.getKey().getFileName() + ": " + pattern);
                }
            }
        }

        assertEquals(
                List.of(),
                found,
                "Задачи и приказы обороны стартуют мимо графа: контроллер/координатор сам выбирает работу или "
                        + "накладывает приказ поверх билета. Цена: вес и этап не участвуют, граф спорит с самим "
                        + "собой. Как надо: факт — во вход графа, билет ставит GraphPost, исполнитель работает по "
                        + "билету. Нарушители");
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
        assertFalse(names.contains("EvergreenWatch.java"), "Еда входит в граф событием hungry, а не сторожем");
        assertFalse(names.contains("FishingWatch.java"), "Рыбалка — путь графа, а не порог сторожа");
        assertFalse(names.contains("HerdWatch.java"), "Стадо входит в граф фактами, а не сторожем");
        assertFalse(names.contains("HiveWatch.java"), "Пасека — узел графа, а не порог сторожа");
        assertFalse(names.contains("HouseLevels.java"), "Уровень дома выбирает узел building:house");
        assertFalse(names.contains("OldChests.java"), "Перенос старых сундуков выбирает узел store:transfer");
        assertFalse(names.contains("PenBuild.java"), "Загон строит узел building:pen");
        assertFalse(names.contains("WoolWatch.java"), "Шерсть и кровати — узлы графа, а не пороги сторожа");
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
    void patternSource_NewTicketOutsideGraphPost_IsReported() {
        String old =
                "final class DropWatch { BoardTicket t() { return new BoardTicket(id, c, a, p, n, 1, x, l, m); } }";

        assertEquals(
                List.of("DropWatch.java: постановка мимо GraphPost"),
                violations(Map.of(BOARD.resolve("DropWatch.java"), old)),
                "Сторож ослеп: билет, собранный вручную мимо GraphPost, обязан ловиться");
    }

    @Test
    void patternSource_BoardServiceManualTicket_IsReported() {
        String old =
                "final class BoardService { void post() { submit(new BoardTicket(id, c, a, p, n, 1, x, l, m)); } }";

        assertEquals(
                List.of("BoardService.java: постановка мимо GraphPost"),
                violations(Map.of(BOARD.resolve("BoardService.java"), old)),
                "Доска не выбирает работу: ручной билет внутри BoardService — тот же обход графа");
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
            Set<String> allowed = INFRASTRUCTURE.getOrDefault(name, Set.of());
            if (EXECUTORS.containsKey(name)) {
                continue;
            }
            String text = SourceFiles.withoutTextBlocks(source.getValue());
            boolean posts = OFFENCES.stream().anyMatch(pattern -> !allowed.contains(pattern) && text.contains(pattern));
            if (posts) {
                found.add(name + ": постановка мимо GraphPost");
            }
        }
        return found;
    }
}
