package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ToolBreakCostTests {
    record BreakRow(double hardness, double toolSpeed, boolean dropsAllowed, int ticks) {
        @Override
        public String toString() {
            return "твёрдость " + hardness + " скорость " + toolSpeed;
        }
    }

    record PlaceRow(String blockId, int ticks) {
        @Override
        public String toString() {
            return blockId;
        }
    }

    static Stream<Arguments> breakTable() {
        return Stream.of(
                Arguments.of(new BreakRow(1.5, 1.0, false, 160)),
                Arguments.of(new BreakRow(1.5, 4.0, true, 22)),
                Arguments.of(new BreakRow(0.5, 1.0, true, 25)),
                Arguments.of(new BreakRow(0.0, 1.0, false, 11)));
    }

    static Stream<Arguments> placeTable() {
        return Stream.of(
                Arguments.of(new PlaceRow("dirt", 5)),
                Arguments.of(new PlaceRow("cobblestone", 5)),
                Arguments.of(new PlaceRow("gravel", 5)),
                Arguments.of(new PlaceRow("sand", 5)),
                Arguments.of(new PlaceRow("oak_planks", 5)),
                Arguments.of(new PlaceRow("minecraft:dirt", 5)),
                Arguments.of(new PlaceRow("diamond_block", 20)),
                Arguments.of(new PlaceRow("oak_log", 20)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("breakTable")
    void breakTicks_TableRow_TicksMatch(BreakRow row) {
        assertEquals(row.ticks(), ToolBreakCost.breakTicks(row.hardness(), row.toolSpeed(), row.dropsAllowed()),
                "Ломка твёрдости " + row.hardness() + " обязана занимать " + row.ticks() + " тиков: по сроку "
                        + "решается «ломать или обходить», заниженный срок ведёт духа в стену вместо обхода");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("placeTable")
    void placePenalty_TableRow_PenaltyMatches(PlaceRow row) {
        assertEquals(row.ticks(), ToolBreakCost.placePenalty(row.blockId()),
                "Установка " + row.blockId() + " обязана стоить " + row.ticks() + " тиков: расходный блок дёшево, "
                        + "ценный дорого, иначе дух засыплет туннель алмазными блоками");
    }

    @Test
    void breakTicks_HandVsPickaxeOnStone_HandTakesLonger() {
        assertEquals(true,
                ToolBreakCost.breakTicks(1.5, 1.0, false) > ToolBreakCost.breakTicks(1.5, 4.0, true),
                "Камень рукой обязан ломаться дольше, чем киркой: иначе выбор инструмента не влияет на порядок целей");
    }

    @Test
    void breakTicks_HugeHardness_SaturatesWithoutOverflow() {
        assertEquals(Integer.MAX_VALUE, ToolBreakCost.breakTicks(1e18, 1.0, false),
                "Неразрушимый блок обязан давать насыщенный срок, а не отрицательный: переполнение int "
                        + "превратило бы вечность в мгновенную ломку и дух бил бы коренную породу");
    }

    @Test
    void throwaway_KnownIds_AreListed() {
        for (String id : List.of("dirt", "cobblestone", "gravel", "sand", "oak_planks")) {
            assertTrue(ToolBreakCost.THROWAWAY.contains(id),
                    "Расходный блок '" + id + "' обязан быть в списке THROWAWAY: без него засыпка и мосты "
                            + "пойдут ценными блоками");
        }
    }

    @Test
    void orderByCost_CostsDiffer_CheapFirst() {
        assertEquals(List.of("dirt", "stone", "diamond_block"),
                ToolBreakCost.orderByCost(Map.of("stone", 20, "diamond_block", 100, "dirt", 5)),
                "Цели обязаны упорядочиваться от дешёвой к дорогой: иначе дух сначала потащится к дальней "
                        + "дорогой цели мимо ближней");
    }

    @Test
    void isThrowaway_BlankId_Throws() {
        assertThrows(IllegalArgumentException.class, () -> ToolBreakCost.isThrowaway("   "),
                "Пустой id блока обязан отвергаться: пробельный id не отличит расходный блок от ценного");
    }
}
