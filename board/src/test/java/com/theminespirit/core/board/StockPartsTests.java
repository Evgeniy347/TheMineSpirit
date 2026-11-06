package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;

class StockPartsTests {
    @Test
    void total_ChestsAndCarried_SumsBoth() {
        StockParts parts = new StockParts(
                new StockView(Map.of("oak_log", 5)), new StockView(Map.of("oak_log", 25, "oak_planks", 86)));

        assertEquals(
                30,
                parts.total().available().getOrDefault("oak_log", 0),
                "нехватка материалов считается по сундукам и инвентарям духов сразу (аномалия 7: warehouse_1 "
                        + "ждал oak_log×8, а у spirit-1 лежало 25): иначе доска просит то, что духи уже несут");
        assertEquals(
                86,
                parts.total().available().getOrDefault("oak_planks", 0),
                "инвентарь духа входит в общий запас по каждому предмету, а не только по брёвнам");
        assertEquals(5, parts.chests().available().get("oak_log"), "часть снимка «в сундуках» остаётся отдельной");
    }

    @Test
    void free_ReserveEatsChestsOnly_CarriedStays() {
        StockParts parts = new StockParts(new StockView(Map.of("oak_log", 2)), new StockView(Map.of("oak_log", 25)));

        StockView free = parts.free(Map.of("oak_log", 8));

        assertEquals(
                25,
                free.available().getOrDefault("oak_log", 0),
                "бронь списывается только с сундуков: предметы в инвентаре духа другой дух из сундука не возьмёт, "
                        + "и они не должны пропадать из свободного запаса");
    }

    @Test
    void free_ChestReserveLargerThanStock_NotNegative() {
        StockParts parts = new StockParts(new StockView(Map.of("oak_planks", 3)), new StockView(Map.of()));

        StockView free = parts.free(Map.of("oak_planks", 40));

        assertEquals(
                0,
                free.available().getOrDefault("oak_planks", 0),
                "бронь больше сундуков не делает отрицательный запас: свободного просто нет");
    }
}
