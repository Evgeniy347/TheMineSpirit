package com.theminespirit.core.intellect.defense;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.coordinator.DefenseAssignment;
import com.theminespirit.core.tactics.EnemyFact;
import com.theminespirit.core.tactics.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Assume;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class DefenseAllocatorProperties {
    @Provide
    Arbitrary<List<DefenderCapability>> defenders() {
        return Combinators.combine(Arbitraries.create(UUID::randomUUID),
                Arbitraries.integers().between(-16, 16).map(x -> new Point(x, 64, -x)),
                Arbitraries.doubles().between(0.0, 1.0), Arbitraries.integers().between(0, 20),
                Arbitraries.of(true, false), Arbitraries.doubles().between(0.0, 12.0),
                Arbitraries.of(true, false))
                .as((id, pos, hp, armor, bow, dps, heal) -> new DefenderCapability(id, pos, hp, armor, bow, dps, heal))
                .list().ofMinSize(1).ofMaxSize(5).map(DefenseAllocatorProperties::distinctIds);
    }

    @Provide
    Arbitrary<List<EnemyFact>> enemies() {
        return Combinators.combine(Arbitraries.of("minecraft:zombie", "minecraft:spider", "minecraft:creeper"),
                Arbitraries.create(UUID::randomUUID).map(UUID::toString),
                Arbitraries.integers().between(-14, 14), Arbitraries.integers().between(-14, 14))
                .as((type, id, x, z) -> new EnemyFact(type, id, new Point(x, 64, z), 1.0, 0, true, true))
                .list().ofMinSize(1).ofMaxSize(4);
    }

    @Property
    void allocate_AnyInput_EachDefenderOnce(@ForAll("defenders") List<DefenderCapability> defenders,
            @ForAll("enemies") List<EnemyFact> enemies, @ForAll("center") Point center,
            @ForAll("radius") double radius) {
        List<DefenseAssignment> posts = DefenseAllocator.allocate(center, radius, defenders, enemies,
                DefenseAllocatorTests.PROFILES);

        List<UUID> inputIds = defenders.stream().map(DefenderCapability::id).sorted().toList();
        List<UUID> postIds = posts.stream().map(DefenseAssignment::defender).sorted().toList();

        assertEquals(inputIds, postIds,
                "Каждый защитник ровно на одном посту: иначе дух либо без поста, либо раздвоен");
    }

    @Property
    void allocate_AnyInput_PostsWithinRadius(@ForAll("defenders") List<DefenderCapability> defenders,
            @ForAll("enemies") List<EnemyFact> enemies, @ForAll("center") Point center,
            @ForAll("radius") double radius) {
        List<DefenseAssignment> posts = DefenseAllocator.allocate(center, radius, defenders, enemies,
                DefenseAllocatorTests.PROFILES);

        for (DefenseAssignment post : posts) {
            assertTrue(post.post().distanceTo(center) <= radius + 1e-9,
                    "Пост внутри радиуса обороны: иначе дух встанет за периметром");
        }
    }

    @Property
    void allocate_StrengthenedSector_KeepsDefenders(@ForAll("defenders") List<DefenderCapability> defenders,
            @ForAll("enemies") List<EnemyFact> enemies, @ForAll("center") Point center,
            @ForAll("radius") double radius) {
        EnemyFact first = enemies.get(0);
        double sectorBearing = ThreatBearing
                .sum(List.of(first), center, DefenseAllocatorTests.PROFILES).get(0).bearing();
        List<EnemyFact> stronger = new ArrayList<>(enemies);
        stronger.add(new EnemyFact(first.type(), UUID.randomUUID().toString(), first.pos(), 1.0, 0, true, true));

        long before = countOnBearing(
                DefenseAllocator.allocate(center, radius, defenders, enemies, DefenseAllocatorTests.PROFILES),
                sectorBearing);
        long after = countOnBearing(
                DefenseAllocator.allocate(center, radius, defenders, stronger, DefenseAllocatorTests.PROFILES),
                sectorBearing);

        assertTrue(after >= before,
                "Усилившееся направление не теряет защитников: иначе подмога врагу оголяет то же направление");
    }

    @Property
    void allocate_RemovedDefender_PlanStaysValid(@ForAll("defenders") List<DefenderCapability> defenders,
            @ForAll("enemies") List<EnemyFact> enemies, @ForAll("center") Point center,
            @ForAll("radius") double radius) {
        Assume.that(defenders.size() >= 2);
        List<DefenderCapability> remaining = defenders.subList(1, defenders.size());

        List<DefenseAssignment> full = DefenseAllocator.allocate(center, radius, defenders, enemies,
                DefenseAllocatorTests.PROFILES);
        List<DefenseAssignment> reduced = DefenseAllocator.allocate(center, radius, remaining, enemies,
                DefenseAllocatorTests.PROFILES);

        assertEquals(defenders.size(), full.size(),
                "Полный план накрывает всех: иначе выбытие началось уже с потери поста");
        assertEquals(remaining.size(), reduced.size(),
                "После выбытия оставшиеся перераспределены: иначе посты застыли на выбывшем");
        for (DefenseAssignment post : reduced) {
            assertTrue(post.post().distanceTo(center) <= radius + 1e-9,
                    "Перераспределённые посты внутри радиуса: иначе уцелевшие встанут за периметром");
        }
        List<DirectionThreat> threats =
                ThreatBearing.sum(enemies, center, DefenseAllocatorTests.PROFILES);
        Assume.that(!threats.isEmpty() && remaining.size() >= threats.size());
        for (DirectionThreat threat : threats) {
            assertTrue(countOnBearing(reduced, threat.bearing()) >= 1,
                    "Каждое угрожаемое направление накрыто: иначе выбытие оголило сектор");
        }
    }

    @Property
    void allocate_ShuffledDefenders_SamePosts(@ForAll("defenders") List<DefenderCapability> defenders,
            @ForAll("enemies") List<EnemyFact> enemies, @ForAll("center") Point center,
            @ForAll("radius") double radius) {
        List<DefenderCapability> shuffled = new ArrayList<>(defenders);
        Collections.shuffle(shuffled, new Random(13));

        assertEquals(postsByDefender(
                DefenseAllocator.allocate(center, radius, defenders, enemies, DefenseAllocatorTests.PROFILES)),
                postsByDefender(
                        DefenseAllocator.allocate(center, radius, shuffled, enemies, DefenseAllocatorTests.PROFILES)),
                "Посты не зависят от порядка входа: иначе пересборка дёргает духов между постами");
    }

    @Provide
    Arbitrary<Point> center() {
        return Combinators.combine(Arbitraries.integers().between(-16, 16), Arbitraries.integers().between(-16, 16))
                .as((x, z) -> new Point(x, 64, z));
    }

    @Provide
    Arbitrary<Double> radius() {
        return Arbitraries.doubles().between(4.0, 20.0);
    }

    private static long countOnBearing(List<DefenseAssignment> posts, double bearing) {
        return posts.stream().filter(post -> post.bearing() == bearing).count();
    }

    private static Map<UUID, Point> postsByDefender(List<DefenseAssignment> posts) {
        return posts.stream().collect(Collectors.toMap(DefenseAssignment::defender, DefenseAssignment::post,
                (first, second) -> first, TreeMap::new));
    }

    private static List<DefenderCapability> distinctIds(List<DefenderCapability> defenders) {
        return defenders.stream()
                .collect(Collectors.toMap(DefenderCapability::id, item -> item, (first, second) -> first,
                        LinkedHashMap::new))
                .values().stream().sorted(Comparator.comparing(DefenderCapability::id)).toList();
    }
}
