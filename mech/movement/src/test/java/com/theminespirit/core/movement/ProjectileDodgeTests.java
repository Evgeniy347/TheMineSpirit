package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.mining.api.Cell;
import com.theminespirit.core.mining.api.MiningSafety;
import com.theminespirit.core.mining.api.SafetyView;
import com.theminespirit.core.movement.ProjectileDodge.Dodge;
import com.theminespirit.core.movement.ProjectileDodge.Position;
import com.theminespirit.core.movement.ProjectileDodge.Request;
import com.theminespirit.core.movement.ProjectileTrajectory.Bounds;
import com.theminespirit.core.movement.ProjectileTrajectory.Projectile;
import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class ProjectileDodgeTests {
    private static final ProjectileDodge RULES = ProjectileDodge.load();
    private static final Bounds BODY = new Bounds(10.2, 64.0, 10.2, 10.8, 65.8, 10.8);
    private static final Bounds BESIDE = new Bounds(13.2, 64.0, 10.2, 13.8, 65.8, 10.8);
    private static final BlockCoord FEET = new BlockCoord(10, 64, 10);
    private static final Predicate<BlockCoord> FLAT = cell -> cell.y() == 64;
    private static final int NOW = 100;

    private static final Projectile FROM_SOUTH = new Projectile(10.5, 65.0, 20.5, 0.0, 0.0, -3.0, 0.05, 0.99);

    @Test
    void table_OwnerDefaults_KeepReactionWindowAndOneCellStep() {
        assertEquals(
                new ProjectileDodge(true, 20.0, 3, 12, 8, 0.45, 0.3, 1.5),
                RULES,
                "Пассивное уклонение (владелец 07.11): стрела за 3–12 тиков до попадания, шаг в сторону раз в 8 тиков, "
                        + "скорость шага 0,45. Менять числа — в data/theminespirit/combat/dodge.json"
                        + " Иначе тест table_OwnerDefaults_KeepReactionWindowAndOneCellStep перестанет ловить эту поломку. При смене table_OwnerDefaults_KeepReactionWindowAndOneCellStep проверь это утверждение.");
    }

    @Test
    void arrowTowardsSpirit_DirectPath_StepsAcrossAndMisses() {
        assertTrue(
                RULES.threat(FROM_SOUTH, BODY).isPresent(),
                "Стрела летит в духа: предсказание траектории обязано найти попадание, иначе навык мёртв"
                        + " При смене arrowTowardsSpirit_DirectPath_StepsAcrossAndMisses проверь это утверждение.");

        Dodge step = RULES.decide(request(List.of(FROM_SOUTH)), FLAT).orElseThrow();

        assertEquals(
                0,
                step.dz(),
                "Стрела летит вдоль Z — поперёк неё шаг по X, а не назад/вперёд по траектории"
                        + " Иначе тест arrowTowardsSpirit_DirectPath_StepsAcrossAndMisses перестанет ловить эту поломку. При смене arrowTowardsSpirit_DirectPath_StepsAcrossAndMisses проверь это утверждение.");
        assertEquals(
                1,
                Math.abs(step.dx()),
                "Уклонение — ровно одна соседняя клетка: дух не телепортируется"
                        + " Иначе тест arrowTowardsSpirit_DirectPath_StepsAcrossAndMisses перестанет ловить эту поломку. При смене arrowTowardsSpirit_DirectPath_StepsAcrossAndMisses проверь это утверждение.");
        assertTrue(
                RULES.threat(FROM_SOUTH, BODY.shift(step.dx(), 0, step.dz())).isEmpty(),
                "После шага поперёк траектории стрела обязана пройти мимо: иначе уклонение не уклоняет"
                        + " При смене arrowTowardsSpirit_DirectPath_StepsAcrossAndMisses проверь это утверждение.");
    }

    @Test
    void fireballTowardsSpirit_AcceleratingFlight_StepsAcrossAndMisses() {
        Projectile fireball = new Projectile(10.5, 65.0, 15.0, 0.0, 0.0, -0.1, 0.0, 0.95, 0.1);

        assertTrue(
                RULES.threat(fireball, BODY).isPresent(),
                "Огненный шар набирает скорость вдоль полёта: без ускорения в модели он «не долетает» за горизонт "
                        + "предсказания, и дух стоит под ударом (debtmisc6: AbstractHurtingProjectile не покрыт)"
                        + " Иначе тест fireballTowardsSpirit_AcceleratingFlight_StepsAcrossAndMisses перестанет ловить эту поломку. При смене fireballTowardsSpirit_AcceleratingFlight_StepsAcrossAndMisses проверь это утверждение.");

        Dodge step = RULES.decide(request(List.of(fireball)), FLAT).orElseThrow();

        assertEquals(
                0,
                step.dz(),
                "Шар летит вдоль Z — уклонение поперёк траектории по X, а не назад/вперёд"
                        + " Иначе тест fireballTowardsSpirit_AcceleratingFlight_StepsAcrossAndMisses перестанет ловить эту поломку. При смене fireballTowardsSpirit_AcceleratingFlight_StepsAcrossAndMisses проверь это утверждение.");
        assertEquals(
                1,
                Math.abs(step.dx()),
                "Уклонение от шара — ровно одна соседняя клетка"
                        + " Иначе тест fireballTowardsSpirit_AcceleratingFlight_StepsAcrossAndMisses перестанет ловить эту поломку. При смене fireballTowardsSpirit_AcceleratingFlight_StepsAcrossAndMisses проверь это утверждение.");
        assertTrue(
                RULES.threat(fireball, BODY.shift(step.dx(), 0, step.dz())).isEmpty(),
                "После шага поперёк траектории шар обязан пройти мимо: иначе ускорение в модели не совпало с полётом"
                        + " При смене fireballTowardsSpirit_AcceleratingFlight_StepsAcrossAndMisses проверь это утверждение.");
    }

    @Test
    void projectile_NegativeAcceleration_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Projectile(0, 0, 0, 1, 0, 0, 0, 0.95, -0.1),
                "Отрицательное ускорение вдоль полёта обязано отклоняться: торможение задаёт сопротивление, "
                        + "а минус означал бы снаряд, летящий назад"
                        + " Иначе тест projectile_NegativeAcceleration_IsRejected перестанет ловить эту поломку. При смене projectile_NegativeAcceleration_IsRejected проверь это утверждение.");
    }

    @Test
    void arrowPastSpirit_PassesBeside_DoesNotMove() {
        Projectile past = new Projectile(13.5, 65.0, 20.5, 0.0, 0.0, -3.0, 0.05, 0.99);

        assertTrue(
                RULES.threat(past, BODY).isEmpty(),
                "Стрела летит в трёх блоках сбоку — попадания нет, выдумывать угрозу нельзя"
                        + " Иначе тест arrowPastSpirit_PassesBeside_DoesNotMove перестанет ловить эту поломку. При смене arrowPastSpirit_PassesBeside_DoesNotMove проверь это утверждение.");
        assertTrue(
                RULES.decide(request(List.of(past)), FLAT).isEmpty(),
                "Стрела мимо: дух не дёргается и продолжает текущую работу"
                        + " Иначе тест arrowPastSpirit_PassesBeside_DoesNotMove перестанет ловить эту поломку. При смене arrowPastSpirit_PassesBeside_DoesNotMove проверь это утверждение.");
    }

    @Test
    void twoSpirits_OneInPath_OnlyItStepsAside() {
        Projectile between = new Projectile(10.5, 65.0, 20.5, 0.0, 0.0, -3.0, 0.05, 0.99);

        Optional<Dodge> first = RULES.decide(request(List.of(between)), FLAT);
        Optional<Dodge> second = RULES.decide(
                new Request(List.of(between), BESIDE, new BlockCoord(13, 64, 10), List.of(), -1, NOW), FLAT);

        assertTrue(
                first.isPresent(),
                "Стрела проходит через первого духа — он обязан уклониться"
                        + " Иначе тест twoSpirits_OneInPath_OnlyItStepsAside перестанет ловить эту поломку. При смене twoSpirits_OneInPath_OnlyItStepsAside проверь это утверждение.");
        assertTrue(
                second.isEmpty(),
                "Второй дух стоит в стороне от линии огня — уклоняться ему незачем"
                        + " Иначе тест twoSpirits_OneInPath_OnlyItStepsAside перестанет ловить эту поломку. При смене twoSpirits_OneInPath_OnlyItStepsAside проверь это утверждение.");
    }

    @Test
    void twoArrows_FromOppositeSides_StepAvoidsBoth() {
        Projectile fromSouth = FROM_SOUTH;
        Projectile fromNorth = new Projectile(10.5, 65.0, 0.5, 0.0, 0.0, 3.0, 0.05, 0.99);

        Dodge step = RULES.decide(request(List.of(fromSouth, fromNorth)), FLAT).orElseThrow();

        assertEquals(
                0,
                step.dz(),
                "Две стрелы летят вдоль Z с обеих сторон — уклонение поперёк обеих по X"
                        + " Иначе тест twoArrows_FromOppositeSides_StepAvoidsBoth перестанет ловить эту поломку. При смене twoArrows_FromOppositeSides_StepAvoidsBoth проверь это утверждение.");
        assertTrue(
                RULES.threat(fromSouth, BODY.shift(step.dx(), 0, step.dz())).isEmpty()
                        && RULES.threat(fromNorth, BODY.shift(step.dx(), 0, step.dz()))
                                .isEmpty(),
                "Один шаг в сторону обязан вывести из траектории обеих стрел: иначе вторая добьёт духа"
                        + " При смене twoArrows_FromOppositeSides_StepAvoidsBoth проверь это утверждение.");
    }

    @Test
    void arrowTooClose_FastFlight_IsSeenButNotDodged() {
        Projectile pointBlank = new Projectile(10.5, 65.0, 12.5, 0.0, 0.0, -3.0, 0.05, 0.99);
        Projectile fast = new Projectile(10.5, 65.0, 20.5, 0.0, 0.0, -10.0, 0.05, 0.99);

        assertTrue(
                RULES.threat(fast, BODY).isPresent(),
                "Быстрая стрела проскакивает тело между двумя замерами: отрезок пути обязан давать попадание"
                        + " Иначе тест arrowTooClose_FastFlight_IsSeenButNotDodged перестанет ловить эту поломку. При смене arrowTooClose_FastFlight_IsSeenButNotDodged проверь это утверждение.");
        assertTrue(
                RULES.decide(request(List.of(pointBlank)), FLAT).isEmpty(),
                "До попадания меньше минимального времени реакции: дух не успевает, и это честный промах реакции"
                        + " Иначе тест arrowTooClose_FastFlight_IsSeenButNotDodged перестанет ловить эту поломку. При смене arrowTooClose_FastFlight_IsSeenButNotDodged проверь это утверждение.");
    }

    @Test
    void cooldownAfterDodge_BeforeReady_BlocksRepeat() {
        Request cooling = new Request(List.of(FROM_SOUTH), BODY, FEET, List.of(), NOW - 7, NOW);
        Request ready = new Request(List.of(FROM_SOUTH), BODY, FEET, List.of(), NOW - 8, NOW);

        assertTrue(
                RULES.decide(cooling, FLAT).isEmpty(),
                "Кулдаун между уклонениями: дух не дёргается каждый тик, иначе он не может ни бежать, ни работать"
                        + " При смене cooldownAfterDodge_BeforeReady_BlocksRepeat проверь это утверждение.");
        assertTrue(
                RULES.decide(ready, FLAT).isPresent(),
                "После кулдауна новая стрела снова вызывает уклонение"
                        + " Иначе тест cooldownAfterDodge_BeforeReady_BlocksRepeat перестанет ловить эту поломку. При смене cooldownAfterDodge_BeforeReady_BlocksRepeat проверь это утверждение.");
    }

    @Test
    void cliffAndLavaOnOneSide_UnsafeSide_StepsToSolidSide() {
        SafetyView lavaEast = coord -> {
            if (coord.y() < 64) {
                return Cell.SOLID;
            }
            if (coord.x() == 11 && coord.y() == 64) {
                return Cell.LAVA;
            }
            return coord.y() <= 65 ? Cell.PASSABLE : Cell.SOLID;
        };
        SafetyView cliffEast = coord -> {
            if (coord.x() == 11 && coord.y() == 63) {
                return Cell.PASSABLE;
            }
            if (coord.y() < 64) {
                return Cell.SOLID;
            }
            return coord.y() <= 65 ? Cell.PASSABLE : Cell.SOLID;
        };

        Dodge lavaStep = RULES.decide(request(List.of(FROM_SOUTH)), cell -> MiningSafety.canStand(cell, lavaEast))
                .orElseThrow();
        Dodge cliffStep = RULES.decide(request(List.of(FROM_SOUTH)), cell -> MiningSafety.canStand(cell, cliffEast))
                .orElseThrow();

        assertEquals(
                -1,
                lavaStep.dx(),
                "С востока лава — шаг на восток запрещён, дух уходит на запад, а не в опасную клетку"
                        + " Иначе тест cliffAndLavaOnOneSide_UnsafeSide_StepsToSolidSide перестанет ловить эту поломку. При смене cliffAndLavaOnOneSide_UnsafeSide_StepsToSolidSide проверь это утверждение.");
        assertEquals(
                -1,
                cliffStep.dx(),
                "С востока обрыв — шаг на восток запрещён, дух уходит на запад"
                        + " Иначе тест cliffAndLavaOnOneSide_UnsafeSide_StepsToSolidSide перестанет ловить эту поломку. При смене cliffAndLavaOnOneSide_UnsafeSide_StepsToSolidSide проверь это утверждение.");
        assertTrue(
                RULES.decide(request(List.of(FROM_SOUTH)), cell -> false).isEmpty(),
                "Обе стороны непроходимы — уклонения нет: лучше принять удар, чем шагнуть в лаву или с обрыва"
                        + " Иначе тест cliffAndLavaOnOneSide_UnsafeSide_StepsToSolidSide перестанет ловить эту поломку. При смене cliffAndLavaOnOneSide_UnsafeSide_StepsToSolidSide проверь это утверждение.");
    }

    @Test
    void mobBesideSpirit_OnOneSide_DodgesAway() {
        Position zombie = new Position(11.5, 64.0, 10.5);

        Dodge step = RULES.decide(request(List.of(FROM_SOUTH), List.of(zombie)), FLAT)
                .orElseThrow();

        assertEquals(
                -1,
                step.dx(),
                "Зомби стоит на востоке — шаг на восток вёл бы в моба, дух уходит на запад"
                        + " Иначе тест mobBesideSpirit_OnOneSide_DodgesAway перестанет ловить эту поломку. При смене mobBesideSpirit_OnOneSide_DodgesAway проверь это утверждение.");
        assertTrue(
                RULES.decide(request(List.of(FROM_SOUTH), List.of(zombie, new Position(9.5, 64.0, 10.5))), FLAT)
                        .isEmpty(),
                "Мобы с обеих сторон — уклонения нет: шаг в моба не спасает от стрелы"
                        + " Иначе тест mobBesideSpirit_OnOneSide_DodgesAway перестанет ловить эту поломку. При смене mobBesideSpirit_OnOneSide_DodgesAway проверь это утверждение.");
    }

    @Test
    void fleeingSpirit_ArrowFromBehind_DodgesSidewaysAndKeepsRunning() {
        Projectile fromBehind = new Projectile(0.5, 65.0, 10.5, 3.0, 0.0, 0.0, 0.05, 0.99);

        Dodge step = RULES.decide(request(List.of(fromBehind)), FLAT).orElseThrow();

        assertEquals(
                0,
                step.dx(),
                "Дух убегает на восток — уклонение поперёк бега по Z, а не назад в стрелу"
                        + " Иначе тест fleeingSpirit_ArrowFromBehind_DodgesSidewaysAndKeepsRunning перестанет ловить эту поломку. При смене fleeingSpirit_ArrowFromBehind_DodgesSidewaysAndKeepsRunning проверь это утверждение.");
        assertEquals(
                1,
                Math.abs(step.dz()),
                "Уклонение — один шаг вбок"
                        + " Иначе тест fleeingSpirit_ArrowFromBehind_DodgesSidewaysAndKeepsRunning перестанет ловить эту поломку. При смене fleeingSpirit_ArrowFromBehind_DodgesSidewaysAndKeepsRunning проверь это утверждение.");
        assertTrue(
                0.2 + step.dx() * RULES.stepSpeed() > 0,
                "Боковой шаг не отменяет бег: прибавка к скорости поперёк не разворачивает духа назад"
                        + " Иначе тест fleeingSpirit_ArrowFromBehind_DodgesSidewaysAndKeepsRunning перестанет ловить эту поломку. При смене fleeingSpirit_ArrowFromBehind_DodgesSidewaysAndKeepsRunning проверь это утверждение.");
        assertTrue(
                RULES.threat(fromBehind, BODY.shift(step.dx(), 0, step.dz())).isEmpty(),
                "Боковой шаг выводит из траектории стрелы, пущенной вдогонку"
                        + " Иначе тест fleeingSpirit_ArrowFromBehind_DodgesSidewaysAndKeepsRunning перестанет ловить эту поломку. При смене fleeingSpirit_ArrowFromBehind_DodgesSidewaysAndKeepsRunning проверь это утверждение.");
    }

    private static Request request(List<Projectile> projectiles) {
        return request(projectiles, List.of());
    }

    private static Request request(List<Projectile> projectiles, List<Position> mobs) {
        return new Request(projectiles, BODY, FEET, mobs, -1, NOW);
    }
}
