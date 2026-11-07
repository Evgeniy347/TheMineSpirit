package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.board.graph.GraphFacts;
import com.theminespirit.core.board.graph.GraphFactsUpdates;
import com.theminespirit.core.board.graph.GraphFeedback;
import com.theminespirit.core.board.graph.GraphSiteFacts;
import com.theminespirit.core.board.graph.GraphWorldFacts;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class GraphFactsCopyGuardTests {
    private static final Map<String, Function<GraphFacts, Object>> REPLACEMENTS = Map.of(
            "withSpirits", GraphFacts::spirits,
            "withThreats", GraphFacts::threats,
            "withChests", GraphFacts::chests,
            "withFeedback", GraphFacts::feedback,
            "withWorld", GraphFactsCopyGuardTests::world,
            "withStations", GraphFacts::stations,
            "withStageReqs", GraphFacts::stageReqs);

    @Test
    void graphFactsCopies_ReplacedWithSameValue_KeepEveryComponent() throws ReflectiveOperationException {
        GraphFacts base = base();
        Method[] copies = Arrays.stream(GraphFacts.class.getMethods())
                .filter(method -> method.getName().startsWith("with") && method.getReturnType() == GraphFacts.class)
                .toArray(Method[]::new);
        assertEquals(
                new TreeSet<>(REPLACEMENTS.keySet())
                        .stream()
                                .filter(name -> !"withStations".equals(name) && !"withStageReqs".equals(name))
                                .toList(),
                Arrays.stream(copies).map(Method::getName).sorted().toList(),
                "Появился новый with* у GraphFacts, который не проверен сторожем: копия может молча потерять поле "
                        + "(так уже терялась обратная связь мира, и память неудач не доходила до графа). "
                        + "Добавь with* в REPLACEMENTS, чтобы сторож вызвал его с тем же значением и сверил запись.");
        for (Method copy : copies) {
            GraphFacts next = (GraphFacts)
                    copy.invoke(base, REPLACEMENTS.get(copy.getName()).apply(base));
            assertEquals(
                    base,
                    next,
                    "Копия " + copy.getName() + " потеряла компонент GraphFacts: замена тем же значением обязана "
                            + "вернуть ту же запись. Потерянный компонент — это скрытая пустота во входе графа: "
                            + "решение принимается по неполным фактам, и виноват не вес, а копия.");
        }
    }

    @Test
    void graphFactsUpdates_ReplacedWithSameValue_KeepEveryComponent() throws ReflectiveOperationException {
        GraphFacts base = base();
        List<Method> copies = Arrays.stream(GraphFactsUpdates.class.getDeclaredMethods())
                .filter(method ->
                        Modifier.isStatic(method.getModifiers()) && method.getReturnType() == GraphFacts.class)
                .toList();
        assertEquals(
                List.of("withStageReqs", "withStations"),
                copies.stream().map(Method::getName).sorted().toList(),
                "Появился новый копирующий helper в GraphFactsUpdates, который не проверен сторожем: он собирает "
                        + "GraphFacts вручную и может потерять поле (так уже терялась обратная связь мира). "
                        + "Добавь его в REPLACEMENTS и проверь равенством записи.");
        for (Method copy : copies) {
            GraphFacts next = (GraphFacts)
                    copy.invoke(null, base, REPLACEMENTS.get(copy.getName()).apply(base));
            assertEquals(
                    base,
                    next,
                    "Копия " + copy.getName() + " потеряла компонент GraphFacts: замена тем же значением обязана "
                            + "вернуть ту же запись. Потерянный компонент — это скрытая пустота во входе графа.");
        }
    }

    @Test
    void graphFacts_ShortConstructor_IsAbsent() {
        List<Constructor<?>> constructors =
                Arrays.stream(GraphFacts.class.getConstructors()).toList();
        assertEquals(
                List.of(10),
                constructors.stream().map(Constructor::getParameterCount).toList(),
                "У GraphFacts есть конструктор не на все 10 компонентов: короткий конструктор подставляет "
                        + "feedback = none(), и память неудач молча не доходит до графа. Компоненты задают все "
                        + "десять, явно.");
        assertEquals(
                10,
                GraphFacts.class.getRecordComponents().length,
                "Компоненты GraphFacts читаются рефлексией: сторож перечисляет их и сверяет каждую копию");
    }

    private static GraphWorldFacts world(GraphFacts base) {
        return new GraphWorldFacts(base.strays(), base.terrain(), base.buildings());
    }

    private static GraphFacts.Threat threat() {
        return new GraphFacts.Threat(
                "threat-1",
                "zombie",
                10,
                20,
                new GraphFacts.Position(3, 64, 4),
                "spirit-1",
                GraphFacts.TargetKind.SPIRIT,
                true,
                true,
                "attack",
                5,
                null,
                0.2);
    }

    private static GraphFacts base() {
        return new GraphFacts(
                Map.of(
                        "spirit-1",
                        new GraphFacts.Spirit(
                                "spirit-1",
                                new GraphFacts.Position(1, 64, 2),
                                20,
                                Math.max(20, 1f),
                                null,
                                18,
                                300,
                                true,
                                false,
                                true,
                                "supply_gather",
                                Map.of("minecraft:oak_log", 2),
                                List.of(new GraphFacts.Armor("HEAD", "minecraft:iron_helmet", 100, 165)),
                                List.of(new GraphFacts.Tool("minecraft:iron_pickaxe", "pickaxe", 2, 200, 250)),
                                null)),
                List.of(threat()),
                List.of(new GraphFacts.Chest(new GraphFacts.Position(5, 64, 6), Map.of("minecraft:coal", 8), 5, 27)),
                List.of(new GraphFacts.Resource(
                        "tree:oak", new GraphFacts.Position(7, 64, 8), 9, GraphFacts.Reach.PATH, 18)),
                Map.of(
                        "furnace",
                        new GraphFacts.Station(
                                "furnace",
                                "furnace",
                                new GraphFacts.Position(9, 64, 10),
                                true,
                                "coal",
                                "ore",
                                "ingot")),
                Map.of(
                        "hut",
                        new GraphFacts.Building(
                                "hut",
                                1,
                                80,
                                3,
                                Map.of("minecraft:dirt", 2),
                                Map.of("minecraft:stone", 1),
                                1,
                                new GraphFacts.Position(11, 64, 12),
                                false,
                                true)),
                List.of(new GraphSiteFacts.Stray(
                        "stray:fence",
                        "fence",
                        new GraphFacts.Position(13, 64, 14),
                        "spirit-1",
                        Map.of("oak_fence", 3))),
                new GraphSiteFacts.Terrain(
                        2,
                        4,
                        3,
                        5,
                        1,
                        Map.of("minecraft:dirt", 2),
                        Map.of("minecraft:dirt", 2),
                        Map.of("minecraft:oak_log", 1),
                        false,
                        9),
                new GraphSiteFacts.StageReqs(Map.of("hut", 1), Map.of("hut", 1)),
                new GraphFeedback(
                        new GraphFeedback.Clock(true, true, false, 13000),
                        new GraphFeedback.Player(new GraphFacts.Position(1, 64, 1), true, true, true, false),
                        List.of(new GraphFeedback.Failure(
                                "make:dirt", "0,64,0", 2, GraphFeedback.Cause.NO_PATH, 100, 1.5))));
    }
}
