package com.theminespirit.core.coordinator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.tactics.CombatMode;
import com.theminespirit.core.tactics.Point;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class AssistPolicyProperties {
    @Provide
    Arbitrary<List<SpiritSnapshot>> spirits() {
        return spirit().list().ofMinSize(1).ofMaxSize(6);
    }

    @Provide
    Arbitrary<Integer> maxHelpers() {
        return Arbitraries.integers().between(0, 4);
    }

    @Property
    void assist_AnySpiritsAndMaxHelpers_HelpersWithinLimits(@ForAll("spirits") List<SpiritSnapshot> spirits,
            @ForAll("maxHelpers") int maxHelpers) {
        List<DefenseAssignment> posts = spirits.stream()
                .filter(spirit -> spirit.role() == SpiritRole.DEFENDER)
                .map(spirit -> new DefenseAssignment(spirit.id(),
                        new Point(spirit.pos().x() + 1, spirit.pos().y(), spirit.pos().z()), 0))
                .toList();
        UUID attacked = spirits.get(0).id();

        List<UUID> helpers = new AssistPolicy().assist(attacked, spirits, posts, maxHelpers);

        assertTrue(helpers.size() <= maxHelpers,
                "Помощников не больше maxHelpers: иначе сектора пустеют при любом пределе");
        for (UUID helper : helpers) {
            assertTrue(!helper.equals(attacked), "Атакованный не помогает себе: он держит свой сектор или отбивается");
            SpiritSnapshot snapshot = spirits.stream()
                    .filter(spirit -> spirit.id().equals(helper))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Помощник " + helper + " отсутствует в снимке"));
            assertEquals(SpiritRole.DEFENDER, snapshot.role(),
                    "Помощник — защитник: работник бросит работу, которую координатор ему не давал");
            assertTrue(posts.stream().anyMatch(post -> post.defender().equals(helper)),
                    "Помощник держит пост в обороне: иначе помощь приходит от тех, кого нет в секторах");
        }
    }

    private Arbitrary<SpiritSnapshot> spirit() {
        return Arbitraries.integers().between(0, 3).flatMap(threats -> Arbitraries.of(true, false)
                .filter(attacked -> !attacked || threats > 0)
                .map(attacked -> new SpiritSnapshot(UUID.randomUUID(), new Point(threats, 64, threats),
                        threats / 4.0, SpiritRole.DEFENDER, CombatMode.GUARD_AREA, threats, attacked,
                        Optional.empty(), threats, 1.0)));
    }
}
