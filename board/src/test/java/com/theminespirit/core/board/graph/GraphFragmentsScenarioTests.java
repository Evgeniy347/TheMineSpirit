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
                assertTrue(GRAPH.events().containsKey(event), fragment.area() + ": событие объявлено: " + event);
                GraphEvent known = GRAPH.events().get(event);
                assertTrue(
                        fragment.tasksRequired()
                                ? !known.tasks().isEmpty()
                                : !known.tasks().isEmpty() || !known.push().isEmpty(),
                        fragment.area() + ": событие " + event + " без работы не двигает граф");
            }
            for (String event : fragment.absentEvents()) {
                assertFalse(
                        GRAPH.events().containsKey(event),
                        fragment.area() + ": удалённое событие не вернулось: " + event);
            }
        }
    }

    @Test
    void fragments_ProductionData_WaysAreWiredOrStubWithPositiveWeight() {
        for (Fragment fragment : ALL) {
            for (WayCase expected : fragment.ways()) {
                SupplyGraph.Way way = GRAPH.way(expected.id())
                        .orElseThrow(() -> new AssertionError(fragment.area() + ": путь объявлен: " + expected.id()));
                assertTrue(way.boardCase() != null, fragment.area() + ": у пути есть вид работы: " + expected.id());
                assertEquals(
                        expected.implemented(),
                        GRAPH.implemented(expected.id()),
                        fragment.area() + ": путь связан с действием или честно помечен заглушкой: " + expected.id());
                if (expected.makes() != null) {
                    assertEquals(
                            expected.makes(),
                            way.makes(),
                            fragment.area() + ": путь делает свой узел: " + expected.id());
                    assertTrue(
                            GRAPH.producers(expected.makes()).contains(way),
                            fragment.area() + ": узел " + expected.makes() + " знает своего производителя");
                }
            }
            for (String absent : fragment.absentWays()) {
                assertTrue(
                        GRAPH.way(absent).isEmpty(), fragment.area() + ": дубль или правило не висит путём: " + absent);
            }
        }
    }

    @Test
    void fragments_ProductionData_ItemsHavePositiveStartWeights() {
        for (Fragment fragment : ALL) {
            for (String item : fragment.items()) {
                assertTrue(
                        GRAPH.items().get(item).base() > 0,
                        fragment.area() + ": стартовый вес предмета положителен: " + item);
            }
            for (String item : fragment.zeroDemandItems()) {
                assertEquals(
                        0,
                        GRAPH.items().get(item).xmin(),
                        fragment.area() + ": вход не создаёт спрос сам по себе: " + item);
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
                        fragment.area() + ": имена событий области не повторяются");
                for (String event : fragment.events()) {
                    assertTrue(
                            event.startsWith(fragment.prefix()),
                            fragment.area() + ": событие названо с префиксом " + fragment.prefix() + ": " + event);
                }
            }
            for (String event : fragment.events()) {
                if (!fragment.tasksAreWays()) {
                    continue;
                }
                for (GraphEvent.Task task : GRAPH.events().get(event).tasks()) {
                    assertTrue(
                            GRAPH.way(task.id()).isPresent(),
                            fragment.area() + ": задача " + event + " -> " + task.id() + " должна быть путём");
                    assertEquals(
                            GRAPH.implemented(task.id()),
                            task.implemented(),
                            fragment.area() + ": задача " + event + " -> " + task.id()
                                    + " не расходится с состоянием пути");
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
                        .forEach(node -> assertTrue(
                                known(node), fragment.area() + ": " + event + " толкает неизвестный узел " + node));
                known.tasks()
                        .forEach(task -> task.consumes()
                                .keySet()
                                .forEach(item -> assertTrue(
                                        known(item),
                                        fragment.area() + ": " + event + " расходует неизвестное " + item)));
            }
        }
    }

    @Test
    void fragments_ProductionData_GroupsAreDeclaredInOwnFile() {
        for (Fragment fragment : ALL) {
            for (GroupCase group : fragment.groups()) {
                assertTrue(GRAPH.isGroup(group.name()), fragment.area() + ": группа объявлена: " + group.name());
                if (group.exact()) {
                    assertEquals(
                            group.members(),
                            GRAPH.members(group.name()),
                            fragment.area() + ": состав группы " + group.name() + " объявлен один раз и в своём файле");
                } else {
                    assertTrue(
                            GRAPH.members(group.name()).containsAll(group.members()),
                            fragment.area() + ": группа " + group.name() + " содержит " + group.members());
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
                        fragment.area() + ": новая постройка необязательна: " + building);
                assertTrue(
                        GRAPH.implemented(building), fragment.area() + ": действие постройки исполняется: " + building);
                assertTrue(known.base() > 0, fragment.area() + ": стартовый вес постройки положителен: " + building);
            }
        }
    }

    @Test
    void fragments_ExistingEvent_StaysImplemented() {
        assertTrue(
                GRAPH.events().get("crop_ready").tasks().get(0).implemented(),
                "Старые события без флага implemented остаются готовыми: сбор урожая уже работает");
    }

    @Test
    void fragments_LifePlayerFact_SupersedesMore5LegacyEvent() {
        assertFalse(
                GRAPH.events().containsKey("more5_player_weak"),
                "Легаси-событие защиты игрока удалено: слабого игрока ведёт жизнь с фактом здоровья (debtlife)");
        GraphEvent player = GRAPH.events().get("life_threat_player");
        assertTrue(player.life(), "Жизнь игрока остаётся узлом жизни с потолком 100");
        assertTrue(
                player.tasks().stream().anyMatch(task -> "life_protect_player".equals(task.id())),
                "Защита игрока осталась заданием жизни: " + player.tasks());
        assertTrue(
                player.tasks().stream().anyMatch(task -> "life_feed_player".equals(task.id())),
                "Кормление игрока переехало в жизнь, а не потерялось: " + player.tasks());
    }

    private static boolean known(String node) {
        if (node.startsWith("building:")) {
            return GRAPH.buildings().containsKey(node.substring("building:".length()));
        }
        return GRAPH.items().containsKey(node) || GRAPH.isGroup(node) || GRAPH.isTool(node);
    }
}
