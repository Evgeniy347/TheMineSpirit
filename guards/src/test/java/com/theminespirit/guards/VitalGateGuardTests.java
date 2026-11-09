package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.graph.GraphEvent;
import com.theminespirit.core.board.graph.SupplyGraph;
import com.theminespirit.core.board.graph.VitalNeeds;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class VitalGateGuardTests {
    @Test
    void table_EveryVitalEvent_HasPathAndDiagnosis() {
        SupplyGraph graph = SupplyGraph.loadDefault();
        List<VitalNeeds.Need> needs = VitalNeeds.needs();

        assertEquals(
                List.of(),
                missingRows(vitalEvents(graph), needs),
                "Событие графа жизненное, а строки в таблице нужд нет: VitalGate не знает, чем его закрывать, "
                        + "и дух с живой нуждой получит обычную работу. Как надо: добавьте нужду в "
                        + "data/theminespirit/board/vital.json с путём allow и диагнозом blocked. Нарушения");

        List<String> problems = new ArrayList<>();
        for (VitalNeeds.Need need : needs) {
            GraphEvent event = graph.events().get(need.event());
            if (event == null) {
                if (graph.way(need.event()).isEmpty()) {
                    problems.add("нужда " + need.id() + " ссылается на несуществующее событие/путь " + need.event());
                }
                continue;
            }
            if (need.gate() && !event.vital() && !event.life()) {
                problems.add("нужда " + need.id() + " гейтится, но событие " + need.event()
                        + " не помечено vital/life: приоритет не поднимется, и гейт спорит с графом");
            }
            boolean path = event.tasks().stream()
                    .filter(GraphEvent.Task::implemented)
                    .anyMatch(task -> need.allows(ticket(event.name(), task.id())));
            if (!path) {
                problems.add("нужда " + need.id() + " не находит выполнимого пути среди задач события " + need.event()
                        + ": allow=" + need.allow());
            }
        }
        assertEquals(
                List.of(),
                problems,
                "Жизненная нужда без пути в таблице — это молчаливая шахта при живой нужде. Как надо: путь "
                        + "удовлетворения в allow должен совпадать с билетом выполнимой задачи графа. Нарушения");
    }

    @Test
    void table_NewVitalEventWithoutRow_IsReported() {
        Set<String> events = new LinkedHashSet<>(Set.of("hungry", "new_vital"));
        List<VitalNeeds.Need> needs = List.of(need("hunger", "hungry"));

        List<String> found = missingRows(events, needs);

        assertTrue(
                found.stream().anyMatch(issue -> issue.contains("new_vital")),
                "Сторож ослеп: новое жизненное событие без строки таблицы обязано стать красным: " + found);
    }

    @Test
    void table_PathThatMatchesNoTask_IsReported() {
        VitalNeeds.Need broken = new VitalNeeds.Need(
                "hunger", "hungry", true, "проверка", "проверка: пути нет", 0, List.of("graph:event:other:"));
        List<String> found = pathProblems(Map.of("hungry", Set.of("eat", "wait_food")), broken);

        assertTrue(
                found.stream().anyMatch(issue -> issue.contains("не находит выполнимого пути")),
                "Сторож ослеп: allow, не совпавший ни с одной задачей события, обязан стать красным: " + found);
    }

    static List<String> missingRows(Set<String> vitalEvents, List<VitalNeeds.Need> needs) {
        Set<String> described = new LinkedHashSet<>();
        needs.forEach(need -> described.add(need.event()));
        List<String> found = new ArrayList<>();
        for (String event : vitalEvents) {
            if (!described.contains(event)) {
                found.add("жизненное событие " + event + " без строки в таблице нужд");
            }
        }
        return found;
    }

    static List<String> pathProblems(Map<String, Set<String>> tasksByEvent, VitalNeeds.Need need) {
        Set<String> tasks = tasksByEvent.getOrDefault(need.event(), Set.of());
        boolean path = tasks.stream().anyMatch(task -> need.allows(ticket(need.event(), task)));
        return path
                ? List.of()
                : List.of("нужда " + need.id() + " не находит выполнимого пути среди задач события " + need.event()
                        + ": allow=" + need.allow());
    }

    private static Set<String> vitalEvents(SupplyGraph graph) {
        Set<String> events = new LinkedHashSet<>();
        graph.events().values().stream()
                .filter(event -> event.vital() || event.life())
                .forEach(event -> events.add(event.name()));
        return events;
    }

    private static String ticket(String event, String task) {
        return "graph:event:" + event + ":" + task;
    }

    private static VitalNeeds.Need need(String id, String event) {
        return new VitalNeeds.Need(
                id, event, true, "проверка", "проверка: пути нет", 0, List.of("graph:event:" + event + ":"));
    }
}
