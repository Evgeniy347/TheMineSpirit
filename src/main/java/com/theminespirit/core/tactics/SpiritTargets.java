package com.theminespirit.core.tactics;

import java.util.Set;

/**
 * Кому из ванильных враждебных мобов мод добавляет цель «дух».
 *
 * <p>Список намеренно явный: моб попадает в него только по решению владельца. Нейтральные
 * (эндермен, зомбифицированный пиглин), пиглины и иллагеры не трогаются — у них своя логика
 * агрессии, и добавление цели сломало бы её.
 *
 * <p>Breeze, hoglin, warden, zoglin и дракон в списке отсутствуют: цель у них выбирает Brain
 * ({@code StartAttacking} по памяти {@code NEAREST_VISIBLE_*}; hoglin вообще видит только
 * игроков), а не {@code targetSelector}, поэтому добавленный туда goal игнорируется; дракон
 * выбирает цель фазами босса. Чтобы такой моб получил цель «дух», её вешают на его
 * сенсор/память Brain — отдельная задача.
 */
public final class SpiritTargets {
    private static final Set<String> ATTACKERS = Set.of(
            "minecraft:blaze",
            "minecraft:bogged",
            "minecraft:cave_spider",
            "minecraft:creeper",
            "minecraft:drowned",
            "minecraft:elder_guardian",
            "minecraft:endermite",
            "minecraft:ghast",
            "minecraft:giant",
            "minecraft:guardian",
            "minecraft:husk",
            "minecraft:magma_cube",
            "minecraft:phantom",
            "minecraft:shulker",
            "minecraft:silverfish",
            "minecraft:skeleton",
            "minecraft:slime",
            "minecraft:spider",
            "minecraft:stray",
            "minecraft:vex",
            "minecraft:witch",
            "minecraft:wither",
            "minecraft:wither_skeleton",
            "minecraft:zombie",
            "minecraft:zombie_villager");

    private SpiritTargets() {
    }

    public static boolean attacksSpirits(String entityTypeId) {
        if (entityTypeId == null || entityTypeId.isBlank()) {
            throw new IllegalArgumentException("entityTypeId пуст: тип моба называется его id, "
                    + "например minecraft:zombie");
        }
        return ATTACKERS.contains(entityTypeId);
    }
}
