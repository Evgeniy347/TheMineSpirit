package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.api.BoardCase;
import com.theminespirit.core.board.api.Point;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.town.PlotLeveling;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;

class CraterNeedTests {

    @Test
    void estimate_ZeroRadius_ZeroBlocks() {
        Map<String, Integer> estimate = CraterNeed.estimate(new Point(0, 0), 0);

        assertEquals(
                Map.of(),
                estimate,
                "Нулевой радиус — ноль блоков: взрыв без радиуса ничего не выкопал, "
                        + "и задание о засыпке не ставится"
                        + " Иначе тест estimate_ZeroRadius_ZeroBlocks перестанет ловить эту поломку. При смене estimate_ZeroRadius_ZeroBlocks проверь это утверждение.");
    }

    @Test
    void estimate_CreeperRadius_DirtBlocks() {
        Map<String, Integer> estimate = CraterNeed.estimate(new Point(100, -50), 3);

        assertEquals(
                Map.of("dirt", 29),
                estimate,
                "Воронка крипера радиусом 3 — 29 блоков земли (ceil(π·9)): доска ставит "
                        + "гейт ровно на это число, не больше и не меньше"
                        + " Иначе тест estimate_CreeperRadius_DirtBlocks перестанет ловить эту поломку. При смене estimate_CreeperRadius_DirtBlocks проверь это утверждение.");
    }

    @Test
    void estimate_Monotone_ByRadius() {
        Map<String, Integer> small = CraterNeed.estimate(new Point(0, 0), 2);
        Map<String, Integer> large = CraterNeed.estimate(new Point(0, 0), 4);

        assertTrue(
                large.get("dirt") >= small.get("dirt"),
                "Оценка обязана быть монотонной по радиусу: большая воронка требует не "
                        + "меньше земли, иначе засыпка оставляет яму"
                        + " При смене estimate_Monotone_ByRadius проверь это утверждение.");
    }

    @Test
    void estimate_NullBlast_Rejected() {
        assertThrows(
                NullPointerException.class,
                () -> CraterNeed.estimate(null, 3),
                "Точка взрыва обязана быть задана: без неё неизвестен центр воронки"
                        + " Иначе тест estimate_NullBlast_Rejected перестанет ловить эту поломку. При смене estimate_NullBlast_Rejected проверь это утверждение.");
    }

    @Test
    void estimate_NegativeRadius_Rejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> CraterNeed.estimate(new Point(0, 0), -1),
                "Отрицательный радиус обязан отвергаться: он прошёл бы как «взрыв есть, "
                        + "а воронки нет» и скрыл бы реальный радиус от наблюдателя"
                        + " Иначе тест estimate_NegativeRadius_Rejected перестанет ловить эту поломку. При смене estimate_NegativeRadius_Rejected проверь это утверждение.");
    }

    @Test
    void fillBlocks_LevelingTarget_WholePitVolume() {
        TicketTarget target =
                TicketTarget.at(new BlockCoord(0, 64, 0)).withCraterBlocks(99).withLeveling(leveling(3));

        assertEquals(
                3,
                CraterNeed.fillBlocks(target),
                "Пит с планом выравнивания засыпается по объёму подсыпки, а не по числу клеток: "
                        + "иначе карточка назовёт объём, на который духу не хватит земли; "
                        + "При смене PlotLeveling.fillVolume проверь уровень ям и TicketDetails.pit");
        assertEquals(
                Map.of("dirt", 3),
                CraterNeed.fillNeed(target),
                "Нужда засыпки — земля ровно в объёме питы: без этого числа доска покажет пустую карточку "
                        + "или чужую землю и не поставит гейт склада; При смене материала засыпки проверь "
                        + "FillCraterScript и BoardMaterials");
    }

    @Test
    void fillBlocks_CraterWithoutLeveling_ProfileCells() {
        TicketTarget target = TicketTarget.at(new BlockCoord(10, 64, -5)).withCraterBlocks(29);

        assertEquals(
                29,
                CraterNeed.fillBlocks(target),
                "Воронка без плана выравнивания засыпается по числу клеток профиля: "
                        + "иначе доска покажет чужое число и не поставит гейт на реальную землю; "
                        + "При смене профиля воронок в CraterWatch проверь это число");
    }

    @Test
    void fillBlocks_EmptyTarget_FallsBackToEstimate() {
        TicketTarget target = TicketTarget.at(new BlockCoord(0, 64, 0));

        assertEquals(
                CraterNeed.estimate(new Point(0, 0), BoardTuning.craterBlocksEstimateRadius())
                        .get("dirt"),
                CraterNeed.fillBlocks(target),
                "Цель без объёма и выравнивания получает оценку по радиусу крипера, как в плане засыпки: "
                        + "иначе карточка покажет ноль земли при непустой воронке; "
                        + "При смене craterBlocksEstimateRadius проверь PitFillPlan");
    }

    @Test
    void fillNeed_PlanAcquires_ExactlyFillBlocks() {
        TicketTarget target = TicketTarget.at(new BlockCoord(0, 64, 0)).withLeveling(leveling(5));
        List<ScriptCall> calls = new ArrayList<>();
        collect(TicketPlans.plan(BoardCase.FILL_CRATER, target), calls);
        int acquired = calls.stream()
                .filter(call -> "acquire".equals(call.script()))
                .mapToInt(call -> (Integer) call.params().get("count"))
                .sum();

        assertEquals(
                CraterNeed.fillBlocks(target),
                acquired,
                "План засыпки набирает ровно объём питы: иначе карточка и исполнитель назовут разные "
                        + "числа и дух встанет в яму без земли; При смене PitFillPlan проверь fillNeed");
    }

    @Test
    void outsideTerritory_BlastInside_NoTask() {
        List<Point> homes = List.of(new Point(0, 0), new Point(32, 32));
        boolean outside = CraterNeed.outsideTerritory(new Point(10, 10), homes, 64);

        assertFalse(
                outside,
                "Взрыв в центре поселения — внутри территории: задания доски не ставится, "
                        + "воронка в зоне работ координатора"
                        + " Иначе тест outsideTerritory_BlastInside_NoTask перестанет ловить эту поломку. При смене outsideTerritory_BlastInside_NoTask проверь это утверждение.");
    }

    @Test
    void outsideTerritory_BlastOutside_Task() {
        List<Point> homes = List.of(new Point(0, 0), new Point(32, 32));
        boolean outside = CraterNeed.outsideTerritory(new Point(200, 200), homes, 64);

        assertTrue(
                outside,
                "Взрыв за радиусом от всех домов — задание есть: доска ставит FILL_CRATER "
                        + "с числом блоков, иначе воронка за территорией живёт вечно"
                        + " При смене outsideTerritory_BlastOutside_Task проверь это утверждение.");
    }

    @Test
    void outsideTerritory_EmptyHomes_Task() {
        boolean outside = CraterNeed.outsideTerritory(new Point(500, 500), List.of(), 64);

        assertTrue(
                outside,
                "Нет домов — нет территории: любая воронка за её пределами, задание ставится"
                        + " Иначе тест outsideTerritory_EmptyHomes_Task перестанет ловить эту поломку. При смене outsideTerritory_EmptyHomes_Task проверь это утверждение.");
    }

    @Test
    void outsideTerritory_NullHomes_Rejected() {
        assertThrows(
                NullPointerException.class,
                () -> CraterNeed.outsideTerritory(new Point(0, 0), null, 64),
                "Список домов=null обязан отвергаться: пустую территорию задают пустым списком"
                        + " Иначе тест outsideTerritory_NullHomes_Rejected перестанет ловить эту поломку. При смене outsideTerritory_NullHomes_Rejected проверь это утверждение.");
    }

    @Test
    void outsideTerritory_Is_Deterministic() {
        List<Point> homes = List.of(new Point(0, 0));

        assertEquals(
                CraterNeed.outsideTerritory(new Point(100, 0), homes, 64),
                CraterNeed.outsideTerritory(new Point(100, 0), homes, 64),
                "Признак «за территорией» обязан быть детерминирован: по нему доска "
                        + "ставит или не ставит задание, нестабильность — случайное решение"
                        + " Иначе тест outsideTerritory_Is_Deterministic перестанет ловить эту поломку. При смене outsideTerritory_Is_Deterministic проверь это утверждение.");
    }

    @Property
    void estimate_NonNegative_Monotone(
            @ForAll("blastPoints") Point blast,
            @ForAll("radii") double smallRadius,
            @ForAll("radii") double largeRadius) {
        double first = Math.min(smallRadius, largeRadius);
        double second = Math.max(smallRadius, largeRadius);
        Map<String, Integer> small = CraterNeed.estimate(blast, first);
        Map<String, Integer> large = CraterNeed.estimate(blast, second);

        assertTrue(
                small.getOrDefault("dirt", 0) >= 0,
                "Оценка обязана быть неотрицательной: отрицательное число блоков сломало бы гейт"
                        + " Иначе тест estimate_NonNegative_Monotone перестанет ловить эту поломку. При смене estimate_NonNegative_Monotone проверь это утверждение.");
        assertTrue(
                large.getOrDefault("dirt", 0) >= small.getOrDefault("dirt", 0),
                "Оценка обязана быть монотонной по радиусу: иначе меньшая воронка "
                        + "потребовала бы больше земли, чем большая"
                        + " При смене estimate_NonNegative_Monotone проверь это утверждение.");
    }

    @Property
    void outsideTerritory_Deterministic_HomeSensitive(
            @ForAll("blastPoints") Point blast, @ForAll("homes") List<Point> homes, @ForAll("radii") double radius) {
        assertEquals(
                CraterNeed.outsideTerritory(blast, homes, radius),
                CraterNeed.outsideTerritory(blast, homes, radius),
                "Признак «за территорией» детерминирован: иначе наблюдатель видит "
                        + "разное решение при одних и тех же домах и взрыве"
                        + " При смене outsideTerritory_Deterministic_HomeSensitive проверь это утверждение.");
    }

    private static PlotLeveling leveling(int blocks) {
        return new PlotLeveling(
                List.of(), List.of(new PlotLeveling.Box(0, 64, 0, 0, 64, blocks - 1)), PlotLeveling.DEFAULT_BLOCK);
    }

    private static void collect(TaskNode node, List<ScriptCall> found) {
        if (node instanceof SequenceNode sequence) {
            sequence.steps().forEach(step -> collect(step, found));
        } else if (node instanceof ScriptCall call) {
            found.add(call);
        }
    }

    @Provide
    Arbitrary<Point> blastPoints() {
        return Arbitraries.of(new Point(0, 0), new Point(10, 10), new Point(200, 200), new Point(-300, 150));
    }

    @Provide
    Arbitrary<List<Point>> homes() {
        return Arbitraries.of(
                List.of(), List.of(new Point(0, 0)), List.of(new Point(0, 0), new Point(32, 32), new Point(-50, 20)));
    }

    @Provide
    Arbitrary<Double> radii() {
        return Arbitraries.doubles().between(0, 200);
    }
}
