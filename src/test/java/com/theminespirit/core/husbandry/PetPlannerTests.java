package com.theminespirit.core.husbandry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class PetPlannerTests {
    @Test
    void cap_AnySpiritCount_HalfRoundedDown() {
        assertEquals(
                5,
                PetRules.cap(PetKind.WOLF, 10),
                "Десять духов держат пять волков: лимит вида — половина духов с округлением вниз");
        assertEquals(
                2,
                PetRules.cap(PetKind.CAT, 5),
                "Пять духов держат двух кошек: половина с округлением вниз, иначе лимит обходился бы");
        assertEquals(
                0,
                PetRules.cap(PetKind.OCELOT, 1),
                "Один дух не держит ни одного оцелота: половина от одного округляется вниз до нуля");
    }

    @Test
    void action_FourSpiritsTwoWolves_NoBreed() {
        assertEquals(
                PetPlanner.Action.NONE,
                PetPlanner.action(PetKind.WOLF, 2, 0, 4),
                "Четыре духа, две волка — лимит выбран, размножение не ставится и новых волков не приручаем");
    }

    @Test
    void action_FourSpiritsOneWolf_TamePlanned() {
        assertEquals(
                PetPlanner.Action.TAME,
                PetPlanner.action(PetKind.WOLF, 1, 1, 4),
                "Четыре духа, один волк при диком рядом — место до лимита есть, приручение ставится");
    }

    @Test
    void action_OneWolfAtHigherLimit_BreedPlanned() {
        assertEquals(
                PetPlanner.Action.BREED,
                PetPlanner.action(PetKind.WOLF, 2, 0, 6),
                "Шесть духов держат трёх волков: пара ниже лимита размножается");
    }

    @Test
    void action_OcelotLacking_TrustPlanned() {
        assertEquals(
                PetPlanner.Action.TRUST,
                PetPlanner.action(PetKind.OCELOT, 0, 1, 4),
                "Оцелотов меньше лимита — доверие сырой рыбой ставится, а не размножение без пары");
    }

    @Test
    void action_OcelotAtLimit_NoTrust() {
        assertEquals(
                PetPlanner.Action.NONE,
                PetPlanner.action(PetKind.OCELOT, 2, 3, 4),
                "Оцелотов на лимите — доверие не ставится, даже если рядом дикие оцелоты");
    }

    @Test
    void action_NegativeCounts_Rejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> PetPlanner.action(PetKind.WOLF, -1, 0, 4),
                "Отрицательное поголовье обязано падать: минус волков прятал бы перебор лимита");
        assertThrows(
                NullPointerException.class,
                () -> PetPlanner.action(null, 0, 0, 4),
                "Вид null обязан отвергаться: без него неизвестен лимит");
    }

    @Test
    void foods_FromGraphPaths_OwnItemsPerKind() {
        assertTrue(
                PetRules.tameFoods(PetKind.WOLF).contains("bone"),
                "Волка приручают костью: корм берётся из пути tame_wolf, фактически "
                        + PetRules.tameFoods(PetKind.WOLF));
        assertEquals(
                List.of("cod", "salmon"),
                PetRules.tameFoods(PetKind.OCELOT),
                "Оцелот доверяет и треске, и лососю: пути trust_ocelot и trust_ocelot_salmon дают оба корма");
        assertTrue(
                PetRules.breedFoods(PetKind.WOLF).containsAll(List.of("beef", "porkchop")),
                "Волков разводят и говядиной, и свининой: " + PetRules.breedFoods(PetKind.WOLF));
        assertEquals(
                List.of("cod"),
                PetRules.breedFoods(PetKind.OCELOT),
                "Оцелотов разводят треской: " + PetRules.breedFoods(PetKind.OCELOT));
    }
}
