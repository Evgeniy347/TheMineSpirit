package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Optional;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;

class FirstStepsPlanTests {

    private static final BoardCase[] CHAIN = {
            BoardCase.CLEAR_GROUNDS,
            BoardCase.STOCK_CHESTS,
            BoardCase.CRAFTING_STATION,
            BoardCase.CRAFT_TOOLS,
            BoardCase.BUILD_MINE,
            BoardCase.BUILD_FENCE,
            BoardCase.LIGHTING,
            BoardCase.BUILD_HOUSE
    };

    private static SettlementFacts factsAt(int step) {
        return new SettlementFacts(true, step > 0, step > 1 ? 2 : 0, step > 2, step > 3, step > 4,
                step > 5, step > 6, step > 7);
    }

    @Test
    void next_EmptySettlement_ClearGrounds() {
        SettlementFacts fresh = new SettlementFacts(true, false, 0, false, false, false, false, false,
                false);

        assertEquals(Optional.of(BoardCase.CLEAR_GROUNDS), FirstStepsPlan.next(fresh),
                "Пустое поселение с домом начинается с зачистки территории: первый шаг "
                        + "цепочки из источника, без него сундуки ставятся в лесу");
    }

    @Test
    void next_Chain_StrictOrder() {
        for (int step = 0; step < CHAIN.length; step++) {
            assertEquals(Optional.of(CHAIN[step]), FirstStepsPlan.next(factsAt(step)),
                    "Шаг " + step + " цепочки обязан давать " + CHAIN[step]
                            + ": пропуск шага невозможен, иначе забор встанет до входа в шахту");
        }
    }

    @Test
    void next_AllDone_Empty() {
        SettlementFacts complete = new SettlementFacts(true, true, 2, true, true, true, true, true, true);

        assertEquals(Optional.empty(), FirstStepsPlan.next(complete),
                "Всё сделано — генератор молчит: новые задания не плодятся, иначе доска "
                        + "бесконечно ставила бы уже выполненные шаги");
    }

    @Test
    void next_NoHome_Empty() {
        SettlementFacts noHome = new SettlementFacts(false, false, 0, false, false, false, false,
                false, false);

        assertEquals(Optional.empty(), FirstStepsPlan.next(noHome),
                "Без дома генератор не начинает цепочку: дом — точка поселения, её ставит "
                        + "стратег или игрок, а не генератор первых шагов");
    }

    @Test
    void next_Is_Deterministic() {
        SettlementFacts facts = factsAt(3);

        assertEquals(FirstStepsPlan.next(facts), FirstStepsPlan.next(facts),
                "Генератор обязан быть детерминирован: по нему доска ставит задание, "
                        + "нестабильный выбор перетасует первые шаги между пересборками");
    }

    @Test
    void next_NullFacts_Rejected() {
        assertThrows(NullPointerException.class, () -> FirstStepsPlan.next(null),
                "Генератор обязан решать по фактам: null означает, что снимка поселения нет, "
                        + "а не «пустое поселение»");
    }

    @Test
    void next_NegativeChests_Rejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new SettlementFacts(true, true, -1, true, true, true, true, true, true),
                "Отрицательное число сундуков обязано отвергаться: оно прошло бы как "
                        + "«сундуков нет» и сломало бы шаг цепочки");
    }

    @Property
    void next_OnlyChainSteps_Returned(@ForAll("settlementFacts") SettlementFacts facts) {
        Optional<BoardCase> next = FirstStepsPlan.next(facts);

        assertTrue(next.isEmpty() || Arrays.stream(CHAIN).anyMatch(step -> step == next.get()),
                "Результат генератора обязан быть шагом цепочки или пустым: иной кейс "
                        + "означал бы задание вне порядка первых шагов");
    }

    @Property
    void next_Monotone_ByFacts(@ForAll("settlementFacts") SettlementFacts facts,
            @ForAll("settlementFacts") SettlementFacts grown) {
        if (!facts.hasHome() || !growsFrom(facts, grown)) {
            return;
        }
        Optional<BoardCase> before = FirstStepsPlan.next(facts);
        Optional<BoardCase> after = FirstStepsPlan.next(grown);

        int beforeStep = before.map(Arrays.asList(CHAIN)::indexOf).orElse(CHAIN.length);
        int afterStep = after.map(Arrays.asList(CHAIN)::indexOf).orElse(CHAIN.length);

        assertTrue(afterStep >= beforeStep,
                "Рост фактов не обязан откатывать генератор назад: факты монотонно растут, "
                        + "и следующий шаг не может вернуться к пройденному");
    }

    private static boolean growsFrom(SettlementFacts from, SettlementFacts to) {
        return to.hasHome() && (!from.groundsCleared() || to.groundsCleared())
                && to.chests() >= from.chests() && (!from.craftingStation() || to.craftingStation())
                && (!from.tools() || to.tools()) && (!from.mineEntrance() || to.mineEntrance())
                && (!from.fence() || to.fence()) && (!from.lighting() || to.lighting())
                && (!from.houses() || to.houses());
    }

    @Provide
    Arbitrary<SettlementFacts> settlementFacts() {
        return Arbitraries.of(
                factsAt(0),
                factsAt(1),
                factsAt(2),
                factsAt(3),
                factsAt(4),
                factsAt(5),
                factsAt(6),
                factsAt(7),
                factsAt(8),
                new SettlementFacts(false, false, 0, false, false, false, false, false, false),
                new SettlementFacts(true, true, 1, true, false, true, false, true, false));
    }
}
