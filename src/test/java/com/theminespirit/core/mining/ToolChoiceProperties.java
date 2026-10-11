package com.theminespirit.core.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
                Arbitraries.of(true, false)).as(ToolOption::new);
        return option.list().ofMaxSize(12);
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
}
