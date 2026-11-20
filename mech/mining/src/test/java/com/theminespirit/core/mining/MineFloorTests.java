package com.theminespirit.core.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MineFloorTests {
    @Test
    void decide_Stone_Keeps() {
        assertEquals(
                MineFloor.Action.KEEP,
                MineFloor.decide(new MineFloor.Seen("minecraft:stone", false, false, false, true)),
                "камень — годный пол @floor: его не трогают, булыжник не тратится"
                        + " Иначе тест decide_Stone_Keeps перестанет ловить эту поломку. При смене decide_Stone_Keeps проверь это утверждение.");
    }

    @Test
    void decide_Water_Places() {
        assertEquals(
                MineFloor.Action.PLACE,
                MineFloor.decide(new MineFloor.Seen("minecraft:water", false, true, true, false)),
                "вода в полу — ставится булыжник до шага вперёд, иначе дух ступит в воду"
                        + " При смене decide_Water_Places проверь это утверждение.");
    }

    @Test
    void decide_Air_Places() {
        assertEquals(
                MineFloor.Action.PLACE,
                MineFloor.decide(new MineFloor.Seen("minecraft:air", true, false, true, false)),
                "пустота под ступенью (пещера) — ставится булыжник, иначе дух провалится"
                        + " При смене decide_Air_Places проверь это утверждение.");
    }

    @Test
    void decide_GravelOverVoid_Replaces() {
        assertEquals(
                MineFloor.Action.REPLACE,
                MineFloor.decide(new MineFloor.Seen("minecraft:gravel", false, false, false, false)),
                "гравий без опоры обвалится из-под ног — заменить булыжником"
                        + " Иначе тест decide_GravelOverVoid_Replaces перестанет ловить эту поломку. При смене decide_GravelOverVoid_Replaces проверь это утверждение.");
    }

    @Test
    void decide_GravelOnRock_Keeps() {
        assertEquals(
                MineFloor.Action.KEEP,
                MineFloor.decide(new MineFloor.Seen("minecraft:gravel", false, false, false, true)),
                "гравий на опоре держит духа — не трогаем"
                        + " Иначе тест decide_GravelOnRock_Keeps перестанет ловить эту поломку. При смене decide_GravelOnRock_Keeps проверь это утверждение.");
    }

    @Test
    void decide_OreInFloor_KeepsAsSupport() {
        assertEquals(
                MineFloor.Action.KEEP,
                MineFloor.decide(new MineFloor.Seen("minecraft:copper_ore", false, false, false, true)),
                "руда в полу и стенке — уже твёрдая опора спуска: её не перекладывают булыжником, иначе дух "
                        + "ищет недостижимую стоянку у стенки и проходка стоит"
                        + " При смене decide_OreInFloor_KeepsAsSupport проверь это утверждение.");
    }
}
