package com.theminespirit.core.intellect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.coordinator.SpiritRole;
import com.theminespirit.core.coordinator.SpiritSnapshot;
import com.theminespirit.core.tactics.ArmorModel;
import com.theminespirit.core.tactics.CombatMode;
import com.theminespirit.core.tactics.EnemyFact;
import com.theminespirit.core.tactics.EnemyProfile;
import com.theminespirit.core.tactics.EnemyProfiles;
import com.theminespirit.core.tactics.Point;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StrengthAssessorTests {
    static final UUID FIRST = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID SECOND = UUID.fromString("22222222-2222-2222-2222-222222222222");
    static final UUID THIRD = UUID.fromString("33333333-3333-3333-3333-333333333333");
    static final EnemyProfiles PROFILES = new EnemyProfiles(
            Map.of("minecraft:zombie", new EnemyProfile("minecraft:zombie", 20, 3, 0.23, false, false, false, false, 1),
                    "minecraft:ravager",
                    new EnemyProfile("minecraft:ravager", 100, 8, 0.3, false, false, false, false, 4)),
            new EnemyProfile("default", 20, 4, 1, false, false, false, false, 2));
    static final ArmorModel ARMOR = new ArmorModel(0.04, 0.8);

    static SpiritSnapshot defender(UUID id, double hp, int threats) {
        return new SpiritSnapshot(id, new Point(0, 64, 0), hp, SpiritRole.DEFENDER, CombatMode.DEFENSIVE, threats,
                false, Optional.empty(), 0, 1.0);
    }

    static EnemyFact zombie(UUID id, double x) {
        return new EnemyFact("minecraft:zombie", id.toString(), new Point(x, 64, 0), 1, 0, true, true);
    }

    @Test
    void assess_WeakZombieAndThreeDefenders_OneFightRestWork() {
        UUID enemy = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        List<SpiritSnapshot> spirits =
                List.of(defender(FIRST, 1, 1), defender(SECOND, 1, 0), defender(THIRD, 1, 0));

        List<StrengthVerdict> verdicts =
                StrengthAssessor.assess(spirits, List.of(zombie(enemy, 5)), PROFILES, ARMOR, false);

        long fights = verdicts.stream().filter(v -> v.action() == StrengthAction.FIGHT).count();
        assertTrue(fights >= 1 && fights <= 2,
                "Против одного слабого зомби в бой идут один-два духа: иначе либо никто не защищается, либо "
                        + "все бросают работу");
        assertTrue(verdicts.stream().noneMatch(v -> v.spirit().equals(SECOND) || v.spirit().equals(THIRD)),
                "Спокойные духи без вердикта продолжают работу: иначе поселение встанет при каждой угрозе");
        assertEquals(Optional.of(enemy), verdicts.get(0).enemy(),
                "Вердикт указывает на врага из входа: иначе приказ уйдёт не на ту цель");
    }

    @Test
    void assess_ThreatenedBeyondTarget_Flees() {
        UUID enemy = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        List<SpiritSnapshot> spirits =
                List.of(defender(FIRST, 1, 1), defender(SECOND, 1, 1), defender(THIRD, 1, 1));

        List<StrengthVerdict> verdicts =
                StrengthAssessor.assess(spirits, List.of(zombie(enemy, 5)), PROFILES, ARMOR, false);

        assertEquals(1, verdicts.stream().filter(v -> v.action() == StrengthAction.FIGHT).count(),
                "Число бойцов ограничено целью: против одного слабого — один, остальные угрожаемые отходят");
        assertEquals(2, verdicts.stream().filter(v -> v.action() == StrengthAction.FLEE).count(),
                "Угрожаемые сверх цели получают FLEE: иначе толпа бьёт одного зомби вместо работы");
    }

    @Test
    void assess_WoundedDefender_Heals() {
        UUID enemy = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

        List<StrengthVerdict> verdicts = StrengthAssessor.assess(List.of(defender(FIRST, 0.2, 1)),
                List.of(zombie(enemy, 5)), PROFILES, ARMOR, false);

        assertEquals(StrengthAction.HEAL, verdicts.get(0).action(),
                "Раненый дух лечится, а не дерётся: иначе поселение теряет духа вместо его спасения");
    }

    @Test
    void assess_UnarmedWorkerWithStock_Arms() {
        UUID enemy = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        SpiritSnapshot worker = new SpiritSnapshot(FIRST, new Point(0, 64, 0), 1, SpiritRole.WORKER,
                CombatMode.DEFENSIVE, 1, false, Optional.empty(), 0, 1.0);

        List<StrengthVerdict> verdicts =
                StrengthAssessor.assess(List.of(worker), List.of(zombie(enemy, 5)), PROFILES, ARMOR, true);

        assertEquals(StrengthAction.ARM, verdicts.get(0).action(),
                "Безоружный при запасе оружия идёт за ним: голой рукой зомби не бьют");
    }

    @Test
    void assess_StrongGroup_MoreFights() {
        List<SpiritSnapshot> spirits =
                List.of(defender(FIRST, 1, 1), defender(SECOND, 1, 1), defender(THIRD, 1, 1));
        List<EnemyFact> pack = List.of(zombie(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"), 5),
                zombie(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"), 6),
                zombie(UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"), 7));

        List<StrengthVerdict> verdicts = StrengthAssessor.assess(spirits, pack, PROFILES, ARMOR, false);

        assertEquals(2, verdicts.stream().filter(v -> v.action() == StrengthAction.FIGHT).count(),
                "Против тройки зомби бойцов двое: число бойцов соответствует силе врага");
    }

    @Test
    void assess_RavagerAlone_Flees() {
        EnemyFact ravager = new EnemyFact("minecraft:ravager", "ravager-1", new Point(5, 64, 0), 1, 0, true, true);

        List<StrengthVerdict> verdicts =
                StrengthAssessor.assess(List.of(defender(FIRST, 1, 1)), List.of(ravager), PROFILES, ARMOR, false);

        assertEquals(StrengthAction.FLEE, verdicts.get(0).action(),
                "Проигрышный запас победы — отход: иначе дух гибнет в безнадёжном бою");
        assertTrue(verdicts.get(0).winMargin() < 0,
                "Запас против опустошителя отрицателен: вердикт несёт измеренный проигрыш, а не ярлык");
    }

    @Test
    void assess_NoEnemies_Empty() {
        assertEquals(List.of(), StrengthAssessor.assess(List.of(defender(FIRST, 1, 0)), List.of(), PROFILES, ARMOR,
                false), "Без угроз вердиктов нет: иначе мирные духи получат боевые приказы");
    }

    @Test
    void assess_NullInputs_Throw() {
        assertThrows(IllegalArgumentException.class,
                () -> StrengthAssessor.assess(null, List.of(), PROFILES, ARMOR, false),
                "Оценка без духов не считается: бой ведут конкретные духи");
        assertThrows(IllegalArgumentException.class,
                () -> StrengthAssessor.assess(List.of(), null, PROFILES, ARMOR, false),
                "Оценка без врагов не считается: пустой список угроз задают пустым списком");
    }

    @Test
    void fighterTarget_SingleWeak1_ThreeZombies2() {
        assertEquals(1,
                StrengthAssessor.fighterTarget(
                        List.of(zombie(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"), 5)), PROFILES),
                "Против одного слабого цель — один боец: остальные работают");
        assertEquals(2,
                StrengthAssessor.fighterTarget(
                        List.of(zombie(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"), 5),
                                zombie(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"), 6),
                                zombie(UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"), 7)),
                        PROFILES),
                "Против тройки цель — двое: число бойцов растёт с силой врага, но не более предела");
    }
}
