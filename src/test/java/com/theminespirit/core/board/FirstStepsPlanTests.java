package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;

class FirstStepsPlanTests {

    private static final int FIRST_TOOL_STEP = 3;
    private static final int MINE_STEP = 6;

    private static List<BoardCase> chain() {
        List<BoardCase> chain = new ArrayList<>();
        chain.add(BoardCase.CLEAR_GROUNDS);
        chain.add(BoardCase.STOCK_CHESTS);
        chain.add(BoardCase.CRAFTING_STATION);
        chain.addAll(ToolSet.wooden());
        chain.add(BoardCase.BUILD_MINE);
        chain.addAll(ToolSet.stone());
        chain.add(BoardCase.BUILD_FENCE);
        chain.add(BoardCase.LIGHTING);
        chain.add(BoardCase.BUILD_HOUSE);
        chain.add(BoardCase.CRAFT_SMITHING_TABLE);
        chain.add(BoardCase.CRAFT_ANVIL);
        return chain;
    }

    private static Set<String> toolsDone(int step) {
        Set<String> tools = new HashSet<>();
        List<BoardCase> toolsChain = ToolSet.chain();
        for (int i = 0; i < toolsChain.size(); i++) {
            int mineStep = i < ToolSet.wooden().size() ? 0 : 1;
            if (step > FIRST_TOOL_STEP + i + mineStep) {
                tools.add(ToolSet.stockKey(toolsChain.get(i)));
            }
        }
        return tools;
    }

    private static SettlementFacts factsAt(int step) {
        return new SettlementFacts(
                true,
                step > 0,
                step > 1 ? 2 : 0,
                step > 2,
                toolsDone(step),
                step > MINE_STEP,
                step > 11,
                step > 12,
                step > 13,
                step > 14,
                step > 15);
    }

    private static SettlementFacts emptySite() {
        return new SettlementFacts(true, false, 0, false, Set.of(), false, false, false, false, false, false);
    }

    @Test
    void next_EmptySettlement_ClearGrounds() {
        assertEquals(
                Optional.of(BoardCase.CLEAR_GROUNDS),
                FirstStepsPlan.next(emptySite()),
                "Пустое поселение с домом начинается с зачистки территории: первый шаг "
                        + "цепочки из источника, без него сундуки ставятся в лесу");
    }

    @Test
    void next_Chain_StrictOrder() {
        List<BoardCase> chain = chain();
        for (int step = 0; step < chain.size(); step++) {
            assertEquals(
                    Optional.of(chain.get(step)),
                    FirstStepsPlan.next(factsAt(step)),
                    "Шаг " + step + " цепочки обязан давать " + chain.get(step)
                            + ": пропуск шага невозможен, иначе забор встанет до входа в шахту");
        }
    }

    @Test
    void next_Tools_WoodenBeforeStone() {
        List<BoardCase> chain = chain();
        for (BoardCase wooden : ToolSet.wooden()) {
            for (BoardCase stone : ToolSet.stone()) {
                assertTrue(
                        chain.indexOf(wooden) < chain.indexOf(stone),
                        "Деревянный " + wooden + " обязан идти до каменного " + stone
                                + ": каменная ступень открывается по выполнению деревянной");
            }
        }
    }

    @Test
    void next_WoodenToolsDoneNoMine_BuildMineBeforeStoneTools() {
        Set<String> wooden = new HashSet<>();
        ToolSet.wooden().forEach(boardCase -> wooden.add(ToolSet.stockKey(boardCase)));
        SettlementFacts facts =
                new SettlementFacts(true, true, 2, true, wooden, false, false, false, false, false, false);

        assertEquals(
                Optional.of(BoardCase.BUILD_MINE),
                FirstStepsPlan.next(facts),
                "после деревянного набора — шахта, а не каменные инструменты: булыжник для них добывается только "
                        + "в шахте (решение владельца 21.10), иначе дух роет ямы у дома");
    }

    @Test
    void next_WoodenMissing_ReturnedEvenWithStoneTool() {
        SettlementFacts facts = new SettlementFacts(
                true,
                true,
                2,
                true,
                Set.of(ToolSet.stockKey(BoardCase.CRAFT_STONE_PICKAXE)),
                true,
                true,
                true,
                true,
                false,
                false);

        assertEquals(
                Optional.of(BoardCase.CRAFT_WOODEN_PICKAXE),
                FirstStepsPlan.next(facts),
                "Наличие каменной кирки без деревянной не продвигает цепочку: деревянный набор "
                        + "обязан быть собран первым");
    }

    @Test
    void next_AllDone_Empty() {
        SettlementFacts complete = new SettlementFacts(
                true, true, 2, true, toolsDone(Integer.MAX_VALUE), true, true, true, true, true, true);

        assertEquals(
                Optional.empty(),
                FirstStepsPlan.next(complete),
                "Всё сделано — генератор молчит: новые задания не плодятся, иначе доска "
                        + "бесконечно ставила бы уже выполненные шаги");
    }

    @Test
    void next_NoHome_Empty() {
        SettlementFacts noHome =
                new SettlementFacts(false, false, 0, false, Set.of(), false, false, false, false, false, false);

        assertEquals(
                Optional.empty(),
                FirstStepsPlan.next(noHome),
                "Без дома генератор не начинает цепочку: дом — точка поселения, её ставит "
                        + "стратег или игрок, а не генератор первых шагов");
    }

    @Test
    void next_Is_Deterministic() {
        SettlementFacts facts = factsAt(FIRST_TOOL_STEP);

        assertEquals(
                FirstStepsPlan.next(facts),
                FirstStepsPlan.next(facts),
                "Генератор обязан быть детерминирован: по нему доска ставит задание, "
                        + "нестабильный выбор перетасует первые шаги между пересборками");
    }

    @Test
    void next_NullFacts_Rejected() {
        assertThrows(
                NullPointerException.class,
                () -> FirstStepsPlan.next(null),
                "Генератор обязан решать по фактам: null означает, что снимка поселения нет, "
                        + "а не «пустое поселение»");
    }

    @Test
    void next_NegativeChests_Rejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SettlementFacts(true, true, -1, true, Set.of(), true, true, true, true, true, true),
                "Отрицательное число сундуков обязано отвергаться: оно прошло бы как "
                        + "«сундуков нет» и сломало бы шаг цепочки");
    }

    @Test
    void next_NullTools_Rejected() {
        assertThrows(
                NullPointerException.class,
                () -> new SettlementFacts(true, true, 2, true, null, true, true, true, true, true, true),
                "Набор инструментов не может быть null: пустой набор задают пустым множеством, "
                        + "а null спрятал бы «инструментов нет» за отказом");
    }

    @Property
    void next_OnlyChainSteps_Returned(@ForAll("settlementFacts") SettlementFacts facts) {
        Optional<BoardCase> next = FirstStepsPlan.next(facts);
        List<BoardCase> chain = chain();

        assertTrue(
                next.isEmpty() || chain.contains(next.get()),
                "Результат генератора обязан быть шагом цепочки или пустым: иной кейс "
                        + "означал бы задание вне порядка первых шагов");
    }

    @Property
    void next_Monotone_ByFacts(
            @ForAll("settlementFacts") SettlementFacts facts, @ForAll("settlementFacts") SettlementFacts grown) {
        if (!facts.hasHome() || !growsFrom(facts, grown)) {
            return;
        }
        Optional<BoardCase> before = FirstStepsPlan.next(facts);
        Optional<BoardCase> after = FirstStepsPlan.next(grown);
        List<BoardCase> chain = chain();

        int beforeStep = before.map(chain::indexOf).orElse(chain.size());
        int afterStep = after.map(chain::indexOf).orElse(chain.size());

        assertTrue(
                afterStep >= beforeStep,
                "Рост фактов не обязан откатывать генератор назад: факты монотонно растут, "
                        + "и следующий шаг не может вернуться к пройденному");
    }

    private static boolean growsFrom(SettlementFacts from, SettlementFacts to) {
        return to.hasHome()
                && (!from.groundsCleared() || to.groundsCleared())
                && to.chests() >= from.chests()
                && (!from.craftingStation() || to.craftingStation())
                && to.tools().containsAll(from.tools())
                && (!from.mineEntrance() || to.mineEntrance())
                && (!from.fence() || to.fence())
                && (!from.lighting() || to.lighting())
                && (!from.houses() || to.houses())
                && (!from.smithingTable() || to.smithingTable())
                && (!from.anvil() || to.anvil());
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
                factsAt(9),
                factsAt(10),
                factsAt(11),
                factsAt(12),
                factsAt(13),
                factsAt(14),
                factsAt(15),
                new SettlementFacts(false, false, 0, false, Set.of(), false, false, false, false, false, false),
                new SettlementFacts(true, true, 1, true, Set.of(), true, false, true, false, false, false));
    }
}
