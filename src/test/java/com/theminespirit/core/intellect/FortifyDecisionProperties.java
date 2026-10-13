package com.theminespirit.core.intellect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.coordinator.SpiritRole;
import com.theminespirit.core.coordinator.SpiritSnapshot;
import com.theminespirit.core.tactics.EnemyFact;
import com.theminespirit.core.tactics.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class FortifyDecisionProperties {
    @Provide
    Arbitrary<List<SpiritSnapshot>> spirits() {
        return Combinators.combine(Arbitraries.create(UUID::randomUUID),
                Arbitraries.integers().between(-30, 30).map(x -> new Point(x, 64, 0)),
                Arbitraries.of(SpiritRole.values()))
                .as((id, pos, role) -> FortifyDecisionTests.snapshot(id, pos.x(), role)).list().ofMinSize(1)
                .ofMaxSize(5).map(list -> list.stream()
                        .collect(Collectors.toMap(SpiritSnapshot::id, item -> item, (first, second) -> first,
                                LinkedHashMap::new))
                        .values().stream().sorted(Comparator.comparing(SpiritSnapshot::id)).toList());
    }

    @Provide
    Arbitrary<List<EnemyFact>> hordes() {
        return Combinators.combine(Arbitraries.create(UUID::randomUUID).map(UUID::toString),
                Arbitraries.integers().between(-30, 30))
                .as((id, x) -> FortifyDecisionTests.enemy(id, x)).list()
                .ofMinSize(FortifyTuning.FORTIFY_ENEMY_MIN).ofMaxSize(8);
    }

    @Provide
    Arbitrary<List<EnemyFact>> smallPacks() {
        return Combinators.combine(Arbitraries.create(UUID::randomUUID).map(UUID::toString),
                Arbitraries.integers().between(-30, 30))
                .as((id, x) -> FortifyDecisionTests.enemy(id, x)).list()
                .ofMinSize(1).ofMaxSize(FortifyTuning.FORTIFY_ENEMY_MIN - 1);
    }

    @Property
    void roster_AnyMode_BuilderNotAmongGuards(@ForAll("spirits") List<SpiritSnapshot> spirits,
            @ForAll("hordes") List<EnemyFact> enemies, @ForAll("smallPacks") List<EnemyFact> pack,
            @ForAll boolean passage, @ForAll boolean difficult) {
        for (FortifyMode mode : FortifyMode.values()) {
            Optional<FortifyRoster> roster = FortifyDecision.roster(mode, spirits, enemies);
            if (roster.isEmpty()) {
                continue;
            }
            assertTrue(!roster.get().guards().contains(roster.get().builder()),
                    "Строитель обязан не входить в защитники: один дух не строит и не прикрывает сразу");
            Set<UUID> ids = new HashSet<>();
            for (SpiritSnapshot spirit : spirits) {
                ids.add(spirit.id());
            }
            assertTrue(ids.contains(roster.get().builder()),
                    "Строитель обязан быть из отряда: иначе приказ уйдёт несуществующему духу");
            assertTrue(ids.containsAll(roster.get().guards()),
                    "Защитники обязаны быть из отряда: иначе приказ уйдёт несуществующим духам");
            assertEquals(new HashSet<>(roster.get().guards()).size(), roster.get().guards().size(),
                    "Каждая цель прикрытия занята не больше чем одним духом: иначе два духа кроют одну точку");
            assertEquals(mode, roster.get().mode(),
                    "Режим ростера обязан совпадать с решением: иначе строитель ставит не то, что приказано");
        }
        assertEquals(FortifyMode.NONE, FortifyDecision.decide(SettlementViews.withSpirits(spirits), pack, passage,
                difficult),
                "Врагов меньше порога — укреплению нет: иначе стройка поднимается на каждую мелочь");
    }

    @Property
    void roster_SuitableSpirits_PresentExactlyWhenModeSet(@ForAll("spirits") List<SpiritSnapshot> spirits,
            @ForAll("hordes") List<EnemyFact> enemies) {
        boolean hasCandidate = spirits.stream().anyMatch(spirit -> spirit.role() != SpiritRole.DEFENDER);
        for (FortifyMode mode : FortifyMode.values()) {
            Optional<FortifyRoster> roster = FortifyDecision.roster(mode, spirits, enemies);
            assertEquals(mode != FortifyMode.NONE && hasCandidate, roster.isPresent(),
                    "Ростер есть ровно при режиме и кандидате: NONE всегда пуст, без кандидата строить некому");
        }
    }

    @Property
    void roster_ShuffledSpirits_SameRoster(@ForAll("spirits") List<SpiritSnapshot> spirits,
            @ForAll("hordes") List<EnemyFact> enemies) {
        List<SpiritSnapshot> reversed = new ArrayList<>(spirits);
        Collections.reverse(reversed);

        assertEquals(FortifyDecision.roster(FortifyMode.SHELTER, spirits, enemies),
                FortifyDecision.roster(FortifyMode.SHELTER, reversed, enemies),
                "Перемешанный вход даёт тот же ростер: иначе роли прыгают от порядка сбора снимка");
        assertEquals(
                FortifyDecision.decide(SettlementViews.withSpirits(spirits), enemies, true, true),
                FortifyDecision.decide(SettlementViews.withSpirits(reversed), enemies, true, true),
                "Тот же вход даёт то же решение: иначе приказ мигает между пересборками");
    }
}
