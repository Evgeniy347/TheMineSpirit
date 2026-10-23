package com.theminespirit.core.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MineFloorTests {
    @Test
    void decide_Stone_Keeps() {
        assertEquals(
                MineFloor.Action.KEEP,
                MineFloor.decide(new MineFloor.Seen("minecraft:stone", false, false, false, true)),
                "камень — годный пол @floor: его не трогают, булыжник не тратится");
    }

    @Test
    void decide_Water_Places() {
        assertEquals(
                MineFloor.Action.PLACE,
                MineFloor.decide(new MineFloor.Seen("minecraft:water", false, true, true, false)),
                "вода в полу — ставится булыжник до шага вперёд, иначе дух ступит в воду");
    }

    @Test
    void decide_Air_Places() {
        assertEquals(
                MineFloor.Action.PLACE,
                MineFloor.decide(new MineFloor.Seen("minecraft:air", true, false, true, false)),
                "пустота под ступенью (пещера) — ставится булыжник, иначе дух провалится");
    }

    @Test
    void decide_GravelOverVoid_Replaces() {
        assertEquals(
                MineFloor.Action.REPLACE,
                MineFloor.decide(new MineFloor.Seen("minecraft:gravel", false, false, false, false)),
                "гравий без опоры обвалится из-под ног — заменить булыжником");
    }

    @Test
    void decide_GravelOnRock_Keeps() {
        assertEquals(
                MineFloor.Action.KEEP,
                MineFloor.decide(new MineFloor.Seen("minecraft:gravel", false, false, false, true)),
                "гравий на опоре держит духа — не трогаем");
    }

    @Test
    void decide_OreInFloor_GoesToOreRule() {
        assertEquals(
                MineFloor.Action.ORE,
                MineFloor.decide(new MineFloor.Seen("minecraft:diamond_ore", false, false, false, true)),
                "руда в полу решается правилом руды: обычную добыть, на удачу — оставить");
    }
}
