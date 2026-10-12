package com.theminespirit.core.tactics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class FactMapTests {
    private static final List<String> SPEC_NAMES = List.of("self.hpFraction", "self.armor", "self.dps", "self.speed",
            "self.food", "self.blocks", "self.hasRanged", "self.hasWaterBucket", "self.allies", "self.healItems",
            "enemy.count", "enemy.visibleCount", "enemy.nearest", "enemy.nearestExplosive", "enemy.totalHp",
            "enemy.totalDps", "enemy.fastest", "enemy.anyRanged", "enemy.anyExplosive", "enemy.anySwims",
            "enemy.anyFlies", "enemy.targetingSelf", "env.lavaNear", "env.dropHeight", "env.light", "env.night",
            "env.homeDistance", "env.homeReachable", "env.coverDistance", "env.waterDistance",
            "env.escapeAvailability", "order.mode", "order.zoneDistance", "order.leashLeft",
            "order.protectedThreatened", "order.helpAvailable", "time.sinceHit", "time.sinceStrike", "time.sinceEnemySeen",
            "state.tactical", "derived.winMargin");
    private static final Pattern KEY = Pattern.compile("[a-z]+\\.[A-Za-z]+");

    @Test
    void factNames_Catalog_ContainsEveryDocumentedFact() {
        Set<String> keys = Arrays.stream(FactName.values()).map(FactName::key).collect(Collectors.toSet());

        for (String name : SPEC_NAMES) {
            assertTrue(keys.contains(name),
                    "В каталоге фактов нет " + name + ": правило из rules.json на этот факт не загрузится");
        }
    }

    @Test
    void factNames_Catalog_KeysAreUniqueAndWellFormed() {
        List<String> keys = Arrays.stream(FactName.values()).map(FactName::key).toList();

        assertEquals(keys.size(), Set.copyOf(keys).size(),
                "Ключи фактов уникальны: иначе два факта слились бы в условии под одним именем");
        for (String key : keys) {
            assertTrue(KEY.matcher(key).matches(),
                    "Ключ факта «" + key + "» не подходит под группа.имя: так факты читаются в JSON и в trace");
        }
    }

    @Test
    void from_MixedEnemies_BuildsExpectedEnemyFacts() {
        Situation situation = SituationBuilder.create()
                .enemies("zombie", 4, 2).enemy("skeleton", 9).enemy("creeper", 6).build();

        FactMap facts = Assessments.facts(situation);

        assertEquals(List.of(4.0, 4.0, 6.0, 10.0, 0.25),
                List.of(facts.number(FactName.ENEMY_COUNT), facts.number(FactName.ENEMY_VISIBLE_COUNT),
                        facts.number(FactName.ENEMY_NEAREST_EXPLOSIVE), facts.number(FactName.ENEMY_TOTAL_DPS),
                        facts.number(FactName.ENEMY_FASTEST)),
                "Сводка врагов: четверо, все видны, крипер в 6, суммарный урон 3+3+4+0, быстрейший 0.25");
        assertEquals(List.of(true, true, false, false),
                List.of(facts.bool(FactName.ENEMY_ANY_RANGED), facts.bool(FactName.ENEMY_ANY_EXPLOSIVE),
                        facts.bool(FactName.ENEMY_ANY_SWIMS), facts.bool(FactName.ENEMY_ANY_FLIES)),
                "Флаги врагов складываются по профилям: скелет стреляет, крипер взрывается");
    }

    @Test
    void from_NoEnemies_NearestIsFarAndCountsAreZero() {
        FactMap facts = Assessments.facts(SituationBuilder.create().build());

        assertEquals(List.of(0.0, EnvFacts.FAR_DISTANCE, EnvFacts.FAR_DISTANCE),
                List.of(facts.number(FactName.ENEMY_COUNT), facts.number(FactName.ENEMY_NEAREST),
                        facts.number(FactName.ENEMY_NEAREST_EXPLOSIVE)),
                "Без врагов расстояния равны FAR_DISTANCE, а не 0: иначе правило «враг ≤ 3» сработало бы на пустом поле");
    }

    @Test
    void from_OrdersAndSelf_AreReflectedAsFacts() {
        Situation situation = SituationBuilder.create().mode(CombatMode.GUARD_AREA).hp(5).armor(7).ammo(3)
                .zone(0, 0, 10).ward(3, 4, true).sinceHit(12).build();

        FactMap facts = Assessments.facts(situation);

        assertEquals(List.of("guard_area", "guard_area", "calm"),
                List.of(facts.text(FactName.ORDER_MODE), facts.text(FactName.ORDER_ORDERED),
                        facts.text(FactName.STATE_TACTICAL)),
                "Режим и состояние выдаются идентификаторами в нижнем регистре: так их пишут в JSON-условиях");
        assertEquals(List.of(0.25, 7.0, 12.0, true, true, true),
                List.of(facts.number(FactName.SELF_HP_FRACTION), facts.number(FactName.SELF_ARMOR),
                        facts.number(FactName.TIME_SINCE_HIT), facts.bool(FactName.SELF_HAS_RANGED),
                        facts.bool(FactName.ORDER_ZONE_SET), facts.bool(FactName.ORDER_PROTECTED_THREATENED)),
                "Факты о себе и приказах берутся из Situation без искажений");
    }

    @Test
    void from_StrikeTimer_IsReflectedAsFact() {
        Situation situation = SituationBuilder.create().sinceStrike(7).build();

        FactMap facts = Assessments.facts(situation);

        assertEquals(7.0, facts.number(FactName.TIME_SINCE_STRIKE),
                "Тики с последнего удара духа по врагу видны тактике: по ним правило «ударил–отошёл» держит отход "
                        + "после удара, а не по дистанции; значение обязано доходить без искажений");
    }

    @Test
    void number_BoolFact_ThrowsWithFactName() {
        FactMap facts = Assessments.facts(SituationBuilder.create().build());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> facts.number(FactName.ENV_NIGHT),
                "Чтение булева факта как числа — ошибка кода, а не значение 0: тихая подмена скрыла бы опечатку в типе");

        assertTrue(error.getMessage().contains("env.night"),
                "Сообщение называет факт, чтобы ошибку типа нашли сразу: " + error.getMessage());
    }
}
