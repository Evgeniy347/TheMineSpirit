package com.theminespirit.core.night;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.doors.DoorTuning.DoorRules;
import com.theminespirit.core.observation.BlockCoord;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

class NightLeashProperties {
    private static final int RADIUS = 32;

    private final NightLeash leash = new NightLeash(new DoorRules(0.5, 60, 2.0, RADIUS, 100));

    @Property
    void checkTarget_NightWithoutFollowing_AllowedTargetAlwaysWithinRadius(
            @ForAll @IntRange(min = -200, max = 200) int homeX,
            @ForAll @IntRange(min = -64, max = 320) int homeY,
            @ForAll @IntRange(min = -200, max = 200) int homeZ,
            @ForAll @IntRange(min = -200, max = 200) int targetX,
            @ForAll @IntRange(min = -64, max = 320) int targetY,
            @ForAll @IntRange(min = -200, max = 200) int targetZ,
            @ForAll @IntRange(min = 1, max = 128) int radius) {
        BlockCoord home = new BlockCoord(homeX, homeY, homeZ);
        BlockCoord target = new BlockCoord(targetX, targetY, targetZ);
        double dx = targetX - homeX;
        double dz = targetZ - homeZ;
        boolean within = Math.sqrt(dx * dx + dz * dz) <= radius;

        NightLeash.Decision decision = leash.checkTarget(target, home, radius, true, true, false);

        if (decision instanceof NightLeash.Allowed) {
            assertTrue(within,
                    "Ночью без следования за игроком разрешена точка за радиусом " + radius + ": дух ушёл бы "
                            + "за границу поселения вопреки решению владельца, цель " + target + ", дом " + home);
        } else {
            assertInstanceOf(NightLeash.Refused.class, decision,
                    "Нарушение обязано быть явным отказом, а не любым другим решением: " + decision);
            assertTrue(!within,
                    "Ночью внутри поселения цель " + target + " в радиусе " + radius + " от дома " + home
                            + " отвергнута: граница держала бы духа в собственном доме");
        }
    }

    @Property
    void checkTarget_NightFollowingPlayer_AlwaysAllowed(
            @ForAll @IntRange(min = -200, max = 200) int targetX,
            @ForAll @IntRange(min = 1, max = 128) int radius) {
        NightLeash.Decision decision =
                leash.checkTarget(new BlockCoord(targetX, 64, 0), new BlockCoord(0, 64, 0), radius, true, true, true);

        assertInstanceOf(NightLeash.Allowed.class, decision,
                "Следование за игроком обязано быть разрешено всегда: иначе дух бросит игрока в темноте за границей");
    }

    @Property
    void checkTarget_Daytime_AlwaysAllowed(
            @ForAll @IntRange(min = -200, max = 200) int targetX,
            @ForAll @IntRange(min = 1, max = 128) int radius) {
        NightLeash.Decision decision =
                leash.checkTarget(new BlockCoord(targetX, 64, 0), new BlockCoord(0, 64, 0), radius, false, true, false);

        assertInstanceOf(NightLeash.Allowed.class, decision,
                "Днём граница поселения не держит ни одну цель: иначе дневная работа за его пределами встала бы");
    }

    @Property
    void isEvening_AtOrAboveThreshold_IsEvening(@ForAll @IntRange(min = 0, max = 100) int percent) {
        double fraction = 0.5 + percent / 200.0;

        assertTrue(leash.isEvening(fraction),
                "Доля суток " + fraction + " на пороге вечера или выше обязана считаться вечером");
    }

    @Property
    void isEvening_BelowThreshold_IsDay(@ForAll @IntRange(min = 0, max = 49) int percent) {
        double fraction = percent / 100.0;

        assertEquals(fraction >= 0.5, leash.isEvening(fraction),
                "Порог вечера обязан совпадать с doors.json → eveningDayFraction = 0.5: доля " + fraction);
    }
}
