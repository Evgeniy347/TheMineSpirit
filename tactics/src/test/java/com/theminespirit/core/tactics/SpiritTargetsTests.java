package com.theminespirit.core.tactics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class SpiritTargetsTests {
    @ParameterizedTest(name = "{0}")
    @ValueSource(
            strings = {
                "minecraft:zombie",
                "minecraft:husk",
                "minecraft:skeleton",
                "minecraft:creeper",
                "minecraft:witch",
                "minecraft:spider",
                "minecraft:drowned",
                "minecraft:stray",
                "minecraft:blaze",
                "minecraft:wither",
                "minecraft:ghast",
                "minecraft:phantom",
                "minecraft:slime",
                "minecraft:magma_cube",
                "minecraft:silverfish",
                "minecraft:endermite",
                "minecraft:shulker",
                "minecraft:vex",
                "minecraft:bogged",
                "minecraft:giant",
                "minecraft:guardian",
                "minecraft:elder_guardian",
                "minecraft:cave_spider",
                "minecraft:zombie_villager",
                "minecraft:wither_skeleton"
            })
    void attacksSpirits_VanillaHostileMonster_ReturnsTrue(String type) {
        assertTrue(
                SpiritTargets.conditionFor(type).isPresent(),
                "Враждебный моб " + type + " обязан получать цель «дух»: иначе бой в игре начинается только от игрока, "
                        + "и тактика духов срабатывает вхолостую"
                        + " При смене attacksSpirits_VanillaHostileMonster_ReturnsTrue проверь это утверждение.");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
            strings = {
                "minecraft:enderman",
                "minecraft:zombified_piglin",
                "minecraft:piglin",
                "minecraft:piglin_brute",
                "minecraft:pillager",
                "minecraft:vindicator",
                "minecraft:evoker",
                "minecraft:illusioner",
                "minecraft:ravager",
                "minecraft:iron_golem",
                "minecraft:villager",
                "minecraft:wolf",
                "minecraft:bee",
                "minecraft:sheep",
                "minecraft:breeze",
                "minecraft:hoglin",
                "minecraft:warden",
                "minecraft:zoglin",
                "minecraft:ender_dragon"
            })
    void attacksSpirits_NeutralBrainMobOrPeaceful_ReturnsFalse(String type) {
        assertFalse(
                SpiritTargets.conditionFor(type).isPresent(),
                "Нейтральный, пиглин, иллагер, мирный " + type + " или моб, чью цель выбирает Brain (breeze, hoglin, "
                        + "warden, zoglin) и дракон с его фазами босса, не получает цель «дух»: добавленный в "
                        + "targetSelector goal игнорируется, а своя логика агрессии сломалась бы"
                        + " Иначе тест attacksSpirits_NeutralBrainMobOrPeaceful_ReturnsFalse перестанет ловить эту поломку. При смене attacksSpirits_NeutralBrainMobOrPeaceful_ReturnsFalse проверь это утверждение.");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"minecraft:spider", "minecraft:cave_spider"})
    void conditionFor_SpiderFamily_ReturnsLightBelowHalf(String type) {
        assertEquals(
                Optional.of(SpiritTargets.Condition.LIGHT_BELOW_HALF),
                SpiritTargets.conditionFor(type),
                "Паук и пещерный паук берут цель «дух» как игрока в SpiderTargetGoal: только при свете < 0.5, "
                        + "иначе днём на солнце сами нападают на духов без ванильного правила"
                        + " При смене conditionFor_SpiderFamily_ReturnsLightBelowHalf проверь это утверждение.");
    }

    @Test
    void conditionFor_Drowned_ReturnsNightOrInWater() {
        assertEquals(
                Optional.of(SpiritTargets.Condition.NIGHT_OR_IN_WATER),
                SpiritTargets.conditionFor("minecraft:drowned"),
                "Утопленник берёт духа как игрока в Drowned.okTarget: ночь или цель в воде"
                        + " Иначе тест conditionFor_Drowned_ReturnsNightOrInWater перестанет ловить эту поломку. При смене conditionFor_Drowned_ReturnsNightOrInWater проверь это утверждение.");
    }

    @Test
    void conditionFor_Zombie_ReturnsAlways() {
        assertEquals(
                Optional.of(SpiritTargets.Condition.ALWAYS),
                SpiritTargets.conditionFor("minecraft:zombie"),
                "Зомби без условий: ванильная цель на игрока не имеет фильтра"
                        + " Иначе тест conditionFor_Zombie_ReturnsAlways перестанет ловить эту поломку. При смене conditionFor_Zombie_ReturnsAlways проверь это утверждение.");
    }

    @Test
    void conditionFor_UnknownType_ReturnsEmpty() {
        assertFalse(
                SpiritTargets.conditionFor("minecraft:never_seen").isPresent(),
                "Неизвестный тип не получает цель: белый список явный, и новый мод-моб не станет охотником за духами "
                        + "без решения владельца"
                        + " Иначе тест conditionFor_UnknownType_ReturnsEmpty перестанет ловить эту поломку. При смене conditionFor_UnknownType_ReturnsEmpty проверь это утверждение.");
    }

    @Test
    void conditionFor_NullType_Throws() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> SpiritTargets.conditionFor(null),
                "Null-тип — ошибка вызова, а не «мирный моб»: молчаливый null спрятал бы дефект вызова"
                        + " Иначе тест conditionFor_NullType_Throws перестанет ловить эту поломку. При смене conditionFor_NullType_Throws проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("entityTypeId"),
                "Сообщение называет параметр, чтобы дефект вызова было видно, а получили: " + error.getMessage()
                        + " Иначе тест conditionFor_NullType_Throws перестанет ловить эту поломку. При смене conditionFor_NullType_Throws проверь это утверждение.");
    }

    @Test
    void conditionFor_BlankType_Throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SpiritTargets.conditionFor("  "),
                "Пустой id — ошибка вызова: адаптер обязан передать id типа из реестра"
                        + " Иначе тест conditionFor_BlankType_Throws перестанет ловить эту поломку. При смене conditionFor_BlankType_Throws проверь это утверждение.");
    }

    @ParameterizedTest(name = "свет {0}, день {1}, вода {2} → {3}")
    @CsvSource({
        "0.49, true, false, true",
        "0.50, true, false, false",
        "1.00, true, false, false",
        "0.49, false, false, true",
        "0.00, false, false, true"
    })
    void condition_LightBelowHalf_RejectsOnBrightLight(float light, boolean day, boolean water, boolean expected) {
        assertEquals(
                expected,
                SpiritTargets.Condition.LIGHT_BELOW_HALF.holds(light, day, water),
                "Паук обязан повторять SpiderTargetGoal: при свете ≥ 0.5 цель не берётся, ночь и вода не влияют"
                        + " Иначе тест condition_LightBelowHalf_RejectsOnBrightLight перестанет ловить эту поломку. При смене condition_LightBelowHalf_RejectsOnBrightLight проверь это утверждение.");
    }

    @ParameterizedTest(name = "свет {0}, день {1}, вода {2} → {3}")
    @CsvSource({
        "1.00, false, false, true",
        "1.00, true, true, true",
        "1.00, true, false, false",
        "0.00, true, false, false"
    })
    void condition_NightOrInWater_RejectsOnDryDay(float light, boolean day, boolean water, boolean expected) {
        assertEquals(
                expected,
                SpiritTargets.Condition.NIGHT_OR_IN_WATER.holds(light, day, water),
                "Утопленник обязан повторять Drowned.okTarget: днём на суше цель не берётся, ночь или вода решают, "
                        + "свет не влияет"
                        + " Иначе тест condition_NightOrInWater_RejectsOnDryDay перестанет ловить эту поломку. При смене condition_NightOrInWater_RejectsOnDryDay проверь это утверждение.");
    }

    @ParameterizedTest(name = "свет {0}, день {1}, вода {2} → {3}")
    @CsvSource({"1.00, true, false, true", "0.00, false, false, true"})
    void condition_Always_AcceptsAnyLight(float light, boolean day, boolean water, boolean expected) {
        assertEquals(
                expected,
                SpiritTargets.Condition.ALWAYS.holds(light, day, water),
                "ALWAYS не имеет условий: мобы этого правила целятся в духа как обычная ванильная цель"
                        + " Иначе тест condition_Always_AcceptsAnyLight перестанет ловить эту поломку. При смене condition_Always_AcceptsAnyLight проверь это утверждение.");
    }

    @Test
    void attacksSpirits_UnknownType_ReturnsFalse() {
        assertFalse(
                SpiritTargets.conditionFor("minecraft:never_seen").isPresent(),
                "Неизвестный тип не получает цель: белый список явный, и новый мод-моб не станет охотником за духами "
                        + "без решения владельца"
                        + " Иначе тест attacksSpirits_UnknownType_ReturnsFalse перестанет ловить эту поломку. При смене attacksSpirits_UnknownType_ReturnsFalse проверь это утверждение.");
    }

    @Test
    void attacksSpirits_NullType_Throws() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> SpiritTargets.conditionFor(null),
                "Null-тип — ошибка вызова, а не «мирный моб»: молчаливый false спрятал бы дефект вызова"
                        + " Иначе тест attacksSpirits_NullType_Throws перестанет ловить эту поломку. При смене attacksSpirits_NullType_Throws проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("entityTypeId"),
                "Сообщение называет параметр, чтобы дефект вызова было видно, а получили: " + error.getMessage()
                        + " Иначе тест attacksSpirits_NullType_Throws перестанет ловить эту поломку. При смене attacksSpirits_NullType_Throws проверь это утверждение.");
    }

    @Test
    void attacksSpirits_BlankType_Throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SpiritTargets.conditionFor("  "),
                "Пустой id — ошибка вызова: адаптер обязан передать id типа из реестра"
                        + " Иначе тест attacksSpirits_BlankType_Throws перестанет ловить эту поломку. При смене attacksSpirits_BlankType_Throws проверь это утверждение.");
    }
}
