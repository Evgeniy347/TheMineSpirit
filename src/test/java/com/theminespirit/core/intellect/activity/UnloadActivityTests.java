package com.theminespirit.core.intellect.activity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.coordinator.SpiritRole;
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
        assertEquals(0.0, new UnloadActivity().demand(SettlementViews.empty()), 1e-9,
                "Пустое поселение не создаёт спроса на разгрузку: нечего освобождать");
    }

    @Test
    void demand_LoadedInventory_IsPositive() {
        assertTrue(new UnloadActivity().demand(viewWith(SPIRIT, LOADED)) > 0,
                "Загруженный инвентарь рождает спрос освободить его: иначе дух копит и перестаёт работать");
    }

    @Test
    void demand_TwoSpirits_TakesHighestPressure() {
        SettlementView view = SettlementViews.withFills(
                List.of(SettlementViews.spirit(SPIRIT), SettlementViews.spirit(OTHER)),
                Map.of(SPIRIT, LOADED, OTHER, InventoryFill.EMPTY));

        assertEquals(new UnloadActivity().demand(viewWith(SPIRIT, LOADED)), new UnloadActivity().demand(view), 1e-9,
                "Спрос разгрузки берёт самое забитое хранилище: иначе полный дух ждёт, пока разгрузится пустой");
    }

    @Test
    void eligible_WorkerWithPressure_IsTrue() {
        assertTrue(new UnloadActivity().eligible(SettlementViews.spirit(SPIRIT), viewWith(SPIRIT, LOADED)),
                "Работник с забитым инвентарём берёт разгрузку: иначе ему некуда складывать добычу");
    }

    @Test
    void eligible_Defender_IsFalse() {
        SettlementView view = SettlementViews.withFills(
                List.of(SettlementViews.spiritWithRole(SPIRIT, SpiritRole.DEFENDER)), Map.of(SPIRIT, LOADED));

        assertFalse(new UnloadActivity().eligible(SettlementViews.spiritWithRole(SPIRIT, SpiritRole.DEFENDER), view),
                "Защитник пост не бросает ради разгрузки: на посту он нужнее");
    }

    @Test
    void eligible_NoFill_IsFalse() {
        assertFalse(new UnloadActivity().eligible(SettlementViews.spirit(SPIRIT), SettlementViews.empty()),
                "Без данных о заполнении дух разгрузку не берёт: неизвестное давление не гоняет его на базу");
    }

    @Test
    void order_AnySpirit_UsesUnloadScript() {
        assertEquals(Optional.of(UnloadActivity.SCRIPT),
                new UnloadActivity().order(SettlementViews.spirit(SPIRIT), SettlementViews.empty()).script(),
                "Разгрузка зовёт скрипт unload: без него задача не исполнится");
    }
}
