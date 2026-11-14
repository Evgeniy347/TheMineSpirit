package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StorageCellsTests {
    private static final Set<BlockCoord> YARD = Set.of(new BlockCoord(-181, 68, -517), new BlockCoord(-176, 68, -520));
    private static final Set<BlockCoord> WAREHOUSE = Set.of(new BlockCoord(-150, 68, -500));

    @Test
    void storage_WarehouseMissingOnStage1_ReturnsYardRow() {
        assertSame(
                YARD,
                StorageCells.storage(Set.of(), YARD),
                "Склад не построен — все сундуки поселения обязаны стоять в одном ряду двора у верстака "
                        + "(правило владельца 14.11). Если здесь вернуть что-то ещё, обменный сундук и сундуки "
                        + "при полном складе начнут ставиться рядом с первым попавшимся сундуком: кучка снова "
                        + "растёт вширь и вверх, духи спотыкаются об опору (живой сервер 14.11: 25 сундуков "
                        + "на бугре y71-74 у сундука-станции вместо отсутствующего верстака)");
    }

    @Test
    void storage_WarehouseBuilt_ReturnsWarehouseOnly() {
        assertEquals(
                WAREHOUSE,
                StorageCells.storage(WAREHOUSE, YARD),
                "Склад построен — место хранения только склад: сундуки двора уже разобраны или разбираются, "
                        + "новые сундуки вне склада не появляются. Смешанный набор клеток позволил бы духам "
                        + "снова пополнять ряд двора и чинить кучку бесконечно");
    }
}
