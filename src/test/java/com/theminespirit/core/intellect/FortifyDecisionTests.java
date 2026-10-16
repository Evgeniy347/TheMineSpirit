package com.theminespirit.core.intellect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.coordinator.SpiritRole;
import com.theminespirit.core.coordinator.SpiritSnapshot;
import com.theminespirit.core.tactics.CombatMode;
import com.theminespirit.core.tactics.EnemyFact;
import com.theminespirit.core.tactics.Point;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FortifyDecisionTests {
    static SpiritSnapshot snapshot(UUID id, double x, SpiritRole role) {
        return new SpiritSnapshot(id, new Point(x, 64, 0), 1.0, role, CombatMode.DEFENSIVE, 1, false,
                Optional.empty(), 0, 1.0);
    }

    static EnemyFact enemy(String id, double x) {
        return new EnemyFact("minecraft:zombie", id, new Point(x, 64, 0), 1.0, 0, true, true);
    }

    static List<EnemyFact> horde() {
        List<EnemyFact> enemies = new ArrayList<>();
        for (int index = 0; index < FortifyTuning.FORTIFY_ENEMY_MIN; index++) {
            enemies.add(enemy(UUID.randomUUID().toString(), 10 + index));
        }
        return enemies;
    }

    static SettlementView camp(UUID... ids) {
        List<SpiritSnapshot> spirits = new ArrayList<>();
        for (int index = 0; index < ids.length; index++) {
            spirits.add(snapshot(ids[index], index * 2.0, SpiritRole.WORKER));
        }
        spirits.sort(Comparator.comparing(SpiritSnapshot::id));
        return SettlementViews.withSpirits(spirits);
    }

    @Test
    void decide_ManyEnemiesPassageHardRetreat_Barricade() {
        SettlementView view = camp(UUID.randomUUID(), UUID.randomUUID());

        assertEquals(FortifyMode.BARRICADE, FortifyDecision.decide(view, horde(), true, true),
                "Много врагов, проход рядом, отход затруднён — замуроваться: шов дешевле укрытия и держит проход");
    }

    @Test
    void decide_ManyEnemiesNoPassageHardRetreat_Shelter() {
        SettlementView view = camp(UUID.randomUUID(), UUID.randomUUID());

        assertEquals(FortifyMode.SHELTER, FortifyDecision.decide(view, horde(), false, true),
                "Много врагов без прохода — строить укрытие 4×4: иначе духам негде держать оборону");
    }

    @Test
    void decide_FewEnemies_None() {
        SettlementView view = camp(UUID.randomUUID(), UUID.randomUUID());

        assertEquals(FortifyMode.NONE, FortifyDecision.decide(view, List.of(enemy(UUID.randomUUID().toString(), 10)),
                true, true),
                "Мало врагов — без укрепления: иначе стройка поднимается на каждую мелочь");
    }

    @Test
    void decide_EasyRetreat_None() {
        SettlementView view = camp(UUID.randomUUID(), UUID.randomUUID());

        assertEquals(FortifyMode.NONE, FortifyDecision.decide(view, horde(), true, false),
                "Лёгкий отход — без укрепления: проще уйти, чем тратить блоки и время");
    }

    @Test
    void decide_NoSpirits_None() {
        assertEquals(FortifyMode.NONE, FortifyDecision.decide(SettlementViews.empty(), horde(), true, true),
                "Без духов строить некому: решение обязано быть NONE, а не приказом в пустоту");
    }

    @Test
    void decide_NullInput_Throws() {
        SettlementView view = camp(UUID.randomUUID());

        assertThrows(NullPointerException.class, () -> FortifyDecision.decide(null, horde(), true, true),
                "Решение без снимка обязано отвергаться: без духов строить некому");
        assertThrows(NullPointerException.class, () -> FortifyDecision.decide(view, null, true, true),
                "Решение без врагов обязано отвергаться: null вместо пустого списка скрывает баги");
    }

    @Test
    void roster_ShelterMode_OneBuilderRestGuards() {
        UUID near = UUID.randomUUID();
        UUID far = UUID.randomUUID();
        UUID front = UUID.randomUUID();
        List<SpiritSnapshot> spirits = List.of(snapshot(far, 100, SpiritRole.WORKER),
                snapshot(near, 9, SpiritRole.WORKER), snapshot(front, 8, SpiritRole.DEFENDER));

        Optional<FortifyRoster> roster = FortifyDecision.roster(FortifyMode.SHELTER, spirits, horde());

        assertTrue(roster.isPresent(),
                "При подходящих духах ростер обязан собираться: иначе решение SHELTER висит без исполнителя");
        assertEquals(near, roster.get().builder(),
                "Строитель — ближайший к угрозе не фронтовик: фронтовик держит бой, дальний долго идёт");
        assertEquals(2, roster.get().guards().size(),
                "Остальные духи обязаны прикрывать: иначе строитель остаётся без защиты");
        assertTrue(!roster.get().guards().contains(roster.get().builder()),
                "Строитель обязан не входить в защитники: один дух не строит и не прикрывает сразу");
        assertEquals(FortifyMode.SHELTER, roster.get().mode(),
                "Режим ростера обязан совпадать с решением: иначе строитель ставит не то, что приказано");
    }

    @Test
    void roster_NoneMode_GivesEmpty() {
        List<SpiritSnapshot> spirits = List.of(snapshot(UUID.randomUUID(), 0, SpiritRole.WORKER));

        assertEquals(Optional.empty(), FortifyDecision.roster(FortifyMode.NONE, spirits, horde()),
                "Режим NONE обязан давать пустой ростер: иначе духи строятся без решения");
    }

    @Test
    void roster_NoCandidates_GivesEmpty() {
        List<SpiritSnapshot> spirits = List.of(snapshot(UUID.randomUUID(), 0, SpiritRole.DEFENDER),
                snapshot(UUID.randomUUID(), 5, SpiritRole.DEFENDER));

        assertEquals(Optional.empty(), FortifyDecision.roster(FortifyMode.SHELTER, spirits, horde()),
                "Без не фронтовиков строить некому: ростер пуст, а не назначает бойца бросать фронт");
    }

    @Test
    void roster_NullInput_Throws() {
        List<SpiritSnapshot> spirits = List.of(snapshot(UUID.randomUUID(), 0, SpiritRole.WORKER));

        assertThrows(NullPointerException.class, () -> FortifyDecision.roster(null, spirits, horde()),
                "Ростер без режима обязан отвергаться: режим входит в приказ");
        assertThrows(NullPointerException.class, () -> FortifyDecision.roster(FortifyMode.SHELTER, null, horde()),
                "Ростер без духов обязан отвергаться: строить и крыть некому");
    }
}
