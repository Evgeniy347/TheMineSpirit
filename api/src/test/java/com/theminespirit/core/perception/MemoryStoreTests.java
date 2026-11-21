package com.theminespirit.core.perception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class MemoryStoreTests {
    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";
    private static final MemoryPoint IRON = point("iron", MemoryType.RESOURCE, 1);
    private static final MemoryPoint LAVA = point("lava_lake", MemoryType.DANGER, 2);
    private static final MemoryPoint HOME = point("home", MemoryType.BASE, 3);

    private static MemoryPoint point(String name, MemoryType type, int x) {
        return new MemoryPoint(name, type, OVERWORLD, new BlockCoord(x, 64, x));
    }

    private static MemoryStore filledWith(MemoryType type, int count) {
        MemoryStore store = MemoryStore.empty();
        for (int i = 0; i < count; i++) {
            store = store.remember(point(type.id() + i, type, i));
        }
        return store;
    }

    static Stream<Arguments> recalls() {
        return Stream.of(
                Arguments.of(List.of(), "iron", null),
                Arguments.of(List.of(IRON), "iron", IRON),
                Arguments.of(List.of(IRON), "gold", null),
                Arguments.of(List.of(IRON, LAVA, HOME), "lava_lake", LAVA),
                Arguments.of(List.of(IRON, LAVA, HOME), "home", HOME),
                Arguments.of(List.of(IRON), "Iron", null));
    }

    @ParameterizedTest(name = "точки={0}, имя={1} -> {2}")
    @MethodSource("recalls")
    void recall_NameInStore_FindsExactlyThatPointElseNull(List<MemoryPoint> points, String name, MemoryPoint expected) {
        assertEquals(
                expected,
                new MemoryStore(points).recall(name),
                "recall обязан вернуть точку с этим именем, а для неизвестного (и иного регистра) имени — null: "
                        + "стратег отличает «не помню» от «помню»"
                        + " Иначе тест recall_NameInStore_FindsExactlyThatPointElseNull перестанет ловить эту поломку. При смене recall_NameInStore_FindsExactlyThatPointElseNull проверь это утверждение.");
    }

    @Test
    void recallAll_SeveralTypes_ReturnsOnlyRequestedTypeInInsertionOrder() {
        MemoryPoint secondIron = point("iron2", MemoryType.RESOURCE, 9);
        MemoryStore store = new MemoryStore(List.of(IRON, LAVA, secondIron, HOME));

        assertEquals(
                List.of(IRON, secondIron),
                store.recallAll(MemoryType.RESOURCE),
                "recallAll(resource) обязан вернуть только ресурсы в порядке запоминания"
                        + " Иначе тест recallAll_SeveralTypes_ReturnsOnlyRequestedTypeInInsertionOrder перестанет ловить эту поломку. При смене recallAll_SeveralTypes_ReturnsOnlyRequestedTypeInInsertionOrder проверь это утверждение.");
        assertEquals(
                List.of(LAVA),
                store.recallAll(MemoryType.DANGER),
                "recallAll(danger) обязан вернуть только опасности"
                        + " Иначе тест recallAll_SeveralTypes_ReturnsOnlyRequestedTypeInInsertionOrder перестанет ловить эту поломку. При смене recallAll_SeveralTypes_ReturnsOnlyRequestedTypeInInsertionOrder проверь это утверждение.");
        assertEquals(
                List.of(),
                MemoryStore.empty().recallAll(MemoryType.BASE),
                "в пустой памяти баз нет"
                        + " Иначе тест recallAll_SeveralTypes_ReturnsOnlyRequestedTypeInInsertionOrder перестанет ловить эту поломку. При смене recallAll_SeveralTypes_ReturnsOnlyRequestedTypeInInsertionOrder проверь это утверждение.");
    }

    @Test
    void remember_SameName_ReplacesPointInPlaceWithoutGrowing() {
        MemoryStore store = new MemoryStore(List.of(IRON, LAVA));
        MemoryPoint moved = new MemoryPoint("iron", MemoryType.RESOURCE, NETHER, new BlockCoord(50, 10, 50));

        MemoryStore updated = store.remember(moved);

        assertEquals(
                List.of(moved, LAVA),
                updated.all(),
                "повтор имени обязан заменить точку на месте, а не добавить вторую: имя однозначно определяет точку"
                        + " Иначе тест remember_SameName_ReplacesPointInPlaceWithoutGrowing перестанет ловить эту поломку. При смене remember_SameName_ReplacesPointInPlaceWithoutGrowing проверь это утверждение.");
        assertEquals(
                List.of(IRON, LAVA),
                store.all(),
                "исходная память не должна меняться: remember возвращает новую"
                        + " Иначе тест remember_SameName_ReplacesPointInPlaceWithoutGrowing перестанет ловить эту поломку. При смене remember_SameName_ReplacesPointInPlaceWithoutGrowing проверь это утверждение.");
    }

    @Test
    void remember_SameNameOtherType_PointMovesToNewType() {
        MemoryPoint asDanger = new MemoryPoint("iron", MemoryType.DANGER, OVERWORLD, new BlockCoord(1, 64, 1));

        MemoryStore updated = new MemoryStore(List.of(IRON)).remember(asDanger);

        assertEquals(
                List.of(),
                updated.recallAll(MemoryType.RESOURCE),
                "старая запись типа resource должна исчезнуть"
                        + " Иначе тест remember_SameNameOtherType_PointMovesToNewType перестанет ловить эту поломку. При смене remember_SameNameOtherType_PointMovesToNewType проверь это утверждение.");
        assertEquals(
                List.of(asDanger),
                updated.recallAll(MemoryType.DANGER),
                "точка должна перейти в новый тип"
                        + " Иначе тест remember_SameNameOtherType_PointMovesToNewType перестанет ловить эту поломку. При смене remember_SameNameOtherType_PointMovesToNewType проверь это утверждение.");
    }

    @Test
    void remember_PointFromOtherDimension_KeepsItsDimension() {
        MemoryPoint inNether = new MemoryPoint("fortress", MemoryType.BASE, NETHER, new BlockCoord(7, 70, 7));

        assertEquals(
                NETHER,
                MemoryStore.empty().remember(inNether).recall("fortress").dimension(),
                "точка обязана помнить измерение, где её запомнили: recall из другого измерения решает стратег"
                        + " Иначе тест remember_PointFromOtherDimension_KeepsItsDimension перестанет ловить эту поломку. При смене remember_PointFromOtherDimension_KeepsItsDimension проверь это утверждение.");
    }

    @Test
    void remember_TypeAtLimit_NextPointFailsNamingTheType() {
        assertEquals(
                16,
                MemoryStore.MAX_PER_TYPE,
                "предел точек одного типа согласован: 16 (поправка П2)"
                        + " Иначе тест remember_TypeAtLimit_NextPointFailsNamingTheType перестанет ловить эту поломку. При смене remember_TypeAtLimit_NextPointFailsNamingTheType проверь это утверждение.");
        MemoryStore full = filledWith(MemoryType.RESOURCE, 16);

        MemoryFullException error = assertThrows(
                MemoryFullException.class,
                () -> full.remember(point("one_more", MemoryType.RESOURCE, 99)),
                "17-я точка типа обязана отклоняться ошибкой, а не вытеснять старую молча"
                        + " Иначе тест remember_TypeAtLimit_NextPointFailsNamingTheType перестанет ловить эту поломку. При смене remember_TypeAtLimit_NextPointFailsNamingTheType проверь это утверждение.");

        assertEquals(
                MemoryType.RESOURCE,
                error.type(),
                "ошибка обязана называть заполненный тип"
                        + " Иначе тест remember_TypeAtLimit_NextPointFailsNamingTheType перестанет ловить эту поломку. При смене remember_TypeAtLimit_NextPointFailsNamingTheType проверь это утверждение.");
        assertTrue(
                error.getMessage().contains("resource") && error.getMessage().contains("забудь"),
                "сообщение обязано называть тип и что делать (забыть точку): " + error.getMessage()
                        + " Иначе тест remember_TypeAtLimit_NextPointFailsNamingTheType перестанет ловить эту поломку. При смене remember_TypeAtLimit_NextPointFailsNamingTheType проверь это утверждение.");
        assertEquals(
                16,
                full.recallAll(MemoryType.RESOURCE).size(),
                "после отказа память не должна меняться"
                        + " Иначе тест remember_TypeAtLimit_NextPointFailsNamingTheType перестанет ловить эту поломку. При смене remember_TypeAtLimit_NextPointFailsNamingTheType проверь это утверждение.");
    }

    @Test
    void remember_TypeAtLimit_OtherTypesAndReplacementsStillWork() {
        MemoryStore full = filledWith(MemoryType.RESOURCE, 16);

        MemoryStore withDanger = full.remember(LAVA);
        MemoryPoint replaced = point("resource3", MemoryType.RESOURCE, 77);
        MemoryStore replacedStore = full.remember(replaced);

        assertEquals(
                List.of(LAVA),
                withDanger.recallAll(MemoryType.DANGER),
                "заполненный тип не должен блокировать другие типы: пределы раздельные"
                        + " Иначе тест remember_TypeAtLimit_OtherTypesAndReplacementsStillWork перестанет ловить эту поломку. При смене remember_TypeAtLimit_OtherTypesAndReplacementsStillWork проверь это утверждение.");
        assertEquals(
                16,
                replacedStore.recallAll(MemoryType.RESOURCE).size(),
                "замена по имени в заполненном типе не считается новой точкой"
                        + " Иначе тест remember_TypeAtLimit_OtherTypesAndReplacementsStillWork перестанет ловить эту поломку. При смене remember_TypeAtLimit_OtherTypesAndReplacementsStillWork проверь это утверждение.");
        assertEquals(
                replaced,
                replacedStore.recall("resource3"),
                "замена обязана подменить точку"
                        + " Иначе тест remember_TypeAtLimit_OtherTypesAndReplacementsStillWork перестанет ловить эту поломку. При смене remember_TypeAtLimit_OtherTypesAndReplacementsStillWork проверь это утверждение.");
    }

    @Test
    void remember_ExistingPointMovedToFullType_Fails() {
        MemoryStore store = filledWith(MemoryType.RESOURCE, 16).remember(LAVA);
        MemoryPoint lavaAsResource =
                new MemoryPoint("lava_lake", MemoryType.RESOURCE, OVERWORLD, new BlockCoord(2, 64, 2));

        assertThrows(
                MemoryFullException.class,
                () -> store.remember(lavaAsResource),
                "смена типа существующей точки на заполненный тип — это новая точка этого типа: предел обязан сработать"
                        + " Иначе тест remember_ExistingPointMovedToFullType_Fails перестанет ловить эту поломку. При смене remember_ExistingPointMovedToFullType_Fails проверь это утверждение.");
    }

    @Test
    void forget_PointPresent_RemovesOnlyThatPoint() {
        MemoryStore store = new MemoryStore(List.of(IRON, LAVA, HOME));

        MemoryStore after = store.forget("lava_lake");

        assertNull(
                after.recall("lava_lake"),
                "забытая точка не должна находиться"
                        + " Иначе тест forget_PointPresent_RemovesOnlyThatPoint перестанет ловить эту поломку. При смене forget_PointPresent_RemovesOnlyThatPoint проверь это утверждение.");
        assertEquals(
                List.of(IRON, HOME),
                after.all(),
                "остальные точки обязаны остаться в прежнем порядке"
                        + " Иначе тест forget_PointPresent_RemovesOnlyThatPoint перестанет ловить эту поломку. При смене forget_PointPresent_RemovesOnlyThatPoint проверь это утверждение.");
    }

    @Test
    void forget_UnknownName_ReturnsSameStoreWithoutError() {
        MemoryStore store = new MemoryStore(List.of(IRON));

        assertSame(
                store,
                store.forget("nothing"),
                "забыть неизвестное имя — не ошибка и не изменение"
                        + " Иначе тест forget_UnknownName_ReturnsSameStoreWithoutError перестанет ловить эту поломку. При смене forget_UnknownName_ReturnsSameStoreWithoutError проверь это утверждение.");
        assertSame(
                MemoryStore.empty(),
                MemoryStore.empty().forget("nothing"),
                "в пустой памяти забывать нечего, ошибки нет"
                        + " Иначе тест forget_UnknownName_ReturnsSameStoreWithoutError перестанет ловить эту поломку. При смене forget_UnknownName_ReturnsSameStoreWithoutError проверь это утверждение.");
    }

    @Test
    void constructor_DuplicateNames_IsRejected() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new MemoryStore(List.of(IRON, point("iron", MemoryType.BASE, 5))),
                "две точки с одним именем в памяти недопустимы: recall не знал бы, какую вернуть"
                        + " Иначе тест constructor_DuplicateNames_IsRejected перестанет ловить эту поломку. При смене constructor_DuplicateNames_IsRejected проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("iron"),
                "отказ обязан называть имя: " + error.getMessage()
                        + " Иначе тест constructor_DuplicateNames_IsRejected перестанет ловить эту поломку. При смене constructor_DuplicateNames_IsRejected проверь это утверждение.");
    }

    @Test
    void constructor_MoreThanLimitInList_IsRejected() {
        List<MemoryPoint> tooMany =
                new ArrayList<>(filledWith(MemoryType.DANGER, 16).all());
        tooMany.add(point("extra", MemoryType.DANGER, 50));

        assertThrows(
                MemoryFullException.class,
                () -> new MemoryStore(tooMany),
                "память, собранная списком сверх предела типа, обязана отклоняться так же, как remember"
                        + " Иначе тест constructor_MoreThanLimitInList_IsRejected перестанет ловить эту поломку. При смене constructor_MoreThanLimitInList_IsRejected проверь это утверждение.");
    }

    @Test
    void totalCapacity_AllTypesFull_IsFortyEight() {
        MemoryStore store = filledWith(MemoryType.RESOURCE, 16);
        for (MemoryType type : List.of(MemoryType.DANGER, MemoryType.BASE)) {
            for (MemoryPoint point : filledWith(type, 16).all()) {
                store = store.remember(point);
            }
        }

        assertEquals(
                48,
                store.all().size(),
                "три типа по 16 точек — всего 48 (поправка П2)"
                        + " Иначе тест totalCapacity_AllTypesFull_IsFortyEight перестанет ловить эту поломку. При смене totalCapacity_AllTypesFull_IsFortyEight проверь это утверждение.");
    }
}
