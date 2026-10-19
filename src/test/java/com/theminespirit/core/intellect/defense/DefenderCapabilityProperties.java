package com.theminespirit.core.intellect.defense;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.tactics.Point;
import java.util.UUID;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Assume;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class DefenderCapabilityProperties {
    @Provide
    Arbitrary<DefenderCapability> capabilities() {
        return Combinators.combine(
                        Arbitraries.create(UUID::randomUUID),
                        Arbitraries.integers().between(-16, 16).map(x -> new Point(x, 64, -x)),
                        Arbitraries.doubles().between(0.0, 1.0),
                        Arbitraries.integers().between(0, 20),
                        Arbitraries.of(true, false),
                        Arbitraries.doubles().between(0.0, 12.0),
                        Arbitraries.of(true, false))
                .as((id, pos, hp, armor, bow, dps, heal) -> new DefenderCapability(id, pos, hp, armor, bow, dps, heal));
    }

    @Property
    void frontRank_BetterArmorAndHp_NeverLower(
            @ForAll("capabilities") DefenderCapability first, @ForAll("capabilities") DefenderCapability second) {
        Assume.that(first.armor() >= second.armor() && first.hpFraction() >= second.hpFraction());

        assertTrue(
                first.frontRank() >= second.frontRank(),
                "Фронтовость монотонна по броне и здоровью: иначе распределение выберет слабого первым на крипера");
    }

    @Property
    void frontRank_AnyInput_WithinUnitRange(@ForAll("capabilities") DefenderCapability capability) {
        assertTrue(
                capability.frontRank() >= 0.0 && capability.frontRank() <= 1.0,
                "Фронтовость — нормализованная шкала 0..1: иначе сравнение защитников между собой бессмысленно");
    }

    @Property
    void role_AnyInput_Deterministic(@ForAll("capabilities") DefenderCapability capability) {
        DefenderRole before = capability.role();
        capability.frontRank();

        assertEquals(
                before,
                capability.role(),
                "Роль зависит только от флага лука: иначе приказы лучнику и мечнику перемешаются между тиками");
    }
}
