package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.movement.ProjectileDodge.Position;
import com.theminespirit.core.movement.ProjectileDodge.Request;
import com.theminespirit.core.movement.ProjectileTrajectory.Bounds;
import com.theminespirit.core.movement.ProjectileTrajectory.Projectile;
import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class ProjectileDodgeTestsPart2 {
    private static final ProjectileDodge RULES = ProjectileDodge.load();
    private static final Bounds BODY = new Bounds(10.2, 64.0, 10.2, 10.8, 65.8, 10.8);
    private static final BlockCoord FEET = new BlockCoord(10, 64, 10);
    private static final Predicate<BlockCoord> FLAT = cell -> cell.y() == 64;
    private static final int NOW = 100;

    private static final Projectile FROM_SOUTH = new Projectile(10.5, 65.0, 20.5, 0.0, 0.0, -3.0, 0.05, 0.99);

    private static Request request(List<Projectile> projectiles) {
        return request(projectiles, List.of());
    }

    private static Request request(List<Projectile> projectiles, List<Position> mobs) {
        return new Request(projectiles, BODY, FEET, mobs, -1, NOW);
    }

    @Test
    void disabledSkill_DirectHit_NoDodge() {
        ProjectileDodge off = new ProjectileDodge(
                false,
                RULES.scanRadius(),
                RULES.minReactionTicks(),
                RULES.maxPredictionTicks(),
                RULES.cooldownTicks(),
                RULES.stepSpeed(),
                RULES.bodyMargin(),
                RULES.mobClearance());

        assertTrue(
                RULES.threat(FROM_SOUTH, BODY).isPresent(),
                "Стрела всё ещё летит в духа — выключение навыка не отменяет угрозу"
                        + " Иначе тест disabledSkill_DirectHit_NoDodge перестанет ловить эту поломку. При смене disabledSkill_DirectHit_NoDodge проверь это утверждение.");
        assertTrue(
                off.decide(request(List.of(FROM_SOUTH)), FLAT).isEmpty(),
                "Навык выключен в данных — рефлекса нет; так defcases проверяют, что уклонение вообще работает"
                        + " Иначе тест disabledSkill_DirectHit_NoDodge перестанет ловить эту поломку. При смене disabledSkill_DirectHit_NoDodge проверь это утверждение.");
    }

    @Test
    void parse_UnknownField_RejectedWithFieldName() {
        String json = """
                {"enabled": true, "scanRadius": 20.0, "minReactionTicks": 3, "maxPredictionTicks": 12,
                 "cooldownTicks": 8, "stepSpeed": 0.45, "bodyMargin": 0.3, "mobClearance": 1.5,
                 "вкус": 1}
                """;

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> ProjectileDodge.parse(json, "dodge.json"),
                "Битый датапак обязан падать при загрузке с названием поля: иначе ошибка всплывёт в бою"
                        + " При смене parse_UnknownField_RejectedWithFieldName проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("вкус"),
                "Ошибка обязана называть неизвестное поле, иначе опечатка в датапаке молча сделает навык мёртвым; "
                        + "получено: " + error.getMessage()
                        + " При смене parse_UnknownField_RejectedWithFieldName проверь это утверждение.");
    }

    @Test
    void parse_ZeroReaction_RejectedWithThreshold() {
        String json = """
                {"enabled": true, "scanRadius": 20.0, "minReactionTicks": 0, "maxPredictionTicks": 12,
                 "cooldownTicks": 8, "stepSpeed": 0.45, "bodyMargin": 0.3, "mobClearance": 1.5}
                """;

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> ProjectileDodge.parse(json, "dodge.json"),
                "Битый датапак обязан падать при загрузке с названием поля: иначе ошибка всплывёт в бою"
                        + " При смене parse_ZeroReaction_RejectedWithThreshold проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("реакция"),
                "Время реакции 0 тиков — мгновенный рефлекс; загрузка обязана назвать порог, иначе датапак "
                        + "незаметно даст читерский навык; получено: " + error.getMessage()
                        + " При смене parse_ZeroReaction_RejectedWithThreshold проверь это утверждение.");
    }
}
