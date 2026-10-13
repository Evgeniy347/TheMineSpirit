package com.theminespirit.core.social;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SocialTuningTests {
    @Test
    void socialTuning_Arrivals_MatchAgreedValues() {
        assertEquals(1.5, SocialTuning.MEET_ARRIVAL,
                "meet заканчивается в 1.5 блока от цели — как возврат духа TacticsService.RETURN_ARRIVAL: "
                        + "ближе — дух толкается в цель, дальше — отдалится и не закроет задачу");
        assertEquals(2.5, SocialTuning.DELIVER_ARRIVAL,
                "deliver завершается в 2.5 блока от адресата: груз передают с дистанции вытянутой руки, "
                        + "ближе — дух упирается в игрока, дальше — предмет не долетит");
    }

    @Test
    void socialTuning_Arrivals_ArePositiveAndFinite() {
        assertTrue(SocialTuning.MEET_ARRIVAL > 0 && Double.isFinite(SocialTuning.MEET_ARRIVAL),
                "Прибытие meet обязано быть положительным и конечным: ноль завершил бы задачу, не сойдясь "
                        + "с целью, а бесконечность повесила бы её навечно. Фактическое: " + SocialTuning.MEET_ARRIVAL);
        assertTrue(SocialTuning.DELIVER_ARRIVAL > 0 && Double.isFinite(SocialTuning.DELIVER_ARRIVAL),
                "Прибытие deliver обязано быть положительным и конечным: ноль или бесконечность сломали бы "
                        + "передачу. Фактическое: " + SocialTuning.DELIVER_ARRIVAL);
    }

    @Test
    void socialTuning_Arrivals_HaveNoZeros() {
        assertNotEquals(0.0, SocialTuning.MEET_ARRIVAL,
                "Нулевое прибытие meet запрещено: задача завершилась бы, не сойдясь с целью");
        assertNotEquals(0.0, SocialTuning.DELIVER_ARRIVAL,
                "Нулевое прибытие deliver запрещено: груз передали бы, не подойдя к адресату");
    }
}
