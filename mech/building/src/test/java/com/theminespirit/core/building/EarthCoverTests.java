package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class EarthCoverTests {
    @Test
    void covers_GrassOnDirtCell_Accepted() {
        assertTrue(
                EarthCover.covers("minecraft:grass_block", "minecraft:dirt"),
                "дёрн на земляной клетке схемы — та же земля: копать траву ради земли незачем"
                        + " Иначе тест covers_GrassOnDirtCell_Accepted перестанет ловить эту поломку. При смене covers_GrassOnDirtCell_Accepted проверь это утверждение.");
    }

    @Test
    void covers_DirtOnGrassCell_Accepted() {
        assertTrue(
                EarthCover.covers("minecraft:dirt", "minecraft:grass_block"),
                "земля на травяной клетке плана — равноценная замена: дёрн как блок не добыть"
                        + " Иначе тест covers_DirtOnGrassCell_Accepted перестанет ловить эту поломку. При смене covers_DirtOnGrassCell_Accepted проверь это утверждение.");
    }

    @Test
    void earth_GrassAndDirt_Recognized() {
        assertTrue(
                EarthCover.earth("minecraft:grass_block"),
                "дёрн — земля: верхний слой участка"
                        + " Иначе тест earth_GrassAndDirt_Recognized перестанет ловить эту поломку. При смене earth_GrassAndDirt_Recognized проверь это утверждение.");
        assertTrue(
                EarthCover.earth("dirt"),
                "земля без пространства имён — та же земля"
                        + " Иначе тест earth_GrassAndDirt_Recognized перестанет ловить эту поломку. При смене earth_GrassAndDirt_Recognized проверь это утверждение.");
        assertFalse(
                EarthCover.earth("minecraft:stone"),
                "камень не земля"
                        + " Иначе тест earth_GrassAndDirt_Recognized перестанет ловить эту поломку. При смене earth_GrassAndDirt_Recognized проверь это утверждение.");
        assertFalse(
                EarthCover.earth("minecraft:dirt_path"),
                "тропа — отдельный блок, не земля"
                        + " Иначе тест earth_GrassAndDirt_Recognized перестанет ловить эту поломку. При смене earth_GrassAndDirt_Recognized проверь это утверждение.");
    }

    @Test
    void covers_NamesWithAndWithoutNamespace_SameEarth() {
        assertTrue(
                EarthCover.covers("grass_block", "minecraft:dirt"),
                "одно правило земли для планов без пространства имён: дёрн на месте земли не перекрывают"
                        + " Иначе тест covers_NamesWithAndWithoutNamespace_SameEarth перестанет ловить эту поломку. При смене covers_NamesWithAndWithoutNamespace_SameEarth проверь это утверждение.");
        assertFalse(
                EarthCover.covers("stone", "minecraft:dirt"),
                "камень землёй не становится"
                        + " Иначе тест covers_NamesWithAndWithoutNamespace_SameEarth перестанет ловить эту поломку. При смене covers_NamesWithAndWithoutNamespace_SameEarth проверь это утверждение.");
    }

    @Test
    void covers_OtherBlocks_Rejected() {
        assertFalse(
                EarthCover.covers("minecraft:stone", "minecraft:dirt"),
                "камень на земляной клетке — не земля"
                        + " Иначе тест covers_OtherBlocks_Rejected перестанет ловить эту поломку. При смене covers_OtherBlocks_Rejected проверь это утверждение.");
        assertFalse(
                EarthCover.covers("minecraft:grass_block", "minecraft:dirt_path"),
                "тропа этапов 2–3 дёрном не заменяется"
                        + " Иначе тест covers_OtherBlocks_Rejected перестанет ловить эту поломку. При смене covers_OtherBlocks_Rejected проверь это утверждение.");
    }

    @Test
    void restored_Grass_GetsDirt() {
        assertEquals(
                "minecraft:dirt",
                EarthCover.restored("minecraft:grass_block"),
                "пролом травяной клетки заделывается землёй: дёрн лопатой не добыть, а грунт тот же"
                        + " Иначе тест restored_Grass_GetsDirt перестанет ловить эту поломку. При смене restored_Grass_GetsDirt проверь это утверждение.");
        assertEquals(
                "dirt",
                EarthCover.restored("dirt"),
                "пролом земляной клетки заделывается той же землёй в том же коротком имени"
                        + " Иначе тест restored_Grass_GetsDirt перестанет ловить эту поломку. При смене restored_Grass_GetsDirt проверь это утверждение.");
    }

    @Test
    void restored_OtherBlock_Kept() {
        assertEquals(
                "minecraft:jungle_planks",
                EarthCover.restored("minecraft:jungle_planks"),
                "не грунт — заделка тем же блоком: дешёвая замена ломала бы постройку"
                        + " Иначе тест restored_OtherBlock_Kept перестанет ловить эту поломку. При смене restored_OtherBlock_Kept проверь это утверждение.");
    }

    @Test
    void normalized_Grass_GetsDirt() {
        assertEquals(
                "dirt",
                EarthCover.normalized("minecraft:grass_block"),
                "дёрн приводится к земле: склад, нужда и выдача считают их одним предметом"
                        + " Иначе тест normalized_Grass_GetsDirt перестанет ловить эту поломку. При смене normalized_Grass_GetsDirt проверь это утверждение.");
        assertEquals(
                "dirt",
                EarthCover.normalized("dirt"),
                "земля остаётся землёй"
                        + " Иначе тест normalized_Grass_GetsDirt перестанет ловить эту поломку. При смене normalized_Grass_GetsDirt проверь это утверждение.");
        assertEquals(
                "stone",
                EarthCover.normalized("minecraft:stone"),
                "камень нормализация не задевает"
                        + " Иначе тест normalized_Grass_GetsDirt перестанет ловить эту поломку. При смене normalized_Grass_GetsDirt проверь это утверждение.");
    }

    @Test
    void same_GrassAndDirt_Equivalent() {
        assertTrue(
                EarthCover.same("minecraft:dirt", "grass_block"),
                "земля и дёрн — один предмет: спрос на землю закрывает лежащий дёрн"
                        + " Иначе тест same_GrassAndDirt_Equivalent перестанет ловить эту поломку. При смене same_GrassAndDirt_Equivalent проверь это утверждение.");
        assertTrue(
                EarthCover.same("grass_block", "minecraft:grass_block"),
                "пространство имён не различает один и тот же предмет"
                        + " Иначе тест same_GrassAndDirt_Equivalent перестанет ловить эту поломку. При смене same_GrassAndDirt_Equivalent проверь это утверждение.");
        assertFalse(
                EarthCover.same("minecraft:dirt", "minecraft:stone"),
                "камень землю не закрывает"
                        + " Иначе тест same_GrassAndDirt_Equivalent перестанет ловить эту поломку. При смене same_GrassAndDirt_Equivalent проверь это утверждение.");
    }

    @Test
    void ids_Earth_GivesBothInOneNamespace() {
        assertEquals(
                List.of("minecraft:dirt", "minecraft:grass_block"),
                EarthCover.ids("minecraft:dirt"),
                "для нужды в земле ищем оба земляных предмета в том же пространстве имён"
                        + " Иначе тест ids_Earth_GivesBothInOneNamespace перестанет ловить эту поломку. При смене ids_Earth_GivesBothInOneNamespace проверь это утверждение.");
        assertEquals(
                List.of("minecraft:dirt", "minecraft:grass_block"),
                EarthCover.ids("grass_block"),
                "Короткое имя — то же пространство minecraft: выдача обязана назвать предмет полностью"
                        + " Иначе тест ids_Earth_GivesBothInOneNamespace перестанет ловить эту поломку. При смене ids_Earth_GivesBothInOneNamespace проверь это утверждение.");
        assertEquals(
                List.of("minecraft:stone"),
                EarthCover.ids("minecraft:stone"),
                "не земля — ровно один предмет, без подмены"
                        + " Иначе тест ids_Earth_GivesBothInOneNamespace перестанет ловить эту поломку. При смене ids_Earth_GivesBothInOneNamespace проверь это утверждение.");
    }
}
