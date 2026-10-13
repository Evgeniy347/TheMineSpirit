package com.theminespirit.core.intellect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

class InvitationProperties {
    @Property
    void tick_IncreasingTime_RepeatsMonotone(@ForAll @IntRange(min = 0, max = 10000) int start,
            @ForAll List<@IntRange(min = 0, max = 2000) Integer> steps) {
        Invitation invitation = new Invitation();
        invitation.open(start);
        long now = start;
        int prev = 0;
        List<Long> moments = new ArrayList<>();
        for (int step : steps) {
            now += step;
            moments.add(now);
        }

        for (long moment : moments) {
            int repeats = invitation.tick(moment).repeats();
            assertTrue(repeats >= prev,
                    "Счётчик повторов монотонен: иначе игрок увидит повторный вопрос раньше первого");
            prev = repeats;
        }
    }

    @Property
    void tick_BeforeTtl_NoEarlyClose(@ForAll @IntRange(min = 0, max = 10000) int start,
            @ForAll @IntRange(min = 0, max = 3599) int elapsed) {
        Invitation invitation = new Invitation();
        invitation.open(start);

        InvitationState state = invitation.tick((long) start + elapsed);

        assertEquals(InvitationStage.OPEN, state.stage(),
                "Без ответа приглашение не закрывается раньше 3600 тиков: иначе вопрос умрёт до срока");
    }
}
