package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumSet;
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
    private static final Set<BoardCase> LAYOUT =
            EnumSet.of(BoardCase.LIGHTING, BoardCase.BUILD_FENCE, BoardCase.BUILD_HOUSE);

    private static List<BoardCase> chain() {
        List<BoardCase> chain = new ArrayList<>();
        chain.add(BoardCase.CRAFTING_STATION);
        chain.add(BoardCase.STOCK_CHESTS);
        chain.addAll(ToolSet.wooden());
        chain.add(BoardCase.BUILD_MINE);
        chain.addAll(ToolSet.stone());
        chain.add(BoardCase.CRAFT_FURNACE);
        chain.add(BoardCase.CLEAR_GROUNDS);
        chain.add(BoardCase.LIGHTING);
        chain.add(BoardCase.BUILD_FENCE);
        chain.add(BoardCase.BUILD_HOUSE);
        chain.add(BoardCase.CRAFT_SMITHING_TABLE);
        chain.add(BoardCase.CRAFT_ANVIL);
        return chain;
    }

    private static SettlementFacts factsAt(int step) {
        List<BoardCase> chain = chain();
        Set<BoardCase> passed = EnumSet.noneOf(BoardCase.class);
        passed.addAll(chain.subList(0, Math.min(step, chain.size())));
        // Постройки раскладки (свет, ограда, дом) готовы только по журналу: пройденные — закрыты в нём.
        Set<BoardCase> done = EnumSet.noneOf(BoardCase.class);
        passed.stream().filter(LAYOUT::contains).forEach(done::add);
        return facts(passed, done, Set.of());
    }

    private static SettlementFacts facts(Set<BoardCase> world, Set<BoardCase> done, Set<BoardCase> blocked) {
        Set<String> tools = new HashSet<>();
        for (BoardCase tool : ToolSet.chain()) {
            if (world.contains(tool)) {
                tools.add(ToolSet.stockKey(tool));
            }
        }
        return new SettlementFacts(
                true,
                world.contains(BoardCase.CLEAR_GROUNDS),
                world.contains(BoardCase.STOCK_CHESTS) ? 2 : 0,
                world.contains(BoardCase.CRAFTING_STATION),
                tools,
                world.contains(BoardCase.BUILD_MINE),
                world.contains(BoardCase.CRAFT_FURNACE),
                world.contains(BoardCase.BUILD_FENCE),
                world.contains(BoardCase.LIGHTING),
                world.contains(BoardCase.BUILD_HOUSE),
                world.contains(BoardCase.CRAFT_SMITHING_TABLE),
                world.contains(BoardCase.CRAFT_ANVIL),
                done,
                blocked);
    }

    private static SettlementFacts emptySite() {
        return facts(Set.of(), Set.of(), Set.of());
    }

    @Test
    void next_EmptySettlement_CraftingStationFirst() {
        assertEquals(
                Optional.of(BoardCase.CRAFTING_STATION),
                FirstStepsPlan.next(emptySite()),
                "Пустое поселение начинается с верстака: без него нет инструментов (решение владельца 21.10 — "
                        + "сначала инструменты, а не расчистка участка и сундуки)");
    }

    @Test
    void next_Chain_StrictOrder() {
        List<BoardCase> chain = chain();
        for (int step = 0; step < chain.size(); step++) {
            assertEquals(
                    Optional.of(chain.get(step)),
                    FirstStepsPlan.next(factsAt(step)),
                    "Шаг " + step + " цепочки обязан давать " + chain.get(step)
                            + ": порядок владельца — инструменты → шахта → камень → печь → факелы → постройки");
        }
        assertEquals(chain, FirstStepsPlan.chain(), "Цепочка генератора совпадает с порядком владельца");
    }

    @Test
    void next_Order_MineBeforeStoneToolsAndBuildings() {
        List<BoardCase> chain = FirstStepsPlan.chain();
        int mine = chain.indexOf(BoardCase.BUILD_MINE);
        for (BoardCase wooden : ToolSet.wooden()) {
            assertTrue(chain.indexOf(wooden) < mine, "Деревянный " + wooden + " — до шахты: копать нечем");
        }
        for (BoardCase stone : ToolSet.stone()) {
            assertTrue(
                    chain.indexOf(stone) > mine,
                    "Каменный " + stone + " — после шахты: булыжник добывается в шахте, а не ямами у дома (INC-16)");
        }
        int light = chain.indexOf(BoardCase.LIGHTING);
        assertEquals(
                chain.indexOf(BoardCase.CRAFTING_STATION) + 1,
                chain.indexOf(BoardCase.STOCK_CHESTS),
                "Сундуки — сразу после верстака (владелец 23.10): добыча с первых шагов складывается в сундук");
        for (BoardCase building : List.of(BoardCase.BUILD_FENCE, BoardCase.BUILD_HOUSE)) {
            assertTrue(
                    chain.indexOf(building) > light,
                    building + " — после факелов: постройки только после инструментов, шахты и света");
        }
        assertTrue(
                chain.indexOf(BoardCase.CLEAR_GROUNDS) > chain.indexOf(BoardCase.CRAFT_FURNACE)
                        && chain.indexOf(BoardCase.CLEAR_GROUNDS) < light,
                "Расчистка — после инструментов и шахты, но до факелов: факелы встают по углам участка, где до"
                        + " расчистки деревья");
    }

    @Test
    void next_StepDoneInLedgerButFactMissing_Skipped() {
        Set<BoardCase> world = EnumSet.noneOf(BoardCase.class);
        world.addAll(chain().subList(0, chain().indexOf(BoardCase.BUILD_MINE)));
        SettlementFacts facts = facts(world, Set.of(BoardCase.BUILD_MINE), Set.of());

        assertEquals(
                Optional.of(BoardCase.CRAFT_STONE_PICKAXE),
                FirstStepsPlan.next(facts),
                "Шахта сделана по журналу доски, но лестницы в мире нет (спуск её не ставит): цепочка идёт дальше, "
                        + "а не встаёт навсегда на DONE-шаге, который нельзя переиздать");
    }

    @Test
    void next_BlockedStep_NextStepGiven() {
        SettlementFacts facts = facts(Set.of(), Set.of(), Set.of(BoardCase.CRAFTING_STATION));

        assertEquals(
                Optional.of(BoardCase.STOCK_CHESTS),
                FirstStepsPlan.next(facts),
                "Шаг, упавший до BLOCKED, не держит цепочку: дух получает следующее задание, а не стоит "
                        + "(правило владельца — провал → инцидент и новое задание)");
    }

    @Test
    void next_StoneToolCoversWooden_WoodenSkipped() {
        Set<BoardCase> world = EnumSet.of(BoardCase.CRAFTING_STATION);
        SettlementFacts base = facts(world, Set.of(), Set.of());
        SettlementFacts facts = new SettlementFacts(
                true,
                false,
                2,
                true,
                Set.of(ToolSet.stockKey(BoardCase.CRAFT_STONE_PICKAXE)),
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                base.done(),
                base.blocked());

        assertEquals(
                Optional.of(BoardCase.CRAFT_WOODEN_AXE),
                FirstStepsPlan.next(facts),
                "Каменная кирка заменяет деревянную: деревянную кирку не крафтят, следующий — деревянный топор");
    }

    @Test
    void next_AllDone_Empty() {
        assertEquals(
                Optional.empty(),
                FirstStepsPlan.next(factsAt(chain().size())),
                "Все шаги сделаны — первых шагов больше нет");
    }

    @Test
    void next_NoHome_Empty() {
        SettlementFacts noHome = new SettlementFacts(
                false, false, 0, false, Set.of(), false, false, false, false, false, false, false, Set.of(), Set.of());

        assertEquals(Optional.empty(), FirstStepsPlan.next(noHome), "Без дома первых шагов нет: цепочку задаёт дом");
    }

    @Test
    void next_NullFacts_Rejected() {
        assertThrows(
                NullPointerException.class, () -> FirstStepsPlan.next(null), "Генератор без фактов не решает вслепую");
    }

    @Test
    void facts_NegativeChests_Rejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SettlementFacts(
                        true, true, -1, true, Set.of(), true, true, true, true, true, true, true, Set.of(), Set.of()),
                "Отрицательное число сундуков — ошибка адаптера");
    }

    @Property
    void next_OnlyChainSteps_Returned(@ForAll("settlementFacts") SettlementFacts facts) {
        Optional<BoardCase> next = FirstStepsPlan.next(facts);

        assertTrue(
                next.isEmpty() || chain().contains(next.get()),
                "Результат генератора — шаг цепочки или пусто: иной кейс — задание вне порядка первых шагов");
    }

    @Property
    void next_Monotone_ByFacts(@ForAll("steps") int from, @ForAll("steps") int to) {
        if (to < from) {
            return;
        }
        List<BoardCase> chain = chain();
        int before = FirstStepsPlan.next(factsAt(from)).map(chain::indexOf).orElse(chain.size());
        int after = FirstStepsPlan.next(factsAt(to)).map(chain::indexOf).orElse(chain.size());

        assertTrue(after >= before, "Рост фактов не откатывает генератор к пройденному шагу");
    }

    @Provide
    Arbitrary<Integer> steps() {
        return Arbitraries.integers().between(0, chain().size());
    }

    @Provide
    Arbitrary<SettlementFacts> settlementFacts() {
        return Arbitraries.integers().between(0, chain().size()).map(FirstStepsPlanTests::factsAt);
    }

    @Test
    void nextAfter_ClearingRuns_FenceMaterialsAhead() {
        SettlementFacts facts = factsAt(chain().indexOf(BoardCase.CLEAR_GROUNDS));

        assertEquals(
                Optional.of(BoardCase.BUILD_FENCE),
                FirstStepsPlan.nextAfter(
                        facts, BoardCase.CLEAR_GROUNDS, Set.of(BoardCase.BUILD_FENCE, BoardCase.BUILD_HOUSE)),
                "Пока идёт расчистка, материалы ограды добываются заранее: духи не простаивают");
    }
}
