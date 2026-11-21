package com.theminespirit.core.town;

import static com.theminespirit.core.town.FloatingBlocksTests.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class FloatingBlocksTestsExtraTests {

    @Test
    void countedInGraph_Buildings_ExcludedFromHanging() {
        Fake world = new Fake();
        world.put(2, 67, 2, "minecraft:chest");
        world.put(3, 68, 3, "minecraft:furnace");
        world.put(4, 68, 4, "minecraft:crafting_table");
        world.put(5, 66, 5, "minecraft:oak_fence");
        world.put(6, 70, 6, "minecraft:oak_planks");

        assertFalse(
                FloatingBlocks.countedInGraph(world.only(2, 67, 2)),
                "Сундук — постройка/станция, а не висящий блок: [graph] висящее его не считает (observe24: "
                        + "в составе висящего chest/furnace/crafting_table)"
                        + " Иначе тест countedInGraph_Buildings_ExcludedFromHanging перестанет ловить эту поломку. При смене countedInGraph_Buildings_ExcludedFromHanging проверь это утверждение.");
        assertFalse(
                FloatingBlocks.countedInGraph(world.only(3, 68, 3)),
                "Печь — станция поселения, а не мусор: граф не должен звать её в уборку висящего"
                        + " Иначе тест countedInGraph_Buildings_ExcludedFromHanging перестанет ловить эту поломку. При смене countedInGraph_Buildings_ExcludedFromHanging проверь это утверждение.");
        assertFalse(
                FloatingBlocks.countedInGraph(world.only(4, 68, 4)),
                "Верстак — станция поселения, а не висящий блок: граф не должен звать его в уборку висящего"
                        + " Иначе тест countedInGraph_Buildings_ExcludedFromHanging перестанет ловить эту поломку. При смене countedInGraph_Buildings_ExcludedFromHanging проверь это утверждение.");
        assertTrue(
                FloatingBlocks.countedInGraph(world.only(5, 66, 5)),
                "Забор без опоры — висящее"
                        + " Иначе тест countedInGraph_Buildings_ExcludedFromHanging перестанет ловить эту поломку. При смене countedInGraph_Buildings_ExcludedFromHanging проверь это утверждение.");
        assertTrue(
                FloatingBlocks.countedInGraph(world.only(6, 70, 6)),
                "Доска без опоры — висящее"
                        + " Иначе тест countedInGraph_Buildings_ExcludedFromHanging перестанет ловить эту поломку. При смене countedInGraph_Buildings_ExcludedFromHanging проверь это утверждение.");
        assertEquals(
                2,
                FloatingBlocks.countedInGraph(List.of(world.only(2, 67, 2), world.only(5, 66, 5), world.only(6, 70, 6)))
                        .size(),
                "Список для [graph] висящее отбрасывает сундук и станции: 2 из 3 находок — висящее"
                        + " Иначе тест countedInGraph_Buildings_ExcludedFromHanging перестанет ловить эту поломку. При смене countedInGraph_Buildings_ExcludedFromHanging проверь это утверждение.");
        assertEquals(
                2,
                FloatingBlocks.hangingCount(List.of(world.only(2, 67, 2), world.only(5, 66, 5), world.only(6, 70, 6))),
                "Счётчик висящего для /v1/world берётся из того же правила: chest/furnace/crafting_table не "
                        + "висящие, иначе REST и граф считают по-разному"
                        + " При смене countedInGraph_Buildings_ExcludedFromHanging проверь это утверждение.");
    }
}
