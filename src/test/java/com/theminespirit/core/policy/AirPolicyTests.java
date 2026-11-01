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
                Arguments.of("запас 201: ещё тихо", 201, true, false),
                Arguments.of("запас 200: граница тревоги", 200, true, true),
                Arguments.of("запас 199", 199, true, true),
                Arguments.of("запас 1", 1, true, true),
                Arguments.of("запас 0", 0, true, true),
                Arguments.of("запас ушёл в минус: тонем", -20, true, true),
                Arguments.of("запас 50, голова над водой", 50, false, false),
                Arguments.of("запас 0, голова над водой", 0, false, false),
                Arguments.of("запас 200, голова над водой", 200, false, false));
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
    void alarm_Constant_IsTwoHundredTicks() {
        assertEquals(
                200,
                AirPolicy.AIR_ALARM_TICKS,
                "Тревога при запасе воздуха 200 тиков (10 секунд): 5 секунд не хватило, чтобы обойти уступ земли "
                        + "и подняться с глубины 7 в затопленной шахте observe16 N5 — дух утонул с воздухом -12");
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
