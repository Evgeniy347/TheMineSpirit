package com.theminespirit.core.husbandry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.husbandry.api.Species;
import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class HerdSafetyTests {
    @Test
    void motherWatchRadius_ProductionConstant_MatchesAgreedTwoBlocks() {
        assertEquals(
                2.0,
                HerdSafety.MOTHER_WATCH_RADIUS,
                "Радиус опеки в production обязан быть 2 блока: иначе дальше детёныш мать не видит, и забой "
                        + "идёт при детёныше вплотную — стадо теряет приплод. При смене HerdSafety.MOTHER_WATCH_RADIUS "
                        + "проверь watchedByBaby_* и живой забой коровы с телёнком");
    }

    @Test
    void dangerReason_LavaFireWater_NamesCause() {
        assertEquals(
                Optional.of("в лаве"),
                HerdSafety.dangerReason(new HerdSafety.Hazards(true, false, false)),
                "Лава обязана откладывать действие и называть причину: иначе дух погонит животное в огонь, скот "
                        + "сгорит и загон опустеет. При смене HerdSafety.dangerReason проверь соседний "
                        + "dangerReason_AllDangersAtOnce_NamesLavaFirst");
        assertEquals(
                Optional.of("в огне"),
                HerdSafety.dangerReason(new HerdSafety.Hazards(false, true, false)),
                "Огонь обязан откладывать действие: иначе дух бьёт горящее животное, оно погибает от догорания "
                        + "и мясо теряется. При смене HerdSafety.dangerReason проверь порядок «лава, огонь, вода» "
                        + "на всех сочетаниях опасностей");
        assertEquals(
                Optional.of("в воде"),
                HerdSafety.dangerReason(new HerdSafety.Hazards(false, false, true)),
                "Вода обязана откладывать действие: мокрое животное не стригут и не бьют, иначе удар уходит "
                        + "впустую и цель убегает. При смене HerdSafety.dangerReason проверь живой забой овцы "
                        + "у кромки воды");
        assertEquals(
                Optional.empty(),
                HerdSafety.dangerReason(new HerdSafety.Hazards(false, false, false)),
                "Без опасности действие идёт: иначе дух будет стоять над сухим стадом и цикл фермы встанет на "
                        + "ровном месте. При смене HerdSafety.dangerReason проверь, что Hazards(false,false,false) "
                        + "даёт пустой ответ, а не «действие отложено»");
    }

    @Test
    void watchedByBaby_CalfWithinTwoBlocks_BlocksStrike() {
        AnimalRef mother = new AnimalRef("mother", Species.COW, false, false, false, new BlockCoord(0, 64, 0), true);
        AnimalRef calf = new AnimalRef("calf", Species.COW, true, false, false, new BlockCoord(1, 64, 1), true);
        AnimalRef stranger = new AnimalRef("hen", Species.CHICKEN, false, false, false, new BlockCoord(0, 64, 0), true);

        assertTrue(
                HerdSafety.watchedByBaby(mother, List.of(mother, calf, stranger)),
                "Удар при телёнке вида в двух блоках обязан откладываться: мать не бьют на глазах детёныша, "
                        + "иначе приплод пугается и разбегается по загону. При смене HerdSafety.watchedByBaby "
                        + "проверь границу в 2 блока и чужой вид рядом с матерью");
    }

    @Test
    void dangerReason_AllDangersAtOnce_NamesLavaFirst() {
        assertEquals(
                Optional.of("в лаве"),
                HerdSafety.dangerReason(new HerdSafety.Hazards(true, true, true)),
                "Лава главнее огня и воды: из лавы животное уже не вывести, её называют первой, иначе сообщение "
                        + "уводит от главной беды и починка идёт не туда. При смене порядка причин проверь "
                        + "Hazards(true,true,true) и проверки отдельных опасностей выше");
    }

    @Test
    void watchedByBaby_CalfExactlyTwoBlocksAway_BlocksStrike() {
        AnimalRef mother = new AnimalRef("mother", Species.COW, false, false, false, new BlockCoord(0, 64, 0), true);
        AnimalRef edge = new AnimalRef("calf", Species.COW, true, false, false, new BlockCoord(2, 64, 0), true);
        AnimalRef beyond = new AnimalRef("grown", Species.COW, true, false, false, new BlockCoord(2, 64, 1), true);

        assertTrue(
                HerdSafety.watchedByBaby(mother, List.of(mother, edge)),
                "Детёныш ровно в 2 блоках ещё опекается: граница включительна, иначе мать забьют на глазах "
                        + "детёныша у самой черты. При смене сравнения в HerdSafety.watchedByBaby проверь "
                        + "соседний beyond в этой же проверке: чуть дальше — уже не опека");
        assertFalse(
                HerdSafety.watchedByBaby(mother, List.of(mother, beyond)),
                "Детёныш дальше 2 блоков опеке не мешает: иначе забой остановится из-за дальнего молодняка, "
                        + "стадо не проредится и загон переполнится. При смене сравнения проверь пару edge/beyond "
                        + "у этой проверки: граница ровно между ними");
    }

    @Test
    void watchedByBaby_FarOrForeignBaby_AllowsStrike() {
        AnimalRef mother = new AnimalRef("mother", Species.COW, false, false, false, new BlockCoord(0, 64, 0), true);
        AnimalRef farCalf = new AnimalRef("calf", Species.COW, true, false, false, new BlockCoord(5, 64, 5), true);
        AnimalRef chick = new AnimalRef("chick", Species.CHICKEN, true, false, false, new BlockCoord(0, 64, 0), true);
        AnimalRef bull = new AnimalRef("bull", Species.COW, false, false, false, new BlockCoord(1, 64, 0), true);

        assertFalse(
                HerdSafety.watchedByBaby(mother, List.of(mother, farCalf)),
                "Далёкий телёнок удару не мешает: опека держится двумя блоками, иначе один дальний детёныш "
                        + "блокирует забой всего загона. При смене HerdSafety.watchedByBaby проверь границу "
                        + "2 блока и живой забой коровы с дальним телёнком");
        assertFalse(
                HerdSafety.watchedByBaby(mother, List.of(mother, chick)),
                "Детёныш чужого вида удару не мешает: цыплёнок корову матерью не считает, иначе курица рядом "
                        + "навсегда запретит забой коров. При смене проверки вида в watchedByBaby проверь пару "
                        + "«корова и цыплёнок» рядом");
        assertFalse(
                HerdSafety.watchedByBaby(mother, List.of(mother, bull)),
                "Взрослый рядом удару не мешает: опека касается только детёнышей, иначе бык в загоне навсегда "
                        + "остановит забой и план перестанет закрываться. При смене признака baby в "
                        + "watchedByBaby проверь взрослую особь рядом с целью");
        assertFalse(
                HerdSafety.watchedByBaby(mother, List.of(mother)),
                "Сама цель себя не опекает: иначе забой остановился бы навсегда, и дух стоял бы перед животным "
                        + "без действия до тайм-аута. При смене HerdSafety.watchedByBaby проверь список из одной "
                        + "цели — ответ обязан быть «удар можно»");
    }
}
