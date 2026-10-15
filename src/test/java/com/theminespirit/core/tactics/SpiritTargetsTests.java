package com.theminespirit.core.tactics;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SpiritTargetsTests {
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"minecraft:zombie", "minecraft:husk", "minecraft:skeleton", "minecraft:creeper",
            "minecraft:witch", "minecraft:spider", "minecraft:drowned", "minecraft:stray", "minecraft:blaze",
            "minecraft:warden", "minecraft:wither", "minecraft:zoglin", "minecraft:hoglin", "minecraft:ghast",
            "minecraft:phantom", "minecraft:slime", "minecraft:magma_cube", "minecraft:silverfish",
            "minecraft:endermite", "minecraft:shulker", "minecraft:vex", "minecraft:bogged", "minecraft:breeze",
            "minecraft:giant", "minecraft:guardian", "minecraft:elder_guardian", "minecraft:cave_spider",
            "minecraft:zombie_villager", "minecraft:wither_skeleton", "minecraft:ender_dragon"})
    void attacksSpirits_VanillaHostileMonster_ReturnsTrue(String type) {
        assertTrue(SpiritTargets.attacksSpirits(type),
                "Враждебный моб " + type + " обязан получать цель «дух»: иначе бой в игре начинается только от игрока, "
                        + "и тактика духов срабатывает вхолостую");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"minecraft:enderman", "minecraft:zombified_piglin", "minecraft:piglin",
            "minecraft:piglin_brute", "minecraft:pillager", "minecraft:vindicator", "minecraft:evoker",
            "minecraft:illusioner", "minecraft:ravager", "minecraft:iron_golem", "minecraft:villager",
            "minecraft:wolf", "minecraft:bee", "minecraft:sheep"})
    void attacksSpirits_NeutralPiglinIllagerOrPeaceful_ReturnsFalse(String type) {
        assertFalse(SpiritTargets.attacksSpirits(type),
                "Нейтральный, пиглин, иллагер или мирный " + type + " не должен получать цель «дух»: у него своя "
                        + "логика агрессии, и добавление цели сломало бы её");
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
