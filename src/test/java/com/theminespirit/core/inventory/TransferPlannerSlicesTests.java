package com.theminespirit.core.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class TransferPlannerSlicesTests {
    @Test
    void slices_PartialRecipientStack_IsToppedUpFirst() {
        List<Stack> from = List.of(new Stack("iron_ingot", 0, 10, 64));
        List<Stack> to = slots(new Stack("iron_ingot", 0, 60, 64), null);

        TransferPlan plan = TransferPlanner.planSlices(from, to, List.of(new Slice(0, 10)));

        assertEquals(
                List.of(new Move(0, 0, 4), new Move(0, 1, 6)),
                plan.moves(),
                "Излишек доливает частичную стопку приёмника первой: 4 слитка добирают её до 64, остаток 6 "
                        + "уходит в свободный слот. Иначе разгрузка плодила бы обрывки стопок в сундуке");
        assertEquals(10, plan.moved(), "Весь излишек в 10 слитков перенесён: приёмник вместил всё");
        assertEquals(0, plan.leftover(), "Остатка нет: весь излишек поместился");
    }

    @Test
    void slices_RecipientFull_StopsWithLeftover() {
        List<Stack> from = List.of(new Stack("cobblestone", 0, 64, 64), new Stack("dirt", 0, 64, 64));
        List<Stack> to = List.of(new Stack("stone", 0, 64, 64));

        TransferPlan plan = TransferPlanner.planSlices(from, to, List.of(new Slice(0, 64), new Slice(1, 64)));

        assertEquals(List.of(), plan.moves(), "В полный приёмник ходов нет: перенос затирал бы чужие стопки");
        assertEquals(0, plan.moved(), "Ничего не перенесено: приёмник полон");
        assertEquals(128, plan.leftover(), "Остаток 128 показывает скрипту, что контейнер полон: перенесено 0 из 128");
    }

    @Test
    void slices_MissingSourceSlot_CountsAsLeftover() {
        List<Stack> from = slots(null, new Stack("dirt", 0, 5, 64));
        List<Stack> to = slots((Stack) null);

        TransferPlan plan = TransferPlanner.planSlices(from, to, List.of(new Slice(0, 3), new Slice(1, 5)));

        assertEquals(
                List.of(new Move(1, 0, 5)),
                plan.moves(),
                "Пустой слот источника пропускается: везти из него нечего, а второй излишек едет как обычно");
        assertEquals(5, plan.moved(), "Перенесены только 5 земли из существующего слота");
        assertEquals(
                3,
                plan.leftover(),
                "Несуществующие 3 предмета остаются остатком, а не падают в ошибку: источник мог измениться "
                        + "между расчётом излишка и планом");
    }

    @Test
    void slices_ExactSlotKept_OtherSlotsUntouched() {
        List<Stack> from = List.of(new Stack("bread", 0, 16, 64), new Stack("bread", 0, 4, 64));
        List<Stack> to = slots(null, null);

        TransferPlan plan = TransferPlanner.planSlices(from, to, List.of(new Slice(1, 4)));

        assertEquals(4, plan.moved(), "Перенесены ровно 4 хлеба излишка из слота 1");
        for (Move move : plan.moves()) {
            assertEquals(
                    1,
                    move.fromSlot(),
                    "Ходы берут только из слота излишка 1: квота базового набора в слоте 0 неприкосновенна, "
                            + "иначе разгрузка съедала бы оставленное духу. Ход: " + move);
        }
        assertEquals(0, plan.leftover(), "Остатка нет: излишек поместился");
    }

    @Test
    void slices_TotalMovedPlusLeftover_EqualsSlices() {
        List<Stack> from = new ArrayList<>();
        List<Slice> slices = new ArrayList<>();
        for (int slot = 0; slot < 12; slot++) {
            from.add(new Stack("cobblestone", 0, 64, 64));
            if (slot % 3 == 0) {
                slices.add(new Slice(slot, 64));
            }
        }
        List<Stack> to = slots(new Stack("cobblestone", 0, 60, 64), null, null);

        TransferPlan plan = TransferPlanner.planSlices(from, to, slices);

        assertEquals(
                256,
                plan.moved() + plan.leftover(),
                "Перенесённое плюс остаток обязаны равняться заявленному излишку 256: иначе предметы "
                        + "терялись бы или дублировались при разгрузке. Перенесено " + plan.moved()
                        + ", остаток " + plan.leftover());
        assertTrue(
                plan.moved() <= 4 + 64 + 64,
                "Перенесено не больше вместимости приёмника (4 доливка + два пустых слота): фактически "
                        + plan.moved());
    }

    private static List<Stack> slots(Stack... stacks) {
        return Arrays.asList(stacks);
    }
}
