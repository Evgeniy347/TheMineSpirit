package com.theminespirit.core.intellect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;

class OutsideGateTests {
    private static OutsideGate gate() {
        return new OutsideGate(OutsideRules.defaults(), 32);
    }

    @Test
    void admitExternal_LoneSpirit_Refused() {
        assertInstanceOf(OutsideGate.Refused.class, gate().admitExternal(1, false),
                "Внешняя работа в одиночку не назначается, даже когда цель ещё не известна");
    }

    @Test
    void admitExternal_EveningLoneSpirit_RefusedWithNightReason() {
        OutsideGate.Decision decision = gate().admitExternal(1, false, true);

        OutsideGate.Refused refused = assertInstanceOf(OutsideGate.Refused.class, decision,
                "Вечером и ночью внешняя работа не назначается: иначе дух уйдёт за границу навстречу мобам");
        assertEquals(OutsideGate.NIGHT_REASON, refused.reason(),
                "Причина отказа обязана называть ночь: по ней строится журнал решений поселения");
    }

    @Test
    void admitExternal_EveningPartyWithPlayer_StillRefused() {
        assertInstanceOf(OutsideGate.Refused.class, gate().admitExternal(4, true, true),
                "Ночью внешняя работа не назначается даже группой или при игроке: единственное исключение — "
                        + "следование за игроком, а не работа рядом с ним");
    }

    @Property(tries = 50)
    void admitExternal_EveningParty_IsNeverAllowed(@ForAll("parties") int party, @ForAll boolean player) {
        assertInstanceOf(OutsideGate.Refused.class, gate().admitExternal(party, player, true),
                "Вечером внешняя работа отвергается при любом составе и любом сопровождении: ночь — жёсткая граница");
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
