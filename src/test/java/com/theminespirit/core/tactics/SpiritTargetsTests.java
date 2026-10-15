package com.theminespirit.core.tactics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class SpiritTargetsTests {
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"minecraft:zombie", "minecraft:husk", "minecraft:skeleton", "minecraft:creeper",
            "minecraft:witch", "minecraft:spider", "minecraft:drowned", "minecraft:stray", "minecraft:blaze",
            "minecraft:wither", "minecraft:ghast", "minecraft:phantom", "minecraft:slime", "minecraft:magma_cube",
            "minecraft:silverfish", "minecraft:endermite", "minecraft:shulker", "minecraft:vex", "minecraft:bogged",
            "minecraft:giant", "minecraft:guardian", "minecraft:elder_guardian", "minecraft:cave_spider",
            "minecraft:zombie_villager", "minecraft:wither_skeleton"})
    void attacksSpirits_VanillaHostileMonster_ReturnsTrue(String type) {
        assertTrue(SpiritTargets.attacksSpirits(type),
                "Враждебный моб " + type + " обязан получать цель «дух»: иначе бой в игре начинается только от игрока, "
                        + "и тактика духов срабатывает вхолостую");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"minecraft:enderman", "minecraft:zombified_piglin", "minecraft:piglin",
            "minecraft:piglin_brute", "minecraft:pillager", "minecraft:vindicator", "minecraft:evoker",
            "minecraft:illusioner", "minecraft:ravager", "minecraft:iron_golem", "minecraft:villager",
            "minecraft:wolf", "minecraft:bee", "minecraft:sheep", "minecraft:breeze", "minecraft:hoglin",
            "minecraft:warden", "minecraft:zoglin", "minecraft:ender_dragon"})
    void attacksSpirits_NeutralBrainMobOrPeaceful_ReturnsFalse(String type) {
        assertFalse(SpiritTargets.attacksSpirits(type),
                "Нейтральный, пиглин, иллагер, мирный " + type + " или моб, чью цель выбирает Brain (breeze, hoglin, "
                        + "warden, zoglin) и дракон с его фазами босса, не получает цель «дух»: добавленный в "
                        + "targetSelector goal игнорируется, а своя логика агрессии сломалась бы");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"minecraft:spider", "minecraft:cave_spider"})
    void conditionFor_SpiderFamily_ReturnsLightBelowHalf(String type) {
        assertEquals(SpiritTargets.Condition.LIGHT_BELOW_HALF, SpiritTargets.conditionFor(type),
                "Паук и пещерный паук берут цель «дух» как игрока в SpiderTargetGoal: только при свете < 0.5, "
                        + "иначе днём на солнце сами нападают на духов без ванильного правила");
    }

    @Test
    void conditionFor_Drowned_ReturnsNightOrInWater() {
        assertEquals(SpiritTargets.Condition.NIGHT_OR_IN_WATER, SpiritTargets.conditionFor("minecraft:drowned"),
                "Утопленник берёт духа как игрока в Drowned.okTarget: ночь или цель в воде");
    }

    @Test
    void conditionFor_Zombie_ReturnsAlways() {
        assertEquals(SpiritTargets.Condition.ALWAYS, SpiritTargets.conditionFor("minecraft:zombie"),
                "Зомби без условий: ванильная цель на игрока не имеет фильтра");
    }

    @Test
    void conditionFor_UnknownType_ReturnsNull() {
        assertNull(SpiritTargets.conditionFor("minecraft:never_seen"),
                "Неизвестный тип не получает цель: белый список явный, и новый мод-моб не станет охотником за духами "
                        + "без решения владельца");
    }

    @Test
    void conditionFor_NullType_Throws() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> SpiritTargets.conditionFor(null),
                "Null-тип — ошибка вызова, а не «мирный моб»: молчаливый null спрятал бы дефект вызова");

        assertTrue(error.getMessage().contains("entityTypeId"),
                "Сообщение называет параметр, чтобы дефект вызова было видно, а получили: " + error.getMessage());
    }

    @Test
    void conditionFor_BlankType_Throws() {
        assertThrows(IllegalArgumentException.class, () -> SpiritTargets.conditionFor("  "),
                "Пустой id — ошибка вызова: адаптер обязан передать id типа из реестра");
    }

    @ParameterizedTest(name = "свет {0}, день {1}, вода {2} → {3}")
    @CsvSource({"0.49, true, false, true", "0.50, true, false, false", "1.00, true, false, false",
            "0.49, false, false, true", "0.00, false, false, true"})
    void condition_LightBelowHalf_RejectsOnBrightLight(float light, boolean day, boolean water,
            boolean expected) {
        assertEquals(expected, SpiritTargets.Condition.LIGHT_BELOW_HALF.holds(light, day, water),
                "Паук обязан повторять SpiderTargetGoal: при свете ≥ 0.5 цель не берётся, ночь и вода не влияют");
    }

    @ParameterizedTest(name = "свет {0}, день {1}, вода {2} → {3}")
    @CsvSource({"1.00, false, false, true", "1.00, true, true, true", "1.00, true, false, false",
            "0.00, true, false, false"})
    void condition_NightOrInWater_RejectsOnDryDay(float light, boolean day, boolean water,
            boolean expected) {
        assertEquals(expected, SpiritTargets.Condition.NIGHT_OR_IN_WATER.holds(light, day, water),
                "Утопленник обязан повторять Drowned.okTarget: днём на суше цель не берётся, ночь или вода решают, "
                        + "свет не влияет");
    }

    @ParameterizedTest(name = "свет {0}, день {1}, вода {2} → {3}")
    @CsvSource({"1.00, true, false, true", "0.00, false, false, true"})
    void condition_Always_AcceptsAnyLight(float light, boolean day, boolean water,
            boolean expected) {
        assertEquals(expected, SpiritTargets.Condition.ALWAYS.holds(light, day, water),
                "ALWAYS не имеет условий: мобы этого правила целятся в духа как обычная ванильная цель");
    }

    @Test
    void attacksSpirits_UnknownType_ReturnsFalse() {
        assertFalse(SpiritTargets.attacksSpirits("minecraft:never_seen"),
                "Неизвестный тип не получает цель: белый список явный, и новый мод-моб не станет охотником за духами "
                        + "без решения владельца");
    }

    @Test
    void attacksSpirits_NullType_Throws() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> SpiritTargets.attacksSpirits(null),
                "Null-тип — ошибка вызова, а не «мирный моб»: молчаливый false спрятал бы дефект вызова");

        assertTrue(error.getMessage().contains("entityTypeId"),
                "Сообщение называет параметр, чтобы дефект вызова было видно, а получили: " + error.getMessage());
    }

    @Test
    void attacksSpirits_BlankType_Throws() {
        assertThrows(IllegalArgumentException.class, () -> SpiritTargets.attacksSpirits("  "),
                "Пустой id — ошибка вызова: адаптер обязан передать id типа из реестра");
    }
}
