package com.theminespirit.core.board.graph;

import static com.theminespirit.core.board.graph.GraphFragmentCases.ALL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.graph.GraphFragmentCases.Fragment;
import com.theminespirit.core.board.graph.GraphFragmentCases.GroupCase;
import com.theminespirit.core.board.graph.GraphFragmentCases.WayCase;
import java.util.LinkedHashSet;
import org.junit.jupiter.api.Test;

class GraphFragmentsScenarioTests {
    private static final SupplyGraph GRAPH = SupplyGraph.loadDefault();

    @Test
    void fragments_ProductionData_DeclareEventsWithWork() {
        for (Fragment fragment : ALL) {
            for (String event : fragment.events()) {
                assertTrue(
                        GRAPH.events().containsKey(event),
                        fragment.area() + ": событие объявлено: " + event
                                + " Иначе тест fragments_ProductionData_DeclareEventsWithWork перестанет ловить эту поломку. При смене fragments_ProductionData_DeclareEventsWithWork проверь это утверждение.");
                GraphEvent known = GRAPH.events().get(event);
                assertTrue(
                        fragment.tasksRequired()
                                ? !known.tasks().isEmpty()
                                : !known.tasks().isEmpty() || !known.push().isEmpty(),
                        fragment.area() + ": событие " + event + " без работы не двигает граф"
                                + " Иначе тест fragments_ProductionData_DeclareEventsWithWork перестанет ловить эту поломку. При смене fragments_ProductionData_DeclareEventsWithWork проверь это утверждение.");
            }
            for (String event : fragment.absentEvents()) {
                assertFalse(
                        GRAPH.events().containsKey(event),
                        fragment.area() + ": удалённое событие не вернулось: " + event
                                + " Иначе тест fragments_ProductionData_DeclareEventsWithWork перестанет ловить эту поломку. При смене fragments_ProductionData_DeclareEventsWithWork проверь это утверждение.");
            }
        }
    }

    @Test
    void fragments_ProductionData_WaysAreWiredOrStubWithPositiveWeight() {
        for (Fragment fragment : ALL) {
            for (WayCase expected : fragment.ways()) {
                SupplyGraph.Way way = GRAPH.way(expected.id())
                        .orElseThrow(() -> new AssertionError(fragment.area() + ": путь объявлен: " + expected.id()));
                assertTrue(
                        way.boardCase() != null,
                        fragment.area() + ": у пути есть вид работы: " + expected.id()
                                + " Иначе тест fragments_ProductionData_WaysAreWiredOrStubWithPositiveWeight перестанет ловить эту поломку. При смене fragments_ProductionData_WaysAreWiredOrStubWithPositiveWeight проверь это утверждение.");
                assertEquals(
                        expected.implemented(),
                        GRAPH.implemented(expected.id()),
                        fragment.area() + ": путь связан с действием или честно помечен заглушкой: " + expected.id()
                                + " Иначе тест fragments_ProductionData_WaysAreWiredOrStubWithPositiveWeight перестанет ловить эту поломку. При смене fragments_ProductionData_WaysAreWiredOrStubWithPositiveWeight проверь это утверждение.");
                if (expected.makes() != null) {
                    assertEquals(
                            expected.makes(),
                            way.makes(),
                            fragment.area() + ": путь делает свой узел: " + expected.id()
                                    + " Иначе тест fragments_ProductionData_WaysAreWiredOrStubWithPositiveWeight перестанет ловить эту поломку. При смене fragments_ProductionData_WaysAreWiredOrStubWithPositiveWeight проверь это утверждение.");
                    assertTrue(
                            GRAPH.producers(expected.makes()).contains(way),
                            fragment.area() + ": узел " + expected.makes() + " знает своего производителя"
                                    + " Иначе тест fragments_ProductionData_WaysAreWiredOrStubWithPositiveWeight перестанет ловить эту поломку. При смене fragments_ProductionData_WaysAreWiredOrStubWithPositiveWeight проверь это утверждение.");
                }
            }
            for (String absent : fragment.absentWays()) {
                assertTrue(
                        GRAPH.way(absent).isEmpty(),
                        fragment.area() + ": дубль или правило не висит путём: " + absent
                                + " Иначе тест fragments_ProductionData_WaysAreWiredOrStubWithPositiveWeight перестанет ловить эту поломку. При смене fragments_ProductionData_WaysAreWiredOrStubWithPositiveWeight проверь это утверждение.");
            }
        }
    }

    @Test
    void fragments_ProductionData_ItemsHavePositiveStartWeights() {
        for (Fragment fragment : ALL) {
            for (String item : fragment.items()) {
                assertTrue(
                        GRAPH.items().get(item).base() > 0,
                        fragment.area() + ": стартовый вес предмета положителен: " + item
                                + " Иначе тест fragments_ProductionData_ItemsHavePositiveStartWeights перестанет ловить эту поломку. При смене fragments_ProductionData_ItemsHavePositiveStartWeights проверь это утверждение.");
            }
            for (String item : fragment.zeroDemandItems()) {
                assertEquals(
                        0,
                        GRAPH.items().get(item).xmin(),
                        fragment.area() + ": вход не создаёт спрос сам по себе: " + item
                                + " Иначе тест fragments_ProductionData_ItemsHavePositiveStartWeights перестанет ловить эту поломку. При смене fragments_ProductionData_ItemsHavePositiveStartWeights проверь это утверждение.");
            }
        }
    }

    @Test
    void fragments_ProductionData_EventTasksPointAtWaysAndRepeatState() {
        for (Fragment fragment : ALL) {
            if (fragment.prefix() != null) {
                assertEquals(
                        fragment.events().size(),
                        new LinkedHashSet<>(fragment.events()).size(),
                        fragment.area() + ": имена событий области не повторяются"
                                + " Иначе тест fragments_ProductionData_EventTasksPointAtWaysAndRepeatState перестанет ловить эту поломку. При смене fragments_ProductionData_EventTasksPointAtWaysAndRepeatState проверь это утверждение.");
                for (String event : fragment.events()) {
                    assertTrue(
                            event.startsWith(fragment.prefix()),
                            fragment.area() + ": событие названо с префиксом " + fragment.prefix() + ": " + event
                                    + " Иначе тест fragments_ProductionData_EventTasksPointAtWaysAndRepeatState перестанет ловить эту поломку. При смене fragments_ProductionData_EventTasksPointAtWaysAndRepeatState проверь это утверждение.");
                }
            }
            for (String event : fragment.events()) {
                if (!fragment.tasksAreWays()) {
                    continue;
                }
                for (GraphEvent.Task task : GRAPH.events().get(event).tasks()) {
                    assertTrue(
                            GRAPH.way(task.id()).isPresent(),
                            fragment.area() + ": задача " + event + " -> " + task.id() + " должна быть путём"
                                    + " Иначе тест fragments_ProductionData_EventTasksPointAtWaysAndRepeatState перестанет ловить эту поломку. При смене fragments_ProductionData_EventTasksPointAtWaysAndRepeatState проверь это утверждение.");
                    assertEquals(
                            GRAPH.implemented(task.id()),
                            task.implemented(),
                            fragment.area() + ": задача " + event + " -> " + task.id()
                                    + " не расходится с состоянием пути"
                                    + " Иначе тест fragments_ProductionData_EventTasksPointAtWaysAndRepeatState перестанет ловить эту поломку. При смене fragments_ProductionData_EventTasksPointAtWaysAndRepeatState проверь это утверждение.");
                }
            }
        }
    }

    @Test
    void fragments_ProductionData_PushAndConsumesReferToKnownNodes() {
        for (Fragment fragment : ALL) {
            for (String event : fragment.events()) {
                GraphEvent known = GRAPH.events().get(event);
                known.push()
                        .keySet()
                        .forEach(
                                node -> assertTrue(
                                        known(node),
                                        fragment.area() + ": " + event + " толкает неизвестный узел " + node
                                                + " Иначе тест fragments_ProductionData_PushAndConsumesReferToKnownNodes перестанет ловить эту поломку. При смене fragments_ProductionData_PushAndConsumesReferToKnownNodes проверь это утверждение."));
                known.tasks()
                        .forEach(
                                task -> task.consumes()
                                        .keySet()
                                        .forEach(
                                                item -> assertTrue(
                                                        known(item),
                                                        fragment.area() + ": " + event + " расходует неизвестное "
                                                                + item
                                                                + " Иначе тест fragments_ProductionData_PushAndConsumesReferToKnownNodes перестанет ловить эту поломку. При смене fragments_ProductionData_PushAndConsumesReferToKnownNodes проверь это утверждение.")));
            }
        }
    }

    @Test
    void fragments_ProductionData_GroupsAreDeclaredInOwnFile() {
        for (Fragment fragment : ALL) {
            for (GroupCase group : fragment.groups()) {
                assertTrue(
                        GRAPH.isGroup(group.name()),
                        fragment.area() + ": группа объявлена: " + group.name()
                                + " Иначе тест fragments_ProductionData_GroupsAreDeclaredInOwnFile перестанет ловить эту поломку. При смене fragments_ProductionData_GroupsAreDeclaredInOwnFile проверь это утверждение.");
                if (group.exact()) {
                    assertEquals(
                            group.members(),
                            GRAPH.members(group.name()),
                            fragment.area() + ": состав группы " + group.name() + " объявлен один раз и в своём файле"
                                    + " Иначе тест fragments_ProductionData_GroupsAreDeclaredInOwnFile перестанет ловить эту поломку. При смене fragments_ProductionData_GroupsAreDeclaredInOwnFile проверь это утверждение.");
                } else {
                    assertTrue(
                            GRAPH.members(group.name()).containsAll(group.members()),
                            fragment.area() + ": группа " + group.name() + " содержит " + group.members()
                                    + " Иначе тест fragments_ProductionData_GroupsAreDeclaredInOwnFile перестанет ловить эту поломку. При смене fragments_ProductionData_GroupsAreDeclaredInOwnFile проверь это утверждение.");
                }
            }
        }
    }

    @Test
    void fragments_ProductionData_OptionalBuildingsAreImplemented() {
        for (Fragment fragment : ALL) {
            for (String building : fragment.optionalBuildings()) {
                SupplyGraph.Building known = GRAPH.buildings().get(building);
                assertTrue(
                        known != null && known.optional(),
                        fragment.area() + ": новая постройка необязательна: " + building
                                + " Иначе тест fragments_ProductionData_OptionalBuildingsAreImplemented перестанет ловить эту поломку. При смене fragments_ProductionData_OptionalBuildingsAreImplemented проверь это утверждение.");
                assertTrue(
                        GRAPH.implemented(building),
                        fragment.area() + ": действие постройки исполняется: " + building
                                + " Иначе тест fragments_ProductionData_OptionalBuildingsAreImplemented перестанет ловить эту поломку. При смене fragments_ProductionData_OptionalBuildingsAreImplemented проверь это утверждение.");
                assertTrue(
                        known.base() > 0,
                        fragment.area() + ": стартовый вес постройки положителен: " + building
                                + " Иначе тест fragments_ProductionData_OptionalBuildingsAreImplemented перестанет ловить эту поломку. При смене fragments_ProductionData_OptionalBuildingsAreImplemented проверь это утверждение.");
            }
        }
    }

    @Test
    void fragments_ExistingEvent_StaysImplemented() {
        assertTrue(
                GRAPH.events().get("crop_ready").tasks().get(0).implemented(),
                "Старые события без флага implemented остаются готовыми: сбор урожая уже работает"
                        + " Иначе тест fragments_ExistingEvent_StaysImplemented перестанет ловить эту поломку. При смене fragments_ExistingEvent_StaysImplemented проверь это утверждение.");
    }

    @Test
    void fragments_LifePlayerFact_SupersedesMore5LegacyEvent() {
        assertFalse(
                GRAPH.events().containsKey("more5_player_weak"),
                "Легаси-событие защиты игрока удалено: слабого игрока ведёт жизнь с фактом здоровья (debtlife)"
                        + " Иначе тест fragments_LifePlayerFact_SupersedesMore5LegacyEvent перестанет ловить эту поломку. При смене fragments_LifePlayerFact_SupersedesMore5LegacyEvent проверь это утверждение.");
        GraphEvent player = GRAPH.events().get("life_threat_player");
        assertTrue(
                player.life(),
                "Жизнь игрока остаётся узлом жизни с потолком 100"
                        + " Иначе тест fragments_LifePlayerFact_SupersedesMore5LegacyEvent перестанет ловить эту поломку. При смене fragments_LifePlayerFact_SupersedesMore5LegacyEvent проверь это утверждение.");
        assertTrue(
                player.tasks().stream().anyMatch(task -> "life_protect_player".equals(task.id())),
                "Защита игрока осталась заданием жизни: " + player.tasks()
                        + " Иначе тест fragments_LifePlayerFact_SupersedesMore5LegacyEvent перестанет ловить эту поломку. При смене fragments_LifePlayerFact_SupersedesMore5LegacyEvent проверь это утверждение.");
        assertTrue(
                player.tasks().stream().anyMatch(task -> "life_feed_player".equals(task.id())),
                "Кормление игрока переехало в жизнь, а не потерялось: " + player.tasks()
                        + " Иначе тест fragments_LifePlayerFact_SupersedesMore5LegacyEvent перестанет ловить эту поломку. При смене fragments_LifePlayerFact_SupersedesMore5LegacyEvent проверь это утверждение.");
    }

    private static boolean known(String node) {
        if (node.startsWith("building:")) {
            return GRAPH.buildings().containsKey(node.substring("building:".length()));
        }
        return GRAPH.items().containsKey(node) || GRAPH.isGroup(node) || GRAPH.isTool(node);
    }
}
