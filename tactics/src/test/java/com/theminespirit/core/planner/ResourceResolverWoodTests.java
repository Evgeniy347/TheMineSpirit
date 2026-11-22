package com.theminespirit.core.planner;

import static com.theminespirit.core.planner.ResolverWood.chop;
import static com.theminespirit.core.planner.ResolverWood.gather;
import static com.theminespirit.core.planner.ResolverWood.trees;
import static com.theminespirit.core.planner.ResolverWood.wood;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.planner.api.Ingredient;
import com.theminespirit.core.planner.api.ResourceKnowledge;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ResourceResolverWoodTests {
    private static final int STATION_RADIUS = 16;

    private static final ResourceKnowledge KNOWLEDGE =
            new StubKnowledge(Set.of("oak_log"), Map.of("stick", List.of(new Ingredient("oak_log", 2))), Map.of());

    @Test
    void resolve_Log_ChopsInWideRadius() {
        TaskNode plan = CorePlanner.resolve("oak_log", 2, KNOWLEDGE);
        assertEquals(
                wood("oak_log", 2),
                plan,
                "Брёвна рубятся деревом в радиусе 32: после расчистки участка деревья в 16 от дома вырублены, "
                        + "иначе план не находит дерево и падает. При смене радиуса добычи брёвен проверь строку "
                        + "запасного gather ниже в тесте");
        FallbackNode fallback = assertInstanceOf(
                FallbackNode.class,
                plan,
                "Бревно сначала рубится деревом, а сбор остаётся запасным: иначе при отсутствии дерева добыча не "
                        + "доберёт материал. При смене структуры плана для брёвен проверь строку gather ниже в тесте");
        assertEquals(
                gather("oak_log", 2),
                fallback.steps().get(1),
                "Запасной gather обязан нести тот же count и радиус 32: иначе при отсутствии деревьев дух не доберёт "
                        + "материал и ремонт встанет. При смене fallback для брёвен проверь строку рубки выше в тесте");
    }

    @Test
    void resolve_LogFallback_GatherKeepsSameCount() {
        TaskNode plan =
                CorePlanner.resolve("jungle_log", 3, new StubKnowledge(Set.of("jungle_log"), Map.of(), Map.of()));
        FallbackNode fallback = assertInstanceOf(
                FallbackNode.class,
                plan,
                "Бревно любой породы планируется через рубку с запасным сбором: иначе джунглевое бревно снова "
                        + "собирается поштучно и оставляет листву. При смене пород проверь строку count ниже в тесте");
        assertEquals(
                gather("jungle_log", 3),
                fallback.steps().get(1),
                "Запасной сбор несёт ровно запрошенное число брёвен: иначе недобор оставит ремонт без материала, "
                        + "а перебор переполнит инвентарь. При смене count в fallback проверь строку породы выше в тесте");
    }

    @Test
    void resolve_NonLog_GathersGeneric() {
        TaskNode plan = CorePlanner.resolve("sand", 2, new StubKnowledge(Set.of("sand"), Map.of(), Map.of()));
        assertEquals(
                gather("sand", 2),
                plan,
                "Не-бревно (песок) остаётся поштучным сбором: рубка дерева здесь не нашла бы цель и план встал бы, "
                        + "иначе любая добыча песка падала бы. При смене проверки породы проверь строку бревна выше в тесте");
    }

    @Test
    void resolve_FiveLogs_PlansTwoTrees() {
        FallbackNode four = assertInstanceOf(
                FallbackNode.class,
                CorePlanner.resolve("oak_log", 4, KNOWLEDGE),
                "Четыре бревна укладываются в одно дерево: иначе план рубит лишнее дерево впустую. При смене оценки "
                        + "брёвен на дерево проверь строку пяти брёвен ниже в тесте");
        assertEquals(
                chop("oak", 1),
                four.steps().get(0),
                "Четыре бревна — одно дерево: переруб тратит лес зря, иначе сад редеет без нужды. При смене "
                        + "LOGS_PER_TREE проверь строку пяти брёвен ниже в тесте");
        FallbackNode five = assertInstanceOf(
                FallbackNode.class,
                CorePlanner.resolve("oak_log", 5, KNOWLEDGE),
                "Пять брёвен требуют второго дерева: иначе плану не хватает материала и крафт после добычи падает. "
                        + "При смене округления деревьев проверь строку четырёх брёвен выше в тесте");
        assertEquals(
                chop("oak", 2),
                five.steps().get(0),
                "Пять брёвен округляются вверх до двух деревьев: иначе пяти брёвен из одного дерева не хватит и "
                        + "ремонт встанет на нехватке. При смене округления проверь строку четырёх брёвен выше в тесте");
    }

    @Test
    void resolve_CraftChain_ChopPrecedesLogGather() {
        TaskNode plan = CorePlanner.resolve("stick", 1, KNOWLEDGE);
        SequenceNode sequence = assertInstanceOf(
                SequenceNode.class,
                plan,
                "Палки планируются из брёвен: иначе цепочка не начинается с добычи дерева и крафт встанет. При смене "
                        + "craft-ветки проверь строку рубки ниже в тесте");
        FallbackNode wood = assertInstanceOf(
                FallbackNode.class,
                sequence.steps().get(0),
                "Внутри craft-ветки бревно добывается рубкой, а не gather: иначе дух рубит ствол снизу вверх и "
                        + "оставляет листву, как в живом случае repair:build_house. При смене порядка шагов проверь "
                        + "строку скрипта ниже в тесте");
        assertEquals(
                "chop_tree",
                ((ScriptCall) wood.steps().get(0)).script(),
                "Первым шагом добычи брёвен идёт chop_tree: иначе крона не снимается и листва висит над стволом. "
                        + "При смене порядка fallback проверь строку породы выше в тесте");
    }

    @Test
    void resolve_IntermediateDemandOverLimit_ChopsAndBatchesGather() {
        ResourceKnowledge knowledge =
                new StubKnowledge(Set.of("oak_log"), Map.of("stick", List.of(new Ingredient("oak_log", 40))), Map.of());
        TaskNode plan = CorePlanner.resolve("stick", 8, knowledge);
        assertEquals(
                new SequenceNode(List.of(
                        new FallbackNode(List.of(
                                chop("oak", trees(320)),
                                new SequenceNode(List.of(gather("oak_log", 256), gather("oak_log", 64))))),
                        craft("stick", 8))),
                plan,
                "Промежуточное требование 320 (8 крафтов по 40 бревна) обязано разбиваться на вызовы в пределах "
                        + "лимита 256: иначе валидатор ронял бы весь план внутренней ошибкой, а дух оставался бы "
                        + "без плана на достижимую цель. Финальный крафт при этом один, на 8 При смене "
                        + "resolve_IntermediateDemandOverLimit_ChopsAndBatchesGather проверь это утверждение.");
    }

    private static ScriptCall craft(String item, int count) {
        return new ScriptCall("craft", Map.of("item", item, "count", count, "radius", STATION_RADIUS, "place", false));
    }
}
