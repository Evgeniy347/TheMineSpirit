package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.BoardLedger;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.ColumnMask;
import com.theminespirit.core.town.FenceLine;
import com.theminespirit.core.town.Placement;
import com.theminespirit.core.town.TownPlan;
import com.theminespirit.core.town.TownStreets;
import java.util.List;
import org.junit.jupiter.api.Test;

class StoragePlanWatchTests {
    private static final BlockCoord ANCHOR = new BlockCoord(0, 64, 0);

    @Test
    void lots_ThreeStorages_AreListed() {
        assertEquals(
                List.of("storage", "storage_2", "storage_3"),
                StoragePlanWatch.LOTS,
                "План этапа 1 держит ровно три склада: существующий и два компактных участка, иначе духи "
                        + "строят меньше складов, чем ожидает этап. "
                        + "Иначе тест lots_ThreeStorages_AreListed перестанет ловить эту поломку. При смене lots_ThreeStorages_AreListed проверь это утверждение.");
    }

    @Test
    void handles_WarehouseNode_IsHandled() {
        assertTrue(
                StoragePlanWatch.handles("warehouse"),
                "Узел warehouse обслуживает сторож складов: без него задания склада не выдаются. "
                        + "Иначе тест handles_WarehouseNode_IsHandled перестанет ловить эту поломку. При смене handles_WarehouseNode_IsHandled проверь это утверждение.");
        assertFalse(
                StoragePlanWatch.handles("chests"),
                "Старый узел пары сундуков больше не принадлежит сторожу: временных сундуков нет. "
                        + "Иначе тест handles_WarehouseNode_IsHandled перестанет ловить эту поломку. При смене handles_WarehouseNode_IsHandled проверь это утверждение.");
    }

    @Test
    void pending_ThreeLotsWithoutBuild_AreAllWanted() {
        BoardLedger ledger = new BoardLedger();

        assertEquals(
                List.of("storage", "storage_2", "storage_3"),
                StoragePlanWatch.pending(ledger, town(), 64),
                "Пока ни один склад не построен, сторож выдаёт все три задания BUILD_PLACED по участкам "
                        + "плана, иначе стройка складов не начнётся. "
                        + "Иначе тест pending_ThreeLotsWithoutBuild_AreAllWanted перестанет ловить эту поломку. При смене pending_ThreeLotsWithoutBuild_AreAllWanted проверь это утверждение.");
    }

    @Test
    void pending_BuiltLot_IsSkipped() {
        BoardLedger ledger = new BoardLedger();
        ledger.notePlan("storage", 1);

        assertEquals(
                List.of("storage_2", "storage_3"),
                StoragePlanWatch.pending(ledger, town(), 64),
                "Построенный склад не получает повторного задания: сторож молчит по нему и выдаёт только "
                        + "оставшиеся участки. "
                        + "Иначе тест pending_BuiltLot_IsSkipped перестанет ловить эту поломку. При смене pending_BuiltLot_IsSkipped проверь это утверждение.");
    }

    private static TownPlan town() {
        ColumnMask area = ColumnMask.rect(-10, -10, 40, 40);
        return new TownPlan(
                ANCHOR,
                area,
                new TownStreets(area, area),
                List.of(
                        new Placement("storage", "склад", 1, 0, 0, 18, 19, Placement.Facing.NORTH),
                        new Placement("storage_2", "склад 2", 1, 20, 0, 8, 9, Placement.Facing.NORTH),
                        new Placement("storage_3", "склад 3", 1, 30, 0, 8, 9, Placement.Facing.NORTH)),
                new FenceLine(List.of(), List.of()),
                List.of());
    }
}
