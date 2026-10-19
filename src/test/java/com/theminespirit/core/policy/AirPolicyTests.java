package com.theminespirit.core.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AirPolicyTests {
    static Stream<Arguments> table() {
        return Stream.of(
                Arguments.of("полный запас под водой", 300, true, false),
                Arguments.of("запас 101: ещё тихо", 101, true, false),
                Arguments.of("запас 100: граница тревоги", 100, true, true),
                Arguments.of("запас 99", 99, true, true),
                Arguments.of("запас 1", 1, true, true),
                Arguments.of("запас 0", 0, true, true),
                Arguments.of("запас ушёл в минус: тонем", -20, true, true),
                Arguments.of("запас 50, голова над водой", 50, false, false),
                Arguments.of("запас 0, голова над водой", 0, false, false),
                Arguments.of("запас 100, голова над водой", 100, false, false));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("table")
    void evaluate_Row_SurfacesExactlyWhenAirLowAndHeadUnderWater(
            String name, int air, boolean headUnderWater, boolean surfaces) {
        PolicyVerdict verdict = AirPolicy.evaluate(air, headUnderWater);

        if (surfaces) {
            PolicyVerdict.Reflex reflex = (PolicyVerdict.Reflex) verdict;
            assertEquals(
                    PolicyId.AIR, reflex.policy(), "Строка «" + name + "»: рефлекс воздуха принадлежит политике AIR");
            assertEquals(
                    new ReflexAction.Surface(),
                    reflex.action(),
                    "Строка «" + name + "»: при малом запасе воздуха дух всплывает");
        } else {
            assertEquals(
                    new PolicyVerdict.Clear(),
                    verdict,
                    "Строка «" + name + "»: запас воздуха достаточен или голова над водой, рефлекса быть не должно");
        }
    }

    @Test
    void alarm_Constant_IsOneHundredTicks() {
        assertEquals(
                100,
                AirPolicy.AIR_ALARM_TICKS,
                "Тревога при запасе воздуха 100 тиков (5 секунд): дух всплывает до того, как начнёт тонуть");
    }

    @Test
    void evaluate_LowAir_ReasonNamesTheSupply() {
        PolicyVerdict.Reflex reflex = (PolicyVerdict.Reflex) AirPolicy.evaluate(37, true);

        assertEquals(
                "запас воздуха 37 тиков, голова под водой",
                reflex.reason(),
                "Причина называет запас воздуха: по логу видно, насколько поздно дух всплыл");
    }
}
