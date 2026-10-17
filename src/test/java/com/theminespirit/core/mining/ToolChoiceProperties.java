package com.theminespirit.core.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.mining.ToolChoice.Choice;
import com.theminespirit.core.mining.ToolChoice.ToolOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class ToolChoiceProperties {
    @Provide
    Arbitrary<List<ToolOption>> optionLists() {
        Arbitrary<ToolOption> option = Combinators.combine(
                Arbitraries.integers().between(0, 35),
                Arbitraries.of(0.5, 1.0, 2.0, 4.0, 6.0, 8.0, 12.0),
                Arbitraries.of(true, false)).as(ToolOption::of);
        return option.list().ofMaxSize(12);
    }

    @Provide
    Arbitrary<List<ToolOption>> durableOptions() {
        Arbitrary<ToolOption> option = Combinators.combine(
                Arbitraries.integers().between(0, 35),
                Arbitraries.of(0.5, 1.0, 2.0, 4.0, 6.0, 8.0, 12.0),
                Arbitraries.of(true, false),
                Arbitraries.integers().between(0, 40),
                Arbitraries.integers().between(0, 40),
                Arbitraries.of(true, false)).as(ToolOption::new);
        return option.list().ofMaxSize(12).map(ToolChoiceProperties::uniqueSlots);
    }

    @Provide
    Arbitrary<ToolReserve> reserves() {
        return Combinators.combine(
                Arbitraries.doubles().between(0.0, 0.5),
                Arbitraries.integers().between(0, 64),
                Arbitraries.of(true, false)).as(ToolReserve::new);
    }

    @Property
    void best_AnyOptions_ChosenSlotHasMaximumSpeedAmongCorrect(@ForAll("optionLists") List<ToolOption> options) {
        Choice choice = ToolChoice.best(options);

        double fastestCorrect = options.stream().filter(ToolOption::correctForDrops).mapToDouble(ToolOption::speed)
                .max().orElse(0.0);
        if (choice.slot() != ToolChoice.HAND) {
            assertEquals(fastestCorrect, choice.speed(),
                    "Выбранный слот " + choice.slot() + " обязан иметь максимальную скорость среди правильных "
                            + "инструментов: " + options);
            assertTrue(choice.dropsAllowed() && choice.speed() > ToolChoice.HAND_SPEED,
                    "Инструмент выбирается только быстрее руки и только правильный: " + choice);
        } else {
            assertTrue(fastestCorrect <= ToolChoice.HAND_SPEED,
                    "Рука выбрана при быстром правильном инструменте в наборе: " + options);
        }
    }

    @Property
    void best_ShuffledOptions_GiveSameChoice(@ForAll("optionLists") List<ToolOption> options, @ForAll long seed) {
        List<ToolOption> shuffled = new ArrayList<>(options);
        Collections.shuffle(shuffled, new Random(seed));

        assertEquals(ToolChoice.best(options), ToolChoice.best(shuffled),
                "Порядок списка вариантов не должен влиять на выбор: слоты инвентаря перебираются в произвольном порядке");
    }

    @Property
    void best_SpentCorrectTool_NeverChosenWhenHealthyFasterExists(
            @ForAll("durableOptions") List<ToolOption> options, @ForAll("reserves") ToolReserve reserve) {
        List<ToolOption> healthy = options.stream()
                .filter(option -> option.correctForDrops() && !option.spent(reserve)).toList();
        boolean healthyFasterThanHand = healthy.stream().anyMatch(option -> option.speed() > ToolChoice.HAND_SPEED);
        Choice choice = ToolChoice.best(options, reserve);

        if (!healthyFasterThanHand) {
            return;
        }
        assertTrue(choice.slot() != ToolChoice.HAND,
                "Есть подходящий неизношенный инструмент быстрее руки, но выбран не он: " + choice + " из " + options
                        + " при пороге " + reserve);
        ToolOption chosen = options.stream().filter(option -> option.slot() == choice.slot()).findFirst()
                .orElseThrow();
        assertFalse(chosen.spent(reserve),
                "Выбранный слот " + choice.slot() + " обязан быть неизношенным, а он ниже порога " + reserve
                        + ": " + options);
    }

    @Property
    void best_ChosenCorrectTool_IsNeverSpent(
            @ForAll("durableOptions") List<ToolOption> options, @ForAll("reserves") ToolReserve reserve) {
        Choice choice = ToolChoice.best(options, reserve);
        if (choice.slot() == ToolChoice.HAND) {
            return;
        }
        ToolOption chosen = options.stream().filter(option -> option.slot() == choice.slot()).findFirst()
                .orElseThrow();
        assertFalse(chosen.spent(reserve),
                "Изношенный инструмент выбран как рабочий, хотя в наборе " + options + " при пороге " + reserve);
    }

    private static List<ToolOption> uniqueSlots(List<ToolOption> options) {
        List<ToolOption> reslotted = new ArrayList<>(options.size());
        for (int index = 0; index < options.size(); index++) {
            ToolOption option = options.get(index);
            reslotted.add(new ToolOption(index, option.speed(), option.correctForDrops(), option.remainingUses(),
                    option.maxUses(), option.enchanted()));
        }
        return List.copyOf(reslotted);
    }
}
