package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    private static final Set<BoardCase> LAYOUT = EnumSet.of(
            BoardCase.BUILD_MINE,
            BoardCase.LIGHTING,
            BoardCase.BUILD_FENCE,
            BoardCase.BUILD_ORCHARD,
            BoardCase.BUILD_HOUSE);

    private static List<BoardCase> chain() {
        List<BoardCase> chain = new ArrayList<>();
        chain.add(BoardCase.CRAFTING_STATION);
        chain.addAll(ToolSet.wooden());
        chain.add(BoardCase.CLEAR_GROUNDS);
        chain.add(BoardCase.STOCK_CHESTS);
        chain.add(BoardCase.BUILD_MINE);
        chain.addAll(ToolSet.stone());
        chain.add(BoardCase.CRAFT_FURNACE);
        chain.add(BoardCase.LIGHTING);
        chain.add(BoardCase.BUILD_FENCE);
        chain.add(BoardCase.BUILD_ORCHARD);
        chain.add(BoardCase.BUILD_HOUSE);
        chain.add(BoardCase.CRAFT_SMITHING_TABLE);
        chain.add(BoardCase.CRAFT_ANVIL);
        return chain;
    }

    private static SettlementFacts factsAt(int step) {
        List<BoardCase> chain = chain();
        Set<BoardCase> passed = EnumSet.noneOf(BoardCase.class);
        passed.addAll(chain.subList(0, Math.min(step, chain.size())));
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
                world.contains(BoardCase.CLEAR_GROUNDS),
                world.contains(BoardCase.STOCK_CHESTS) ? 2 : 0,
                world.contains(BoardCase.CRAFTING_STATION),
                tools,
                world.contains(BoardCase.BUILD_MINE),
                world.contains(BoardCase.CRAFT_FURNACE),
                world.contains(BoardCase.BUILD_FENCE),
                world.contains(BoardCase.LIGHTING),
                world.contains(BoardCase.BUILD_HOUSE),
                world.contains(BoardCase.BUILD_ORCHARD),
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
                chain.indexOf(BoardCase.CLEAR_GROUNDS) + 1,
                chain.indexOf(BoardCase.STOCK_CHESTS),
                "Сундуки — сразу после выравнивания (владелец 24.10, INC-63): двор на высоте плана");
        for (BoardCase building : List.of(BoardCase.BUILD_FENCE, BoardCase.BUILD_HOUSE)) {
            assertTrue(
                    chain.indexOf(building) > light,
                    building + " — после факелов: постройки только после инструментов, шахты и света");
        }
        assertTrue(
                chain.indexOf(BoardCase.CLEAR_GROUNDS) < mine && chain.indexOf(BoardCase.CLEAR_GROUNDS) < light,
                "Расчистка — после деревянных инструментов, до шахты и факелов: факелы и двор встают на ровном участке");
    }

    @Test
    void next_MineDoneInLedgerButDescentMissing_MineAgain() {
        Set<BoardCase> world = EnumSet.noneOf(BoardCase.class);
        world.addAll(chain().subList(0, chain().indexOf(BoardCase.BUILD_MINE)));
        SettlementFacts facts = facts(world, Set.of(BoardCase.BUILD_MINE), Set.of());

        assertEquals(
                Optional.of(BoardCase.BUILD_MINE),
                FirstStepsPlan.next(facts),
                "Шахта закрыта в журнале (старой сборкой), но спуска по схеме на месте плана нет: шахта снова в "
                        + "очереди, а старая яма шахтой не считается (INC-59)");
    }

    @Test
    void next_LayoutDoneInJournalButMissingInWorld_StepQueuedAgain() {
        int lighting = chain().indexOf(BoardCase.LIGHTING);
        Set<BoardCase> world = EnumSet.copyOf(chain().subList(0, lighting));
        Set<BoardCase> done = EnumSet.of(BoardCase.BUILD_MINE, BoardCase.LIGHTING);
        SettlementFacts facts = facts(world, done, Set.of());

        assertEquals(
                Optional.of(BoardCase.LIGHTING),
                FirstStepsPlan.next(facts),
                "Свет закрыт в журнале (старой сборкой), но факелов в клетках раскладки нет: шаг снова в очереди, "
                        + "иначе поселение навсегда без факелов (INC-50)");
    }

    @Test
    void next_NoTreesButPlotUneven_ClearGroundsQueued() {
        int clear = chain().indexOf(BoardCase.CLEAR_GROUNDS);
        SettlementFacts level = factsAt(clear);
        SettlementFacts facts = new SettlementFacts(
                true,
                true,
                false,
                level.chests(),
                level.craftingStation(),
                level.tools(),
                level.mineEntrance(),
                level.furnace(),
                false,
                false,
                false,
                false,
                false,
                false,
                level.done(),
                level.blocked());

        assertEquals(
                Optional.of(BoardCase.CLEAR_GROUNDS),
                FirstStepsPlan.next(facts),
                "Деревьев нет, но перепад высот вне допуска: расчистка с выравниванием встаёт в очередь — "
                        + "выравнивание отдельный факт, а не «нет брёвен» (INC-50)");
    }

    @Test
    void chain_Order_LevelingBeforeYard() {
        List<BoardCase> chain = FirstStepsPlan.chain();
        int leveling = chain.indexOf(BoardCase.CLEAR_GROUNDS);

        assertEquals(
                BoardCase.CRAFTING_STATION,
                chain.get(0),
                "Первым — временный верстак рядом с духом: без него не скрафтить деревянные инструменты");
        assertTrue(
                chain.indexOf(BoardCase.CRAFT_WOODEN_AXE) < leveling,
                "Деревянные инструменты — до выравнивания: топор нужен расчистке");
        assertTrue(
                leveling < chain.indexOf(BoardCase.STOCK_CHESTS) && leveling < chain.indexOf(BoardCase.CRAFT_FURNACE),
                "Двор (сундуки, печь) — после выравнивания, на высоте плана: двор до него вставал в яме на 4 ниже "
                        + "плана, и все духи «застревали» в одной точке (INC-63, порядок владельца)");
    }

    @Test
    void next_BlockedStep_NextStepGiven() {
        SettlementFacts facts = facts(Set.of(), Set.of(), Set.of(BoardCase.CRAFTING_STATION));

        assertEquals(
                Optional.of(BoardCase.CRAFT_WOODEN_PICKAXE),
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
                false,
                base.done(),
                base.blocked());

        assertEquals(
                Optional.of(BoardCase.CRAFT_WOODEN_AXE),
                FirstStepsPlan.next(facts),
                "Каменная кирка заменяет деревянную: деревянную кирку не крафтят, следующий — деревянный топор");
    }

    @Test
    void ready_AfterLeveling_LayoutAndStoneStepsTogether() {
        List<BoardCase> chain = chain();
        List<BoardCase> ready = FirstStepsPlan.ready(factsAt(chain.indexOf(BoardCase.CLEAR_GROUNDS) + 1));

        assertTrue(
                ready.containsAll(List.of(
                        BoardCase.STOCK_CHESTS,
                        BoardCase.BUILD_MINE,
                        BoardCase.CRAFT_FURNACE,
                        BoardCase.LIGHTING,
                        BoardCase.BUILD_FENCE,
                        BoardCase.BUILD_HOUSE)),
                "После выравнивания шахта, двор, свет, ограда, хижина и печь открываются сразу на разных духов "
                        + "(решение владельца 25.10): по цепочке после выравнивания работал один дух, двое стояли; "
                        + "открыто " + ready);
        assertFalse(
                ready.contains(BoardCase.CRAFT_ANVIL),
                "Наковальня (железо) — после дома по цепочке: её добыча отнимала духов у этапа 1");
        assertEquals(
                List.of(BoardCase.CLEAR_GROUNDS),
                FirstStepsPlan.ready(factsAt(chain.indexOf(BoardCase.CLEAR_GROUNDS))),
                "До выравнивания включительно — по одному шагу: ограда и хижина не встают на неровном участке");
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
                false, false, false, 0, false, Set.of(), false, false, false, false, false, false, false, false,
                Set.of(), Set.of());

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
                        true, true, true, -1, true, Set.of(), true, true, true, true, true, true, true, true, Set.of(),
                        Set.of()),
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

    @Test
    void next_StageOneBlockedByMaterial_IronStepsWait() {
        List<BoardCase> chain = chain();
        Set<BoardCase> world = EnumSet.copyOf(chain.subList(0, chain.indexOf(BoardCase.LIGHTING)));
        Set<BoardCase> blocked =
                EnumSet.of(BoardCase.LIGHTING, BoardCase.BUILD_FENCE, BoardCase.BUILD_ORCHARD, BoardCase.BUILD_HOUSE);
        SettlementFacts facts = facts(world, EnumSet.of(BoardCase.BUILD_MINE), blocked);
        assertEquals(
                Optional.empty(),
                FirstStepsPlan.next(facts),
                "Свет, ограда и хижина упали в BLOCKED — верстак кузнеца и наковальня не открываются: gather:iron_ingot "
                        + "на этапе 1 уводил духов от снабжения (INC-78)");
        assertEquals(
                List.of(BoardCase.LIGHTING, BoardCase.BUILD_FENCE, BoardCase.BUILD_ORCHARD, BoardCase.BUILD_HOUSE),
                FirstStepsPlan.blockedStageOne(facts),
                "Заблокированные шаги этапа 1 видны снабжению: по ним ставится добыча недостающего (INC-79)");
    }

    @Test
    void next_LeveledThenHoleDug_LevelingNotReopened() {
        List<BoardCase> chain = chain();
        Set<BoardCase> world = EnumSet.copyOf(chain.subList(0, chain.indexOf(BoardCase.CLEAR_GROUNDS)));
        world.add(BoardCase.BUILD_FENCE);
        SettlementFacts facts = facts(world, EnumSet.of(BoardCase.CLEAR_GROUNDS, BoardCase.BUILD_FENCE), Set.of());
        assertEquals(
                Optional.of(BoardCase.STOCK_CHESTS),
                FirstStepsPlan.next(facts),
                "Участок выровнен и огорожен, потом в нём дыра — выравнивание не открывается заново: дыру засыпает "
                        + "аудит ровности, повторная расчистка под готовыми постройками не идёт (INC-78)");
    }
}
