package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.theminespirit.core.husbandry.PetKind;
import com.theminespirit.core.husbandry.Species;
import org.junit.jupiter.api.Test;

class FarmWaysTests {
    @Test
    void species_WayId_NamesSpecies() {
        assertEquals(Species.COW, FarmWays.species("capture_cow"), "Ловля называет вид коровы");
        assertEquals(Species.SHEEP, FarmWays.species("breed_sheep"), "Разведение называет вид овцы");
        assertEquals(Species.PIG, FarmWays.species("cull_pig"), "Забой называет вид свиньи");
    }

    @Test
    void species_NoSpecies_IsNull() {
        assertNull(FarmWays.species("weave_wool"), "Тканьё шерсти — не вид животного");
        assertNull(FarmWays.species("harvest_honey"), "Мёд — не вид животного");
    }

    @Test
    void pet_WayId_NamesPet() {
        assertEquals(PetKind.WOLF, FarmWays.pet("tame_wolf"), "Приручение называет волка");
        assertEquals(PetKind.CAT, FarmWays.pet("tame_cat"), "Приручение называет кошку");
        assertEquals(PetKind.OCELOT, FarmWays.pet("trust_ocelot_salmon"), "Доверие называет оцелота");
        assertNull(FarmWays.pet("breed_cow"), "Корова — не питомец");
    }

    @Test
    void bed_WayId_NamesColoredBed() {
        assertEquals("white_bed", FarmWays.bed("craft_white_bed"), "Кровать из пути белая");
        assertEquals("light_blue_bed", FarmWays.bed("craft_light_blue_bed"), "Цвет кровати сохраняется");
        assertNull(FarmWays.bed("weave_wool"), "Тканьё — не кровать");
    }
}
