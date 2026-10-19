package com.theminespirit.core.husbandry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class HerdSafetyTests {
    @Test
    void motherWatchRadius_ProductionConstant_MatchesAgreedTwoBlocks() {
        assertEquals(2.0, HerdSafety.MOTHER_WATCH_RADIUS,
                "Радиус опеки в production обязан быть 2 блока: дальше детёныш мать не видит");
    }

    @Test
    void dangerReason_LavaFireWater_NamesCause() {
        assertEquals(Optional.of("в лаве"), HerdSafety.dangerReason(new HerdSafety.Hazards(true, false, false)),
                "Лава обязана откладывать действие: иначе дух погонит животное в огонь");
        assertEquals(Optional.of("в огне"), HerdSafety.dangerReason(new HerdSafety.Hazards(false, true, false)),
                "Огонь обязан откладывать действие");
        assertEquals(Optional.of("в воде"), HerdSafety.dangerReason(new HerdSafety.Hazards(false, false, true)),
                "Вода обязана откладывать действие: мокрое животное не стригут и не бьют");
        assertEquals(Optional.empty(), HerdSafety.dangerReason(new HerdSafety.Hazards(false, false, false)),
                "Без опасности действие идёт: иначе дух будет стоять над сухим стадом");
    }

    @Test
    void watchedByBaby_CalfWithinTwoBlocks_BlocksStrike() {
        AnimalRef mother = new AnimalRef("mother", Species.COW, false, false, false, new BlockCoord(0, 64, 0), true);
        AnimalRef calf = new AnimalRef("calf", Species.COW, true, false, false, new BlockCoord(1, 64, 1), true);
        AnimalRef stranger = new AnimalRef("hen", Species.CHICKEN, false, false, false, new BlockCoord(0, 64, 0), true);

        assertTrue(HerdSafety.watchedByBaby(mother, List.of(mother, calf, stranger)),
                "Удар при телёнке вида в двух блоках обязан откладываться: мать не бьют на глазах детёныша");
    }

    @Test
    void dangerReason_AllDangersAtOnce_NamesLavaFirst() {
        assertEquals(Optional.of("в лаве"), HerdSafety.dangerReason(new HerdSafety.Hazards(true, true, true)),
                "Лава главнее огня и воды: из лавы животное уже не вывести, её называют первой");
    }

    @Test
    void watchedByBaby_CalfExactlyTwoBlocksAway_BlocksStrike() {
        AnimalRef mother = new AnimalRef("mother", Species.COW, false, false, false, new BlockCoord(0, 64, 0), true);
        AnimalRef edge = new AnimalRef("calf", Species.COW, true, false, false, new BlockCoord(2, 64, 0), true);
        AnimalRef beyond = new AnimalRef("grown", Species.COW, true, false, false, new BlockCoord(2, 64, 1), true);

        assertTrue(HerdSafety.watchedByBaby(mother, List.of(mother, edge)),
                "Детёныш ровно в 2 блоках ещё опекается: граница опеки включительна, иначе мать забьют на глазах");
        assertFalse(HerdSafety.watchedByBaby(mother, List.of(mother, beyond)),
                "Детёныш дальше 2 блоков опеке не мешает: иначе забой остановится из-за дальнего молодняка");
    }

    @Test
    void watchedByBaby_FarOrForeignBaby_AllowsStrike() {
        AnimalRef mother = new AnimalRef("mother", Species.COW, false, false, false, new BlockCoord(0, 64, 0), true);
        AnimalRef farCalf = new AnimalRef("calf", Species.COW, true, false, false, new BlockCoord(5, 64, 5), true);
        AnimalRef chick = new AnimalRef("chick", Species.CHICKEN, true, false, false, new BlockCoord(0, 64, 0), true);
        AnimalRef bull = new AnimalRef("bull", Species.COW, false, false, false, new BlockCoord(1, 64, 0), true);

        assertFalse(HerdSafety.watchedByBaby(mother, List.of(mother, farCalf)),
                "Далёкий телёнок удару не мешает: опека держится двумя блоками");
        assertFalse(HerdSafety.watchedByBaby(mother, List.of(mother, chick)),
                "Детёныш чужого вида удару не мешает: цыплёнок корову матерью не считает");
        assertFalse(HerdSafety.watchedByBaby(mother, List.of(mother, bull)),
                "Взрослый рядом удару не мешает: опека касается только детёнышей");
        assertFalse(HerdSafety.watchedByBaby(mother, List.of(mother)),
                "Сама цель себя не опекает: иначе забой остановился бы навсегда");
    }
}
