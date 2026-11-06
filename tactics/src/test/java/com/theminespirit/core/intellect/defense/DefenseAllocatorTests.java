package com.theminespirit.core.intellect.defense;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.coordinator.DefenseAssignment;
import com.theminespirit.core.tactics.EnemyFact;
import com.theminespirit.core.tactics.EnemyProfile;
import com.theminespirit.core.tactics.EnemyProfiles;
import com.theminespirit.core.tactics.Point;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DefenseAllocatorTests {
    static final Point CENTER = new Point(0, 64, 0);
    static final double RADIUS = 16.0;
    static final double EAST_BEARING = 0.5 * 2 * Math.PI / 8;
    static final EnemyProfiles PROFILES = new EnemyProfiles(
            Map.of(
                    "minecraft:zombie",
                    new EnemyProfile("minecraft:zombie", 20, 3, 0.23, false, false, false, false, 1),
                    "minecraft:spider",
                    new EnemyProfile("minecraft:spider", 16, 2, 0.3, false, false, false, false, 1),
                    "minecraft:creeper",
                    new EnemyProfile("minecraft:creeper", 20, 0, 0.25, false, true, false, false, 2)),
            new EnemyProfile("default", 20, 4, 1, false, false, false, false, 2));

    static DefenderCapability defender(String id, int armor, double hp) {
        return new DefenderCapability(UUID.fromString(id), new Point(1, 64, 1), hp, armor, false, 6.0, true);
    }

    static EnemyFact zombie(String id, double x, double z) {
        return new EnemyFact("minecraft:zombie", id, new Point(x, 64, z), 1.0, 0, true, true);
    }

    static long postsOnBearing(List<DefenseAssignment> posts, double bearing) {
        return posts.stream().filter(post -> post.bearing() == bearing).count();
    }

    @Test
    void allocate_NoDefenders_ReturnsEmpty() {
        assertEquals(
                List.of(),
                DefenseAllocator.allocate(
                        CENTER,
                        RADIUS,
                        List.of(),
                        List.of(zombie("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", 10, 0)),
                        PROFILES),
                "Без защитников постов нет: иначе приказ уйдёт несуществующему духу");
    }

    @Test
    void allocate_SingleDefenderEast_PostOnEastBearing() {
        List<DefenseAssignment> posts = DefenseAllocator.allocate(
                CENTER,
                RADIUS,
                List.of(defender("11111111-1111-1111-1111-111111111111", 10, 1.0)),
                List.of(zombie("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", 10, 0)),
                PROFILES);

        assertEquals(
                1, posts.size(), "Один защитник получает ровно один пост: иначе дух либо без поста, либо раздвоен");
        assertEquals(
                EAST_BEARING,
                posts.get(0).bearing(),
                "Пост стоит на азимуте угрожаемого сектора: иначе защитник смотрит не на угрозу");
        assertTrue(
                Math.abs(posts.get(0).post().distanceTo(CENTER) - DefenseTuning.FRONT_RADIUS) < 1e-9,
                "Пост внутри радиуса на дистанции FRONT_RADIUS: иначе дух встанет либо у дома, либо за периметром");
    }

    @Test
    void allocate_EastStrongerThanNorth_EastGetsMoreDefenders() {
        List<DefenderCapability> defenders = List.of(
                defender("11111111-1111-1111-1111-111111111111", 10, 1.0),
                defender("22222222-2222-2222-2222-222222222222", 10, 1.0),
                defender("33333333-3333-3333-3333-333333333333", 10, 1.0));
        List<EnemyFact> enemies = List.of(
                zombie("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", 10, 0),
                zombie("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb", 12, 1),
                new EnemyFact(
                        "minecraft:spider",
                        "cccccccc-cccc-cccc-cccc-cccccccccccc",
                        new Point(0, 64, -10),
                        1.0,
                        0,
                        true,
                        true));

        List<DefenseAssignment> posts = DefenseAllocator.allocate(CENTER, RADIUS, defenders, enemies, PROFILES);

        assertEquals(3, posts.size(), "Каждый защитник ровно один раз: иначе дух либо без поста, либо раздвоен");
        assertEquals(
                2,
                postsOnBearing(posts, EAST_BEARING),
                "Сильнейшее восточное направление получает два из трёх: иначе вес сектора не влияет на число постов");
        assertEquals(
                3,
                posts.stream()
                        .mapToDouble(post -> post.post().distanceTo(CENTER))
                        .filter(distance -> distance <= RADIUS + 1e-9)
                        .count(),
                "Все посты внутри радиуса: иначе дух встанет за периметром обороны");
    }

    @Test
    void allocate_StrongestFront_GoesToStrongestSector() {
        DefenderCapability weak = defender("11111111-1111-1111-1111-111111111111", 0, 0.2);
        DefenderCapability strong = defender("22222222-2222-2222-2222-222222222222", 20, 1.0);
        List<EnemyFact> enemies = List.of(
                zombie("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", 10, 0),
                zombie("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb", 12, 1),
                new EnemyFact(
                        "minecraft:spider",
                        "cccccccc-cccc-cccc-cccc-cccccccccccc",
                        new Point(0, 64, -10),
                        1.0,
                        0,
                        true,
                        true));

        List<DefenseAssignment> posts =
                DefenseAllocator.allocate(CENTER, RADIUS, List.of(weak, strong), enemies, PROFILES);

        DefenseAssignment strongPost = posts.stream()
                .filter(post -> post.defender().equals(strong.id()))
                .findFirst()
                .orElseThrow();
        assertEquals(
                EAST_BEARING,
                strongPost.bearing(),
                "Дух с лучшей бронёй встаёт на сильнейшее направление: иначе слабый доспех встретит главную угрозу");
    }

    @Test
    void allocate_FewerDefendersThanSectors_StrongestCovered() {
        List<EnemyFact> enemies = List.of(
                zombie("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", 10, 0),
                new EnemyFact(
                        "minecraft:spider",
                        "cccccccc-cccc-cccc-cccc-cccccccccccc",
                        new Point(0, 64, -10),
                        1.0,
                        0,
                        true,
                        true));

        List<DefenseAssignment> posts = DefenseAllocator.allocate(
                CENTER, RADIUS, List.of(defender("11111111-1111-1111-1111-111111111111", 10, 1.0)), enemies, PROFILES);

        assertEquals(
                1, posts.size(), "Единственный защитник получает один пост: иначе дух раздвоится между направлениями");
        assertEquals(
                EAST_BEARING,
                posts.get(0).bearing(),
                "При нехватке духов накрывается сильнейший сектор: иначе главная угроза останется без присмотра");
    }

    @Test
    void allocate_NoEnemies_EqualSectorAssignerPosts() {
        List<DefenseAssignment> posts = DefenseAllocator.allocate(
                CENTER,
                RADIUS,
                List.of(
                        defender("11111111-1111-1111-1111-111111111111", 10, 1.0),
                        defender("22222222-2222-2222-2222-222222222222", 10, 1.0)),
                List.of(),
                PROFILES);

        assertEquals(
                2,
                posts.size(),
                "Без врагов каждый защитник всё равно на посту: иначе мирное время оставит периметр пустым");
        for (DefenseAssignment post : posts) {
            assertTrue(
                    Math.abs(post.post().distanceTo(CENTER) - RADIUS) < 1e-9,
                    "Без угроз посты делит SectorAssigner поровну по кругу: иначе тихое распределение врёт");
        }
    }

    @Test
    void allocate_NullCenter_Throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> DefenseAllocator.allocate(
                        null,
                        RADIUS,
                        List.of(defender("11111111-1111-1111-1111-111111111111", 10, 1.0)),
                        List.of(zombie("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", 10, 0)),
                        PROFILES),
                "Круг без центра обязан падать: иначе сектора посчитаются из ничего");
    }

    @Test
    void allocate_ZeroRadius_Throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> DefenseAllocator.allocate(
                        CENTER,
                        0,
                        List.of(defender("11111111-1111-1111-1111-111111111111", 10, 1.0)),
                        List.of(zombie("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", 10, 0)),
                        PROFILES),
                "Нулевой радиус обязан падать: иначе сектора вырождены в точку");
    }

    @Test
    void allocate_DuplicateDefender_Throws() {
        DefenderCapability defender = defender("11111111-1111-1111-1111-111111111111", 10, 1.0);

        assertThrows(
                IllegalArgumentException.class,
                () -> DefenseAllocator.allocate(
                        CENTER,
                        RADIUS,
                        List.of(defender, defender),
                        List.of(zombie("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", 10, 0)),
                        PROFILES),
                "Дух дважды в списке обязан падать: один дух — один пост, иначе раздвоение приказа");
    }
}
