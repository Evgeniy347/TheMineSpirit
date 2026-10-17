package com.theminespirit.core.intellect;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.tactics.Point;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;

class OutsideGateTests {
    private static final Point HOME = new Point(0, 64, 0);
    private static final Point NEAR = new Point(3, 64, 0);
    private static final Point FAR = new Point(40, 64, 0);

    private static OutsideGate gate() {
        return new OutsideGate(OutsideRules.defaults(), 32);
    }

    @Test
    void admit_InsideTarget_LoneSpiritAllowed() {
        OutsideGate.Decision decision = gate().admit(NEAR, HOME, 1, false);

        assertInstanceOf(OutsideGate.Allowed.class, decision,
                "Внутри поселения правило выхода не держит: граница — только про внешние работы");
    }

    @Test
    void admit_OutsideTargetLoneSpirit_RefusedWithReason() {
        OutsideGate.Decision decision = gate().admit(FAR, HOME, 1, false);

        assertInstanceOf(OutsideGate.Refused.class, decision,
                "Один дух за границу поселения не выходит: шахта и поход — только организованно");
        assertTrue(((OutsideGate.Refused) decision).reason().contains("в одиночку"),
                "Причина отказа обязана говорить про одиночку, фактически: "
                        + ((OutsideGate.Refused) decision).reason());
    }

    @Test
    void admit_OutsideTargetTwoSpirits_Allowed() {
        assertInstanceOf(OutsideGate.Allowed.class, gate().admit(FAR, HOME, 2, false),
                "Двое духов за границу выходят: группа от порога — уже организация");
    }

    @Test
    void admit_OutsideTargetLoneSpiritWithPlayer_Allowed() {
        assertInstanceOf(OutsideGate.Allowed.class, gate().admit(FAR, HOME, 1, true),
                "С реальным игроком рядом один дух выходит: игрок — вторая пара глаз и рук");
    }

    @Test
    void admitExternal_LoneSpirit_Refused() {
        assertInstanceOf(OutsideGate.Refused.class, gate().admitExternal(1, false),
                "Внешняя работа в одиночку не назначается, даже когда цель ещё не известна");
    }

    @Test
    void admit_BoundaryDistance_InsideIsAllowed() {
        Point onRadius = new Point(32, 64, 0);

        assertInstanceOf(OutsideGate.Allowed.class, gate().admit(onRadius, HOME, 1, false),
                "Точка ровно на радиусе — ещё внутри поселения: граница не режится по строгому «больше»");
    }

    @Test
    void outside_NullHome_Throws() {
        assertThrows(NullPointerException.class, () -> gate().outside(FAR, null),
                "Без дома граница не считается: null — ошибка, а не «всё разрешено»");
    }

    @Property(tries = 100)
    void admitExternal_AnyPartyAtThreshold_Allowed(@ForAll("parties") int party) {
        assertInstanceOf(OutsideGate.Allowed.class, gate().admitExternal(Math.max(party, 2), false),
                "Любая группа от порога допускается наружу независимо от случайного состава");
    }

    @Provide
    Arbitrary<Integer> parties() {
        return Arbitraries.integers().between(2, 10);
    }
}
