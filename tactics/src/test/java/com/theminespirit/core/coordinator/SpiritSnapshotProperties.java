package com.theminespirit.core.coordinator;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.tactics.CombatMode;
import com.theminespirit.core.tactics.Point;
import java.util.Optional;
import java.util.UUID;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.DoubleRange;

class SpiritSnapshotProperties {
    @Property
    void hpFraction_AnyFiniteValue_ClampedIntoUnitRange(@ForAll @DoubleRange(min = -1000000, max = 1000000) double hp) {
        SpiritSnapshot snapshot = new SpiritSnapshot(
                UUID.randomUUID(),
                new Point(0, 64, 0),
                hp,
                SpiritRole.IDLE,
                CombatMode.DEFENSIVE,
                0,
                false,
                Optional.empty(),
                1,
                1.0);

        assertTrue(
                snapshot.hpFraction() >= 0.0 && snapshot.hpFraction() <= 1.0,
                "Доля здоровья обязана остаться в [0;1] при любом входе: иначе оценка обороны получит духа "
                        + "здоровее живого или живее мёртвого");
    }

    @Property
    void attacked_TrueWithZeroThreats_Throws(
            @ForAll @DoubleRange(min = 0, max = 1) double hp, @ForAll SpiritRole role, @ForAll CombatMode mode) {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SpiritSnapshot(
                        UUID.randomUUID(), new Point(0, 64, 0), hp, role, mode, 0, true, Optional.empty(), 1, 1.0),
                "Атакованный без угроз противоречит снимку при любых остальных полях: координированная помощь "
                        + "уходила бы на ложную тревогу");
    }
}
