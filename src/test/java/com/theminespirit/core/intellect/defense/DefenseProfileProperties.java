package com.theminespirit.core.intellect.defense;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.tactics.EnemyProfile;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class DefenseProfileProperties {
    @Provide
    Arbitrary<String> types() {
        return Arbitraries.oneOf(
                Arbitraries.of(
                        "minecraft:creeper",
                        "minecraft:skeleton",
                        "minecraft:zombie",
                        "minecraft:phantom",
                        "minecraft:ravager"),
                Arbitraries.strings().withCharRange('a', 'z').ofLength(5).map(name -> "minecraft:" + name));
    }

    @Provide
    Arbitrary<EnemyProfile> profiles() {
        Arbitrary<List<Boolean>> flags = Arbitraries.of(true, false).list().ofSize(4);
        return Combinators.combine(
                        Arbitraries.of("minecraft:beast", "minecraft:walker"),
                        Arbitraries.doubles().between(1.0, 2000.0),
                        Arbitraries.doubles().between(0.0, 200.0),
                        Arbitraries.doubles().between(0.01, 2.0),
                        Arbitraries.integers().between(0, 5),
                        flags)
                .as((type, hp, dps, speed, tier, flag) -> new EnemyProfile(
                        type, hp, dps, speed, flag.get(0), flag.get(1), flag.get(2), flag.get(3), tier));
    }

    @Property
    void classify_AnyTypeAndProfile_ReturnsRealClass(
            @ForAll("types") String type, @ForAll("profiles") EnemyProfile profile) {
        TargetClass cls = DefenseProfileTests.PROFILE.classify(type, profile);

        assertTrue(
                EnumSet.allOf(TargetClass.class).contains(cls),
                "Классификация обязана вернуть реальный класс: иначе цель выпадет из таблицы приоритетов");
        assertEquals(
                cls,
                DefenseProfileTests.PROFILE.classify(type, profile),
                "Классификация детерминирована: иначе порядок целей мигает между пересборками");
    }

    @Property
    void orderFor_AnyRole_HasNoDuplicates(@ForAll("types") String ignored, @ForAll boolean archerPresent) {
        for (DefenderRole role : DefenderRole.values()) {
            List<TargetClass> order = DefenseProfileTests.PROFILE.orderFor(role, archerPresent);

            assertEquals(
                    Set.copyOf(order).size(),
                    order.size(),
                    "Порядок целей без дублей: повтор ломает тай-брейк, и цель выбирается случайно");
            assertTrue(
                    order.size() <= TargetClass.values().length,
                    "Порядок не длиннее множества классов: иначе в нём мусор вместо приоритетов");
        }
    }
}
