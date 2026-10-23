package com.theminespirit.core.intellect.activity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.coordinator.SpiritRole;
import com.theminespirit.core.coordinator.WorkKind;
import com.theminespirit.core.intellect.FarmWork;
import com.theminespirit.core.intellect.Forage;
import com.theminespirit.core.intellect.SettlementView;
import com.theminespirit.core.intellect.SettlementViews;
import com.theminespirit.core.inventory.InventoryFill;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UnloadActivityTests {
    private static final UUID SPIRIT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final InventoryFill LOADED = new InventoryFill(30, 36, 6, 36);

    private static SettlementView viewWith(UUID id, InventoryFill fill) {
        return SettlementViews.withFills(List.of(SettlementViews.spirit(id)), Map.of(id, fill));
    }

    @Test
    void demand_EmptyView_IsZero() {
        assertEquals(
                0.0,
                new UnloadActivity().demand(SettlementViews.empty()),
                1e-9,
                "Пустое поселение не создаёт спроса на разгрузку: нечего освобождать");
    }

    @Test
    void demand_LoadedInventory_IsPositive() {
        assertTrue(
                new UnloadActivity().demand(viewWith(SPIRIT, LOADED)) > 0,
                "Загруженный инвентарь рождает спрос освободить его: иначе дух копит и перестаёт работать");
    }

    @Test
    void demand_TwoSpirits_TakesHighestPressure() {
        SettlementView view = SettlementViews.withFills(
                List.of(SettlementViews.spirit(SPIRIT), SettlementViews.spirit(OTHER)),
                Map.of(SPIRIT, LOADED, OTHER, InventoryFill.EMPTY));

        assertEquals(
                new UnloadActivity().demand(viewWith(SPIRIT, LOADED)),
                new UnloadActivity().demand(view),
                1e-9,
                "Спрос разгрузки берёт самое забитое хранилище: иначе полный дух ждёт, пока разгрузится пустой");
    }

    @Test
    void eligible_WorkerWithPressure_IsTrue() {
        assertTrue(
                new UnloadActivity().eligible(SettlementViews.spirit(SPIRIT), viewWith(SPIRIT, LOADED)),
                "Работник с забитым инвентарём берёт разгрузку: иначе ему некуда складывать добычу");
    }

    @Test
    void eligible_DefenderWhileThreats_IsFalse() {
        SettlementView view = new SettlementView(
                1,
                0,
                2,
                2,
                64,
                32,
                2,
                1,
                0.5,
                FarmWork.NONE,
                Forage.NONE,
                List.of(SettlementViews.spiritWithRole(SPIRIT, SpiritRole.DEFENDER)),
                100,
                Map.of(SPIRIT, LOADED));

        assertFalse(
                new UnloadActivity().eligible(SettlementViews.spiritWithRole(SPIRIT, SpiritRole.DEFENDER), view),
                "Защитник при угрозе в поселении пост не бросает ради разгрузки: на посту он нужнее");
    }

    @Test
    void eligible_NoFill_IsFalse() {
        assertFalse(
                new UnloadActivity().eligible(SettlementViews.spirit(SPIRIT), SettlementViews.empty()),
                "Без данных о заполнении дух разгрузку не берёт: неизвестное давление не гоняет его на базу");
    }

    @Test
    void eligible_BusyMinerWithFreeSlots_KeepsTask() {
        InventoryFill pressured = new InventoryFill(26, 36, 0, 26);
        SettlementView view = SettlementViews.withFills(
                List.of(SettlementViews.busySpirit(SPIRIT, WorkKind.VEIN)), Map.of(SPIRIT, pressured));

        assertFalse(
                new UnloadActivity().eligible(SettlementViews.busySpirit(SPIRIT, WorkKind.VEIN), view),
                "Шахтёр держит задачу до последней свободной ячейки: разгрузку он не берёт, пока есть куда складывать");
    }

    @Test
    void eligible_BusyMinerAtLastSlot_TakesUnload() {
        InventoryFill full = new InventoryFill(36, 36, 0, 36);
        SettlementView view = SettlementViews.withFills(
                List.of(SettlementViews.busySpirit(SPIRIT, WorkKind.VEIN)), Map.of(SPIRIT, full));

        assertTrue(
                new UnloadActivity().eligible(SettlementViews.busySpirit(SPIRIT, WorkKind.VEIN), view),
                "На последней ячейке шахтёр бросает задачу ради разгрузки: дальше добыча некуда ляжет");
    }

    @Test
    void eligible_BusyFarmerFewFreeSlots_TakesUnload() {
        InventoryFill tight = new InventoryFill(34, 36, 0, 34);
        SettlementView view = SettlementViews.withFills(
                List.of(SettlementViews.busySpirit(SPIRIT, WorkKind.CELL)), Map.of(SPIRIT, tight));

        assertTrue(
                new UnloadActivity().eligible(SettlementViews.busySpirit(SPIRIT, WorkKind.CELL), view),
                "Ферма бросает задачу раньше шахтёра (порог 2): при двух свободных ячейках она идёт разгружаться");
    }

    @Test
    void order_AnySpirit_UsesUnloadScript() {
        assertEquals(
                Optional.of(UnloadActivity.SCRIPT),
                new UnloadActivity()
                        .order(SettlementViews.spirit(SPIRIT), SettlementViews.empty())
                        .script(),
                "Разгрузка зовёт скрипт unload: без него задача не исполнится");
    }
}
