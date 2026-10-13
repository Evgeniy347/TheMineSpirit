package com.theminespirit.core.intellect.defense;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.theminespirit.core.tactics.EnemyFact;
import com.theminespirit.core.tactics.Point;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TargetHandoffTests {
    private static final UUID SELF = UUID.fromString("00000000-0000-0000-0000-000000000000");
    private static final EnemyFact ZOMBIE = new EnemyFact("minecraft:zombie",
            "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", new Point(10, 64, 0), 1.0, 0, true, true);
    private static final EnemyFact CREEPER = new EnemyFact("minecraft:creeper",
            "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb", new Point(10, 64, 0), 1.0, 0, true, true);

    private static DefenderCapability ally(String id, double x, double hp, boolean bow) {
        return new DefenderCapability(UUID.fromString(id), new Point(x, 64, 0), hp, 10, bow, 6.0, true);
    }

    @Test
    void handOff_TwoCapable_NearestChosen() {
        List<DefenderCapability> allies = List.of(ally("11111111-1111-1111-1111-111111111111", 4, 1.0, false),
                ally("22222222-2222-2222-2222-222222222222", 8, 1.0, false));

        assertEquals(Optional.of(UUID.fromString("22222222-2222-2222-2222-222222222222")),
                TargetHandoff.handOff(SELF, ZOMBIE, allies, Set.of(), DefenseProfileTests.PROFILE),
                "Цель уходит ближайшему к ней способному: иначе подмена идёт дольше через полкарты");
    }

    @Test
    void handOff_NearestBusy_FartherChosen() {
        List<DefenderCapability> allies = List.of(ally("11111111-1111-1111-1111-111111111111", 8, 1.0, false),
                ally("22222222-2222-2222-2222-222222222222", 4, 1.0, false));

        assertEquals(Optional.of(UUID.fromString("22222222-2222-2222-2222-222222222222")),
                TargetHandoff.handOff(SELF, ZOMBIE, allies,
                        Set.of(UUID.fromString("11111111-1111-1111-1111-111111111111")),
                        DefenseProfileTests.PROFILE),
                "Занятый союзник пропускается: иначе цель уйдёт духу, который уже ведёт свой бой");
    }

    @Test
    void handOff_NearestWounded_FartherChosen() {
        List<DefenderCapability> allies = List.of(ally("11111111-1111-1111-1111-111111111111", 8, 0.2, false),
                ally("22222222-2222-2222-2222-222222222222", 4, 1.0, false));

        assertEquals(Optional.of(UUID.fromString("22222222-2222-2222-2222-222222222222")),
                TargetHandoff.handOff(SELF, ZOMBIE, allies, Set.of(), DefenseProfileTests.PROFILE),
                "Раненый союзник пропускается: иначе цель уйдёт духу, который сам вот-вот отойдёт");
    }

    @Test
    void handOff_Creeper_NearerMeleeSkipped() {
        List<DefenderCapability> allies = List.of(ally("11111111-1111-1111-1111-111111111111", 9, 1.0, false),
                ally("22222222-2222-2222-2222-222222222222", 2, 1.0, true));

        assertEquals(Optional.of(UUID.fromString("22222222-2222-2222-2222-222222222222")),
                TargetHandoff.handOff(SELF, CREEPER, allies, Set.of(), DefenseProfileTests.PROFILE),
                "Крипер уходит лучнику, а не ближнему мечнику: иначе подмена подойдёт к взрыву");
    }

    @Test
    void handOff_CreeperWithoutArcher_ReturnsEmpty() {
        List<DefenderCapability> allies =
                List.of(ally("11111111-1111-1111-1111-111111111111", 9, 1.0, false));

        assertEquals(Optional.empty(),
                TargetHandoff.handOff(SELF, CREEPER, allies, Set.of(), DefenseProfileTests.PROFILE),
                "Без лучника крипера передать некому: дух отходит и лечится сам, а не бросает цель другому мечнику");
    }

    @Test
    void handOff_SelfInAllies_NeverChosen() {
        DefenderCapability selfAlly =
                new DefenderCapability(SELF, new Point(9, 64, 0), 1.0, 10, false, 6.0, true);
        List<DefenderCapability> allies =
                List.of(selfAlly, ally("22222222-2222-2222-2222-222222222222", 4, 1.0, false));

        assertEquals(Optional.of(UUID.fromString("22222222-2222-2222-2222-222222222222")),
                TargetHandoff.handOff(SELF, ZOMBIE, allies, Set.of(), DefenseProfileTests.PROFILE),
                "Уходящий не принимает свою же цель: иначе передача замкнётся на раненого");
    }

    @Test
    void handOff_EqualDistance_LowerIdWins() {
        List<DefenderCapability> allies = List.of(
                new DefenderCapability(UUID.fromString("22222222-2222-2222-2222-222222222222"), new Point(10, 64, 5),
                        1.0, 10, false, 6.0, true),
                new DefenderCapability(UUID.fromString("11111111-1111-1111-1111-111111111111"), new Point(10, 64, -5),
                        1.0, 10, false, 6.0, true));

        assertEquals(Optional.of(UUID.fromString("11111111-1111-1111-1111-111111111111")),
                TargetHandoff.handOff(SELF, ZOMBIE, allies, Set.of(), DefenseProfileTests.PROFILE),
                "Ничья по дистанции решается меньшим id: иначе подмена мигает между пересборками");
    }

    @Test
    void handOff_NoAllies_ReturnsEmpty() {
        assertEquals(Optional.empty(),
                TargetHandoff.handOff(SELF, ZOMBIE, List.of(), Set.of(), DefenseProfileTests.PROFILE),
                "Без союзников передавать некому: дух отходит и лечится сам");
    }

    @Test
    void handOff_NullTarget_Throws() {
        assertThrows(IllegalArgumentException.class,
                () -> TargetHandoff.handOff(SELF, null,
                        List.of(ally("11111111-1111-1111-1111-111111111111", 4, 1.0, false)), Set.of(),
                        DefenseProfileTests.PROFILE),
                "Передача без цели обязана падать: иначе приказ уйдёт без объекта боя");
    }
}
