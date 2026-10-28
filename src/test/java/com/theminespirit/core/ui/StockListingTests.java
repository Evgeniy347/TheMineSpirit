package com.theminespirit.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class StockListingTests {
    private static final List<StockListing.Entry> ALL = List.of(
            new StockListing.Entry(0, "oak_log", "Дубовое бревно", 12),
            new StockListing.Entry(1, "coal", "Уголь", 40),
            new StockListing.Entry(2, "create:andesite_alloy", "Андезитовый сплав", 3),
            new StockListing.Entry(3, "birch_log", "Берёзовое бревно", 12));

    @Test
    void view_CountSort_MostFirstThenName() {
        assertEquals(
                List.of(1, 3, 0, 2),
                keys(StockListing.view(ALL, "", StockListing.Sort.COUNT, false)),
                "По количеству — больше сверху, при равенстве по имени: порядок списка не прыгает между запросами");
    }

    @Test
    void view_NameQuery_FiltersCaseInsensitive() {
        assertEquals(
                List.of(3, 0),
                keys(StockListing.view(ALL, "БРЕВНО", StockListing.Sort.NAME, false)),
                "Поиск по имени без учёта регистра, как в Storage Network и JEI");
    }

    @Test
    void view_ModPrefix_FiltersByNamespace() {
        assertEquals(
                List.of(2),
                keys(StockListing.view(ALL, "@create", StockListing.Sort.NAME, false)),
                "«@мод» отбирает предметы мода, как в JEI: иначе синхронизация поиска с JEI даёт разные списки");
    }

    @Test
    void view_Reversed_InvertsOrder() {
        assertEquals(
                List.of(2, 0, 3, 1),
                keys(StockListing.view(ALL, "", StockListing.Sort.COUNT, true)),
                "Обратный порядок — точное зеркало прямого");
    }

    @Test
    void next_LastSort_WrapsToFirst() {
        assertEquals(StockListing.Sort.COUNT, StockListing.Sort.MOD.next(), "Кнопка сортировки ходит по кругу");
    }

    private static List<Integer> keys(List<StockListing.Entry> entries) {
        return entries.stream().map(StockListing.Entry::key).toList();
    }
}
