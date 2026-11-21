package com.theminespirit.core.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.IdFilter;
import java.util.AbstractList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class TransferPlannerTests {
    private static final IdFilter ANY = IdFilter.matchAll();

    @Test
    void plan_RecipientSlotsAllFull_MovesNothing() {
        List<Stack> from = List.of(new Stack("cobblestone", 0, 10, 64));
        List<Stack> to = List.of(new Stack("dirt", 0, 64, 64));

        TransferPlan plan = TransferPlanner.plan(from, to, ANY, 64);

        assertEquals(
                List.of(),
                plan.moves(),
                "Полный приёмник не принимает ничего: ходов быть не должно, иначе перенос затирал бы "
                        + "чужие стопки. При смене plan проверь полный приёмник");
        assertEquals(
                0,
                plan.moved(),
                "В полный приёмник перенесено 0: счётчик moved сообщает скрипту, что перемещать нечего, "
                        + "иначе скрипт доложит ложный успех. При смене plan проверь moved при полном приёмнике");
        assertEquals(
                10,
                plan.leftover(),
                "Все 10 булыжника остаются у источника: leftover показывает, сколько подходящих "
                        + "предметов так и не поместилось, иначе недобор скроется. При смене plan проверь leftover");
    }

    @Test
    void plan_PartialRecipientStack_IsToppedFirst() {
        List<Stack> from = List.of(new Stack("iron_ingot", 0, 5, 64));
        List<Stack> to = slots(new Stack("iron_ingot", 0, 60, 64), null);

        TransferPlan plan = TransferPlanner.plan(from, to, ANY, 64);

        assertEquals(
                List.of(new Move(0, 0, 4), new Move(0, 1, 1)),
                plan.moves(),
                "Частичная стопка приёмника доливается первой: 4 слитка добирают её до 64, остаток 1 "
                        + "уходит в свободный слот. Иначе сундук забивался бы обрывками, которые "
                        + "придётся сливать заново. При смене plan проверь доливку частичной стопки");
        assertEquals(
                5,
                plan.moved(),
                "Все 5 слитков перенесены: доливка и свободный слот вместили весь источник, иначе часть "
                        + "груза осталась бы в инвентаре. При смене plan проверь moved при доливке");
    }

    @Test
    void plan_RequestedMoreThanAvailable_MovesEverythingAvailable() {
        List<Stack> from = List.of(new Stack("coal", 0, 3, 64));
        List<Stack> to = slots((Stack) null);

        TransferPlan plan = TransferPlanner.plan(from, to, ANY, 10);

        assertEquals(
                List.of(new Move(0, 0, 3)),
                plan.moves(),
                "Запрос 10 при наличии 3 переносит 3: часть лучше, чем отказ, иначе дух не сдаст ничего; "
                        + "нехватку называет итог FAILED по leftover. При смене plan проверь нехватку");
        assertEquals(
                3,
                plan.moved(),
                "Перенесено столько, сколько было в инвентаре: moved не может превышать наличие, иначе "
                        + "скрипт отчитается о переносе несуществующих предметов. При смене plan проверь запрос 10 при 3");
        assertEquals(
                0,
                plan.leftover(),
                "Остатка нет: всё, что подходило под фильтр, ушло в контейнер, иначе нехватку посчитают "
                        + "дважды. При смене plan проверь leftover при нехватке");
    }

    @Test
    void plan_MaxCountLowerThanAvailable_MovesOnlyMaxCount() {
        List<Stack> from = List.of(new Stack("cobblestone", 0, 64, 64));
        List<Stack> to = slots(null, null);

        TransferPlan plan = TransferPlanner.plan(from, to, ANY, 10);

        assertEquals(
                List.of(new Move(0, 0, 10)),
                plan.moves(),
                "Лимит 10 переносит ровно 10: count в плане приказ, а не пожелание, иначе дух опустошит "
                        + "слот сверх него. При смене plan проверь лимит 10");
        assertEquals(
                10,
                plan.moved(),
                "moved равен лимиту: перенос остановился ровно на нём, иначе лимит перестанет ограничивать "
                        + "скрипт. При смене plan проверь moved при лимите");
        assertEquals(
                54,
                plan.leftover(),
                "54 булыжника остались: leftover показывает, что в инвентаре ещё есть подходящее, иначе "
                        + "недобор не видно. При смене plan проверь leftover при лимите");
    }

    @Test
    void plan_ItemsOutsideFilter_StayInSource() {
        IdFilter filter = IdFilter.of(List.of("cobblestone"));
        List<Stack> from = List.of(new Stack("cobblestone", 0, 10, 64), new Stack("dirt", 0, 5, 64));
        List<Stack> to = slots((Stack) null);

        TransferPlan plan = TransferPlanner.plan(from, to, filter, 64);

        assertEquals(
                List.of(new Move(0, 0, 10)),
                plan.moves(),
                "Фильтр пропускает только подходящие предметы: deposit(item=\"cobblestone\") не должен "
                        + "отдавать в контейнер землю, иначе исчезнет смысл параметра item. "
                        + "При смене plan проверь фильтр");
        assertEquals(
                10,
                plan.moved(),
                "Перенесено 10 булыжника: земля осталась на месте, иначе фильтр затянет лишнее в контейнер. "
                        + "При смене plan проверь moved при фильтре");
        assertEquals(
                0,
                plan.leftover(),
                "Остатка подходящего нет: земля фильтру не подходит и в leftover не считается, иначе "
                        + "скрипт пойдёт искать несуществующий недобор. При смене plan проверь leftover при фильтре");
    }

    @Test
    void plan_TwoSourceStacks_FillEmptySlotsInOrder() {
        List<Stack> from = List.of(new Stack("cobblestone", 0, 64, 64), new Stack("cobblestone", 0, 36, 64));
        List<Stack> to = slots(null, null, null);

        TransferPlan plan = TransferPlanner.plan(from, to, ANY, 100);

        assertEquals(
                List.of(new Move(0, 0, 64), new Move(1, 1, 36)),
                plan.moves(),
                "Первая стопка занимает первый свободный слот, вторая — следующий: 64 в первый слот, "
                        + "остаток 36 во второй, иначе перенос затирал бы занятые слоты и предметы терялись бы. "
                        + "При смене plan проверь порядок слотов");
        assertEquals(
                100,
                plan.moved(),
                "Все 100 булыжника перенесены: перенос двух стопок не теряет предметы, иначе часть груза "
                        + "останется в инвентаре. При смене plan проверь moved двух стопок");
        assertEquals(
                0,
                plan.leftover(),
                "Остатка нет: обе стопки ушли в контейнер, иначе скрипт зря пойдёт за недобором. "
                        + "При смене plan проверь leftover двух стопок");
    }

    @Test
    void plan_DifferentVariants_AreNotMergedIntoRecipientStack() {
        List<Stack> from = List.of(new Stack("oak_log", 1, 10, 64));
        List<Stack> to = slots(new Stack("oak_log", 0, 60, 64), null);

        TransferPlan plan = TransferPlanner.plan(from, to, ANY, 64);

        assertEquals(
                List.of(new Move(0, 1, 10)),
                plan.moves(),
                "Брёвна с разными компонентами не сливаются: доливка работает только внутри одного "
                        + "item+variant, иначе зачарованное или поврежденное смешалось бы с обычным. "
                        + "При смене plan проверь вариант");
        assertEquals(
                10,
                plan.moved(),
                "Все 10 брёвен перенесены в отдельный слот: вариант сохраняется, иначе предметы с разными "
                        + "компонентами потеряют свойства. При смене plan проверь moved варианта");
    }

    @Test
    void plan_SeveralSourceSlots_FillRecipientInOrder() {
        List<Stack> from = List.of(new Stack("cobblestone", 0, 30, 64), new Stack("cobblestone", 0, 30, 64));
        List<Stack> to = slots(new Stack("cobblestone", 0, 60, 64), null);

        TransferPlan plan = TransferPlanner.plan(from, to, ANY, 64);

        assertEquals(
                List.of(new Move(0, 0, 4), new Move(0, 1, 26), new Move(1, 1, 30)),
                plan.moves(),
                "Сначала доливается частичная стопка (4 до 64), затем остаток уходит в свободный слот, "
                        + "и когда он тоже частично занят — следующий слот источника добивает его, "
                        + "а не открывает новый, иначе сундук забивается обрывками. "
                        + "При смене plan проверь доливку несколькими слотами");
        assertEquals(
                60,
                plan.moved(),
                "Перенесено 60 предметов: оба слота источника опустошены, сундук принял всё, иначе часть "
                        + "булыжника останется у духа. При смене plan проверь moved нескольких слотов");
    }

    @Test
    void plan_MaxCountZero_MovesNothing() {
        List<Stack> from = List.of(new Stack("cobblestone", 0, 64, 64));
        List<Stack> to = slots((Stack) null);

        TransferPlan plan = TransferPlanner.plan(from, to, ANY, 0);

        assertEquals(
                List.of(),
                plan.moves(),
                "Лимит 0 не делает ни одного хода: вызов с нулевым лимитом не должен трогать сундук, "
                        + "иначе план опустошит его вопреки приказу. При смене plan проверь лимит 0");
        assertEquals(
                64,
                plan.leftover(),
                "Все 64 булыжника остались: leftover показывает полное наличие подходящего, иначе скрипт "
                        + "посчитает перенос выполненным. При смене plan проверь leftover при лимите 0");
    }

    @Test
    void plan_SlotAccesses_StayLinear() {
        CountingSlots from = counting(List.of(
                new Stack("cobblestone", 0, 30, 64), new Stack("iron_ingot", 0, 5, 64),
                new Stack("cobblestone", 0, 30, 64), new Stack("dirt", 0, 3, 64)));
        CountingSlots to = counting(
                slots(new Stack("cobblestone", 0, 10, 64), null, new Stack("iron_ingot", 0, 60, 64), null, null));

        TransferPlanner.plan(from, to, IdFilter.of(List.of("cobblestone", "iron_ingot")), 1000);

        int slots = from.size() + to.size();
        assertTrue(
                from.reads() + to.reads() <= 3 * slots,
                "План читает слоты источника и приёмника не больше трёх раз каждый (" + slots
                        + " слотов всего): перенос идёт на серверном тике, и квадратичный обход сундука "
                        + "подвешивал бы его, иначе серверный тик пропускает переносы. Чтений источника " + from.reads()
                        + ", приёмника "
                        + to.reads() + ". При смене plan проверь счётчик слотов");
    }

    @Test
    void plan_Moves_NeverExceedSlotsOfBothSides() {
        List<Stack> from = List.of(
                new Stack("cobblestone", 0, 64, 64),
                new Stack("cobblestone", 0, 64, 64),
                new Stack("cobblestone", 0, 64, 64));
        List<Stack> to = slots(null, null, null, null);

        TransferPlan plan = TransferPlanner.plan(from, to, ANY, 1000);

        assertTrue(
                plan.moves().size() <= from.size() + to.size(),
                "Ходов не больше слотов источника и приёмника (" + from.size() + " + " + to.size()
                        + " = " + (from.size() + to.size()) + ", а ходов "
                        + plan.moves().size() + "): "
                        + "за тик скрипт успевает ограниченное число перемещений, и план длиннее "
                        + "этого предела не выполнит, иначе перенос зависает. При смене plan проверь число ходов");
    }

    private static CountingSlots counting(List<Stack> slots) {
        return new CountingSlots(slots);
    }

    private static List<Stack> slots(Stack... stacks) {
        return Arrays.asList(stacks);
    }

    private static final class CountingSlots extends AbstractList<Stack> {
        private final List<Stack> delegate;
        private int reads;

        private CountingSlots(List<Stack> delegate) {
            this.delegate = delegate;
        }

        private int reads() {
            return reads;
        }

        @Override
        public Stack get(int index) {
            reads++;
            return delegate.get(index);
        }

        @Override
        public int size() {
            reads++;
            return delegate.size();
        }
    }
}
