package com.theminespirit.core.coordinator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.tactics.Point;
import java.util.List;
import java.util.UUID;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class SectorAssignerProperties {
    private static final Point CENTER = new Point(0, 64, 0);

    @Provide
    Arbitrary<List<UUID>> defenderLists() {
        return Arbitraries.create(UUID::randomUUID).list().ofMinSize(1).ofMaxSize(8);
    }

    @Provide
    Arbitrary<List<UUID>> defenderListsTwoPlus() {
        return Arbitraries.create(UUID::randomUUID).list().ofMinSize(2).ofMaxSize(8);
    }

    @Provide
    Arbitrary<Double> radii() {
        return Arbitraries.doubles().between(0.5, 32);
    }

    @Property
    void assign_AnyDefenderList_EachDefenderOnce(
            @ForAll("defenderLists") List<UUID> defenders, @ForAll("radii") double radius) {
        List<DefenseAssignment> assignments = new SectorAssigner().assign(CENTER, radius, defenders);

        assertEquals(
                defenders.size(),
                assignments.size(),
                "Каждый защитник получает ровно один пост: иначе кто-то без сектора или кто-то с двумя");
        assertEquals(
                defenders.stream().sorted().toList(),
                assignments.stream().map(DefenseAssignment::defender).sorted().toList(),
                "Множество защитников в постах равно множеству на входе: ни один не потерян и не добавлен");
    }

    @Property
    void assign_AnyDefenderList_PostsWithinRadius(
            @ForAll("defenderLists") List<UUID> defenders, @ForAll("radii") double radius) {
        List<DefenseAssignment> assignments = new SectorAssigner().assign(CENTER, radius, defenders);

        for (DefenseAssignment assignment : assignments) {
            assertEquals(
                    radius,
                    assignment.post().distanceTo(CENTER),
                    1e-6,
                    "Пост не дальше радиуса от центра: иначе защитник уходит за пределы обороны");
        }
    }

    @Property
    void assign_AnyDefenderList_Deterministic(
            @ForAll("defenderLists") List<UUID> defenders, @ForAll("radii") double radius) {
        SectorAssigner assigner = new SectorAssigner();

        assertEquals(
                assigner.assign(CENTER, radius, defenders),
                assigner.assign(CENTER, radius, defenders),
                "Тот же вход даёт тот же выход: сектора стабильны между пересборками плана");
    }

    @Property
    void assign_DefenderRemoved_OthersStayInRadius(
            @ForAll("defenderListsTwoPlus") List<UUID> defenders, @ForAll("radii") double radius) {

        UUID removed = defenders.get(0);
        List<DefenseAssignment> after = new SectorAssigner()
                .assign(
                        CENTER,
                        radius,
                        defenders.stream()
                                .filter(defender -> !defender.equals(removed))
                                .toList());

        assertEquals(
                defenders.size() - 1,
                after.size(),
                "Без выбывшего защитника секторов на один меньше: остальные перераспределяются");
        for (DefenseAssignment assignment : after) {
            assertEquals(
                    radius,
                    assignment.post().distanceTo(CENTER),
                    1e-6,
                    "После выбытия оставшиеся посты снова в радиусе: перераспределение не выгоняет защитников "
                            + "за пределы обороны");
        }
        assertTrue(
                after.stream().map(DefenseAssignment::defender).noneMatch(removed::equals),
                "Выбывший защитник не получает поста в новом распределении");
    }
}
