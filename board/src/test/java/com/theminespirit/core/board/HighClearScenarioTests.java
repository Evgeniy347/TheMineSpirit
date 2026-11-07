package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.graph.HighStrays;
import com.theminespirit.core.building.ScaffoldPlan;
import com.theminespirit.core.building.SpiritReach;
import com.theminespirit.core.building.ToolBudget;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.StrayGroups;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HighClearScenarioTests {
    private static final SpiritReach REACH = new SpiritReach(3, 15, 3);
    private static final int GROUND = 64;

    private static StrayGroups.Cell cell(int x, int y, int z) {
        return new StrayGroups.Cell(new BlockCoord(x, y, z), "minecraft:dirt");
    }

    private static int ground(int x, int z) {
        return GROUND;
    }

    private static Set<Integer> occupied(BlockCoord... cells) {
        Set<Integer> columns = new HashSet<>();
        for (BlockCoord cell : cells) {
            columns.add(cell.x() * 100_000 + cell.z());
        }
        return columns;
    }

    private static List<ScaffoldPlan.Stalk> stalks(HighStrays.Split split) {
        return split.high().stream()
                .map(stalk -> new ScaffoldPlan.Stalk(stalk.id(), stalk.cells()))
                .toList();
    }

    private static ScaffoldPlan.Plan plan(List<ScaffoldPlan.Stalk> stalks, Set<Integer> occupied) {
        return ScaffoldPlan.plan(
                stalks,
                REACH,
                HighClearScenarioTests::ground,
                anchor -> !occupied.contains(anchor.x() * 100_000 + anchor.z()));
    }

    @Test
    void highColumn_TwentyAboveGround_OneScaffoldWithExactHeightAndTopDownBreak() {
        List<StrayGroups.Cell> cells = List.of(cell(10, 84, 10));
        HighStrays.Split split = HighStrays.split(cells, REACH, HighClearScenarioTests::ground);

        assertEquals(
                0,
                split.reachable().size(),
                "Столб на 20 выше земли не достаётся с земли; досягаемость "
                        + "с стоянки на земле — 15, иначе высокий столб неотличим от обычного мусора");
        assertEquals(
                List.of("high:10,10"),
                split.high().stream().map(HighStrays.Stalk::id).toList(),
                "Высокое лишнее — отдельный факт мира high_strays: обычная расчистка его не возьмёт");

        ScaffoldPlan.Plan plan = plan(stalks(split), occupied(new BlockCoord(10, 84, 10)));
        assertEquals(1, plan.scaffolds().size(), "Один столб — одна подмость: " + plan);
        ScaffoldPlan.Scaffold scaffold = plan.scaffolds().get(0);
        assertEquals(19, scaffold.height(), "Высота башни — до верхнего блока: дух встаёт на 83 и достаёт 84");
        assertEquals(
                List.of(new BlockCoord(10, 84, 10)), scaffold.targetsTopDown(), "Снос начинается с верхнего блока");
        assertEquals(
                List.of(new BlockCoord(
                        scaffold.anchor().x(), 82, scaffold.anchor().z())),
                scaffold.dismantle().subList(0, 1),
                "Своя башня разбирается сверху вниз");
        assertEquals(19, scaffold.dismantle().size(), "Разбирается вся башня, а не только под ногами");
    }

    @Test
    void severalColumns_GreedyFewestScaffolds_FirstCoversMostColumns() {
        List<StrayGroups.Cell> cells = List.of(cell(10, 84, 10), cell(12, 84, 10), cell(30, 84, 10));
        HighStrays.Split split = HighStrays.split(cells, REACH, HighClearScenarioTests::ground);
        ScaffoldPlan.Plan plan = plan(
                stalks(split),
                occupied(new BlockCoord(10, 84, 10), new BlockCoord(12, 84, 10), new BlockCoord(30, 84, 10)));

        assertEquals(
                2,
                plan.scaffolds().size(),
                "Две подмости на три столба: первая дотягивается до двух, "
                        + "вторая — до дальнего; больше — лишние башни: " + plan);
        assertEquals(
                2,
                plan.scaffolds().get(0).stalks().size(),
                "Жадный порядок: сначала башня, покрывающая максимум столбов: " + plan.scaffolds());
        assertEquals(1, plan.scaffolds().get(1).stalks().size(), "Далее — оставшийся столб: " + plan.scaffolds());
        assertTrue(plan.unreachable().isEmpty(), "Все высокие клетки покрыты: " + plan.unreachable());
    }

    @Test
    void partiallyLowColumn_LowLeftToGroundCleanup_HighGoesToScaffold() {
        List<StrayGroups.Cell> cells = List.of(cell(10, 74, 10), cell(10, 84, 10));
        HighStrays.Split split = HighStrays.split(cells, REACH, HighClearScenarioTests::ground);

        assertEquals(
                List.of(new BlockCoord(10, 74, 10)),
                split.reachable().stream().map(StrayGroups.Cell::at).toList(),
                "Низ столба достаётся с земли и остаётся обычной расчистке: подмость не делает работу снизу");
        assertEquals(
                List.of(new BlockCoord(10, 84, 10)),
                split.high().get(0).cells(),
                "Подмость берёт только верх — остаток внизу после сноса снова уйдёт расчистке");
    }

    @Test
    void race_TwoSpirits_ScaffoldProtectedUntilLastBlock() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        BlockCoord target = new BlockCoord(10, 84, 10);
        BlockCoord tower = new BlockCoord(9, 83, 9);
        ScaffoldClaims claims = new ScaffoldClaims();
        claims.claim(new ScaffoldClaims.Claim("scaffold:9,64,9", first, 100, Set.of(target, tower)));

        assertFalse(claims.blocked(first, target, 50), "Исполнитель ломает свой столб беспрепятственно");
        assertFalse(claims.blocked(first, tower, 50), "Исполнитель разбирает свою башню");
        assertTrue(claims.blocked(second, target, 50), "Чужой дух не ломает цель активной карточки");
        assertTrue(claims.blocked(second, tower, 50), "Чужой дух не ломает чужую башню");

        HighStrays.Split forSecond = HighStrays.split(List.of(cell(10, 84, 10)), REACH, HighClearScenarioTests::ground);
        assertEquals(1, forSecond.high().size(), "Без защиты высокий столб виден как работа");
        assertEquals(
                0,
                HighStrays.split(List.of(), REACH, HighClearScenarioTests::ground)
                        .high()
                        .size(),
                "Клетки активной подмости исключены из входа графа: второй дух не получает их ни выравниванием, "
                        + "ни расчисткой, ни clear_floating");

        claims.removed(target);
        assertTrue(claims.blocked(second, tower, 50), "Сняли цель — башня ещё под защитой, пока стоит");
        claims.removed(tower);
        assertFalse(claims.blocked(second, tower, 50), "Последний блок снят — защиты больше нет");
        assertEquals(0, claims.claims(50).size(), "Запись подмости исчезла вместе с последним блоком");
    }

    @Test
    void abandonedScaffold_Expires_AndRemainsBecomeNormalStray() {
        UUID executor = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        BlockCoord high = new BlockCoord(10, 84, 10);
        BlockCoord low = new BlockCoord(9, 64, 9);
        ScaffoldClaims claims = new ScaffoldClaims();
        claims.claim(new ScaffoldClaims.Claim("scaffold:9,64,9", executor, 10, Set.of(high, low)));

        assertTrue(claims.blocked(other, high, 5), "Пока срок не истёк, чужая карточка не трогает подмость");
        assertFalse(claims.blocked(other, high, 11), "Смерть, усталость или отмена: защита истекла по сроку");
        assertEquals(0, claims.claims(11).size(), "Истёкшая запись не защищает ничего и не копится");

        HighStrays.Split split =
                HighStrays.split(List.of(cell(10, 84, 10), cell(9, 64, 9)), REACH, HighClearScenarioTests::ground);
        assertFalse(split.high().isEmpty(), "Остатки высокой башни снова видны clear_high");
        assertTrue(
                split.reachable().stream().anyMatch(cell -> cell.at().equals(low)),
                "Остатки башни у земли снова уходят обычной расчистке: " + split.reachable());
    }

    @Test
    void toolBudget_Shortage_NeedsCarriedTools() {
        ToolBudget.Plan one = ToolBudget.of(100, List.of(59));
        assertFalse(one.enough(), "Одной кирки на 100 ударов не хватает: карточка не отказывается, а собирает набор");
        assertEquals(41, one.missing(), "Не хватает 41 удара прочности");
        assertEquals(1, ToolBudget.toolsFor(one.missing(), 59), "К одной кирке дух берёт ещё одну — добрать прочность");
        assertEquals(2, ToolBudget.toolsFor(one.hits(), 59), "На всю карточку нужно два инструмента, а не один");

        ToolBudget.Plan two = ToolBudget.of(100, List.of(59, 59));
        assertTrue(two.enough(), "Двух кирок хватает на всю карточку: суммарная прочность и есть запас");
        assertEquals(2, two.tools(), "Оба инструмента участвуют в запасе, а не только тот, что в руке");
    }

    @Test
    void scenario_TwoSpirits_RaceAndCleanupEndWithNoHighAndNoTower() {
        UUID builder = UUID.randomUUID();
        UUID leveller = UUID.randomUUID();
        List<StrayGroups.Cell> strays = new ArrayList<>(List.of(cell(10, 84, 10), cell(12, 84, 10)));
        HighStrays.Split split = HighStrays.split(strays, REACH, HighClearScenarioTests::ground);
        ScaffoldPlan.Plan plan = plan(stalks(split), occupied(new BlockCoord(10, 84, 10), new BlockCoord(12, 84, 10)));
        ScaffoldPlan.Scaffold scaffold = plan.scaffolds().get(0);

        ScaffoldClaims claims = new ScaffoldClaims();
        Set<BlockCoord> claimCells = new HashSet<>(scaffold.cells());
        claimCells.addAll(scaffold.towerCells());
        claims.claim(new ScaffoldClaims.Claim(scaffold.id(), builder, 10_000, claimCells));

        List<StrayGroups.Cell> forLeveller =
                strays.stream().filter(cell -> !claims.claimed(cell.at(), 50)).toList();
        assertTrue(
                HighStrays.split(forLeveller, REACH, HighClearScenarioTests::ground)
                        .high()
                        .isEmpty(),
                "Выравнивание не берёт клетки активной подмости: у второго духа нет работы на этом столбе");

        for (BlockCoord target : scaffold.targetsTopDown()) {
            assertFalse(claims.blocked(builder, target, 60), "Свой столб ломать разрешено");
            claims.removed(target);
            strays.removeIf(cell -> cell.at().equals(target));
        }
        for (BlockCoord own : scaffold.dismantle()) {
            assertFalse(claims.blocked(builder, own, 60), "Своя башня разбирается законно");
            claims.removed(own);
        }

        assertEquals(0, claims.claims(60).size(), "После последнего блока защиты нет");
        assertTrue(
                HighStrays.split(strays, REACH, HighClearScenarioTests::ground)
                        .high()
                        .isEmpty(),
                "Аудит мира: столбов нет, башни нет — карточка закрыта по миру");
        assertFalse(claims.blocked(leveller, new BlockCoord(10, 84, 10), 60), "Защиты больше нет ни для кого");
    }
}
