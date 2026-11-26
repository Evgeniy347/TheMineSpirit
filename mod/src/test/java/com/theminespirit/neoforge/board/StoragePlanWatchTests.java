package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.BoardLedger;
import com.theminespirit.core.inventory.ContainerSnapshot;
import com.theminespirit.core.inventory.Stack;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.ColumnMask;
import com.theminespirit.core.town.FenceLine;
import com.theminespirit.core.town.Placement;
import com.theminespirit.core.town.TownPlan;
import com.theminespirit.core.town.TownStreets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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

    @Test
    void space_TemporaryChests_DoNotCountAsWarehouse() {
        BlockCoord warehouse = new BlockCoord(1, 64, 1);
        BlockCoord temporary = new BlockCoord(3, 64, 1);

        StoragePlanWatch.Space space =
                StoragePlanWatch.space(List.of(snapshot(temporary, 27), snapshot(warehouse, 10)), Set.of(warehouse));

        assertEquals(
                27,
                space.slots(),
                "В складе только его сундук: временные сундуки не создают ложного места, иначе график заполнения "
                        + "склада показывает запас там, где его нет, и расширение не строится. При смене "
                        + "StoragePlanWatch.space проверь live storage_3 с временными сундуками и DemandWatch.");
        assertEquals(
                10,
                space.free(),
                "Свободные слоты считает только складский сундук: иначе перенос и решение о расширении склада "
                        + "опираются на чужую вместимость. При смене StoragePlanWatch.space проверь "
                        + "StorePost.transfer и ChestSpace.of.");
    }

    @Test
    void space_NoWarehouseCells_IsHundredPercentFree() {
        StoragePlanWatch.Space space = StoragePlanWatch.space(List.of(snapshot(new BlockCoord(3, 64, 1), 0)), Set.of());

        assertEquals(
                0,
                space.slots(),
                "Без построенного склада его вместимость равна нулю: иначе временные сундуки подменяют склад "
                        + "и граф не строит storage. При смене StoragePlanWatch.space проверь WarehouseLocations.cells "
                        + "и DemandWatch до первого ledger.hasPlan.");
        assertEquals(
                100,
                space.freePercent(),
                "Пустой склад не считается заполненным временными сундуками: иначе включается ложная нужда "
                        + "в расширении и обычная стройка откладывается. При смене StoragePlanWatch.space проверь "
                        + "ChestSpace.freePercent и live storage_3.");
    }

    private static ContainerSnapshot snapshot(BlockCoord at, int free) {
        List<Stack> slots = new ArrayList<>(27);
        for (int slot = 0; slot < 27; slot++) {
            slots.add(slot < 27 - free ? new Stack("cobblestone", 0, 64, 64) : null);
        }
        return new ContainerSnapshot(at, slots);
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
