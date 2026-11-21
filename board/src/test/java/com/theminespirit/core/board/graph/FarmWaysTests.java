package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.theminespirit.core.husbandry.api.PetKind;
import com.theminespirit.core.husbandry.api.Species;
import org.junit.jupiter.api.Test;

class FarmWaysTests {
    @Test
    void species_WayId_NamesSpecies() {
        assertEquals(
                Species.COW,
                FarmWays.species("capture_cow"),
                "Ловля называет вид коровы"
                        + " Иначе тест species_WayId_NamesSpecies перестанет ловить эту поломку. При смене species_WayId_NamesSpecies проверь это утверждение.");
        assertEquals(
                Species.SHEEP,
                FarmWays.species("breed_sheep"),
                "Разведение называет вид овцы"
                        + " Иначе тест species_WayId_NamesSpecies перестанет ловить эту поломку. При смене species_WayId_NamesSpecies проверь это утверждение.");
        assertEquals(
                Species.PIG,
                FarmWays.species("cull_pig"),
                "Забой называет вид свиньи"
                        + " Иначе тест species_WayId_NamesSpecies перестанет ловить эту поломку. При смене species_WayId_NamesSpecies проверь это утверждение.");
    }

    @Test
    void species_NoSpecies_IsNull() {
        assertNull(
                FarmWays.species("weave_wool"),
                "Тканьё шерсти — не вид животного"
                        + " Иначе тест species_NoSpecies_IsNull перестанет ловить эту поломку. При смене species_NoSpecies_IsNull проверь это утверждение.");
        assertNull(
                FarmWays.species("harvest_honey"),
                "Мёд — не вид животного"
                        + " Иначе тест species_NoSpecies_IsNull перестанет ловить эту поломку. При смене species_NoSpecies_IsNull проверь это утверждение.");
    }

    @Test
    void pet_WayId_NamesPet() {
        assertEquals(
                PetKind.WOLF,
                FarmWays.pet("tame_wolf"),
                "Приручение называет волка"
                        + " Иначе тест pet_WayId_NamesPet перестанет ловить эту поломку. При смене pet_WayId_NamesPet проверь это утверждение.");
        assertEquals(
                PetKind.CAT,
                FarmWays.pet("tame_cat"),
                "Приручение называет кошку"
                        + " Иначе тест pet_WayId_NamesPet перестанет ловить эту поломку. При смене pet_WayId_NamesPet проверь это утверждение.");
        assertEquals(
                PetKind.OCELOT,
                FarmWays.pet("trust_ocelot_salmon"),
                "Доверие называет оцелота"
                        + " Иначе тест pet_WayId_NamesPet перестанет ловить эту поломку. При смене pet_WayId_NamesPet проверь это утверждение.");
        assertNull(
                FarmWays.pet("breed_cow"),
                "Корова — не питомец"
                        + " Иначе тест pet_WayId_NamesPet перестанет ловить эту поломку. При смене pet_WayId_NamesPet проверь это утверждение.");
    }

    @Test
    void bed_WayId_NamesColoredBed() {
        assertEquals(
                "white_bed",
                FarmWays.bed("craft_white_bed"),
                "Кровать из пути белая"
                        + " Иначе тест bed_WayId_NamesColoredBed перестанет ловить эту поломку. При смене bed_WayId_NamesColoredBed проверь это утверждение.");
        assertEquals(
                "light_blue_bed",
                FarmWays.bed("craft_light_blue_bed"),
                "Цвет кровати сохраняется"
                        + " Иначе тест bed_WayId_NamesColoredBed перестанет ловить эту поломку. При смене bed_WayId_NamesColoredBed проверь это утверждение.");
        assertNull(
                FarmWays.bed("weave_wool"),
                "Тканьё — не кровать"
                        + " Иначе тест bed_WayId_NamesColoredBed перестанет ловить эту поломку. При смене bed_WayId_NamesColoredBed проверь это утверждение.");
    }
}
