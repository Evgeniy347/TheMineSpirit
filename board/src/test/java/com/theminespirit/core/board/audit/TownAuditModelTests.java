package com.theminespirit.core.board.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.building.SchematicBlocks;
import com.theminespirit.core.observation.BlockCoord;
import org.junit.jupiter.api.Test;

class TownAuditModelTests {

    @Test
    void blockEquivalence_DirtPathUnderGround_CountsAsGround() {
        assertEquals(
                BlockEquivalence.Verdict.DONE,
                BlockEquivalence.check(Wanted.ground(63), "minecraft:dirt_path"),
                "20.11: тропа считалась отсутствием земли, и аудит выдавал подсыпку под каждый утоптанный шаг — "
                        + "GROUND обязан принимать весь земляной класс, иначе дух перекапывает улицу туда-сюда. "
                        + "При смене правила перепроверь список ground в BlockEquivalence.Rules.DEFAULT и "
                        + "BuildingAudit.streetProbe.");
        assertEquals(
                BlockEquivalence.Verdict.DIFFER,
                BlockEquivalence.check(Wanted.ground(63), "minecraft:cobblestone"),
                "Брусчатка в слое грунта — чужая работа, а не земля; без DIFFER клетка исчезнет из вида, иначе "
                        + "нарушение плана останется в поселении навсегда. При смене правила сверься с plan_breach "
                        + "в graph.d/world.json и перепроверь GraphFactInputs.planBreaches.");
    }

    @Test
    void blockEquivalence_StairsWrongHalf_CountsStateFixNotBreak() {
        Wanted wanted = Wanted.of("minecraft:oak_stairs[facing=north,half=top]");

        assertEquals(
                BlockEquivalence.Verdict.STATE,
                BlockEquivalence.check(wanted, "oak_stairs[facing=north,half=bottom]"),
                "Ступенька не той половины — правка состояния: иначе дух ломает стоящую лестницу, DiffItem "
                        + "остаётся без Action.FIX_STATE, а стройка гоняет блок по кругу до конца задания. "
                        + "При смене правила перепроверь SchematicSite.rightBlockWrongState и "
                        + "SchematicSite.repairState.");
        assertTrue(
                BlockEquivalence.stateOk(Wanted.of("oak_stairs[facing=north]"), "oak_stairs[facing=north,half=bottom]"),
                "Сверяются только свойства, названные в ожидании: иначе half у неназванной ступеньки станет "
                        + "вечным дефектом, и ни одна лестница не закроет группу. "
                        + "При смене правила перепроверь SchematicSite.sameSpecifiedState.");
    }

    @Test
    void blockEquivalence_AirWanted_VoidStandsButStoneDoesNot() {
        assertEquals(
                BlockEquivalence.Verdict.DONE,
                BlockEquivalence.check(Wanted.air(), "minecraft:water"),
                "Вода и снег — «место под блок»: иначе воздушный коридор улицы никогда не сойдёт чистым, и "
                        + "группа улицы будет висеть в граф-очереди вместо стройки склада. "
                        + "При смене правила перепроверь SchematicSite.freeForPlacing и BlockPlacer.");
        assertEquals(
                BlockEquivalence.Verdict.DIFFER,
                BlockEquivalence.check(Wanted.air(), "minecraft:cobblestone"),
                "Камень в воздушной клетке обязан дать BREAK: без предмета над улицей дух снова застрянет на "
                        + "столбиках — живые застревания 14–16.11 держались именно на этом правиле. "
                        + "При смене правила перепроверь группы улиц (block 7) и BuildGap.lifted.");
    }

    @Test
    void blockEquivalence_FloorClassAndSoftBlocks_WantNoWork() {
        assertEquals(
                BlockEquivalence.Verdict.DONE,
                BlockEquivalence.check(Wanted.block(SchematicBlocks.FLOOR), "minecraft:stone"),
                "Класс пола принимает камень и булыжник одним правилом: иначе пол схемы потребует ровно "
                        + "cobblestone, и дух будет выковыривать уже вымощенный склад. "
                        + "При смене правила перепроверь SchematicBlocks.FLOOR_CLASS и BuildGap.mine.");
        assertEquals(
                BlockEquivalence.Verdict.DONE,
                BlockEquivalence.check(Wanted.block("poppy"), "minecraft:air"),
                "Мягкий блок не ремонтируется: иначе цветы и саженцы превращаются в бесконечную посадку, а "
                        + "садовые группы никогда не закроются. "
                        + "При смене правила синхронизируй Rules.soft с softBlocks в "
                        + "data/theminespirit/repair/repair.json и перепроверь этот тест.");
    }

    @Test
    void blockEquivalence_WoodSpeciesSameRole_AcceptsAndChecksState() {
        Wanted wanted = Wanted.of("minecraft:oak_stairs[facing=north,half=bottom]");

        assertEquals(
                BlockEquivalence.Verdict.DONE,
                BlockEquivalence.check(wanted, "minecraft:spruce_stairs[facing=north,half=bottom]"),
                "Порода с той же ролью принимается: иначе выбранная игроком ель заставит духа ломать дубовую"
                        + " лестницу и ставить её обратно до конца задания. При смене правила перепроверь"
                        + " WoodSpecies.sameRole и BlockEquivalence.block.");
        assertEquals(
                BlockEquivalence.Verdict.STATE,
                BlockEquivalence.check(wanted, "minecraft:spruce_stairs[facing=south,half=bottom]"),
                "У другой породы состояние всё равно значимо: иначе лестница развернётся не туда, а DiffItem"
                        + " останется без FIX_STATE. При смене правила перепроверь stateOk и SchematicSite.repairState.");
    }

    @Test
    void expectation_ForeignAccessAndSource_StayVisibleAndRoundTrip() {
        Expectation tile = new Expectation(
                new BlockCoord(-100, 63, 40),
                Wanted.block("cobblestone"),
                SourceRef.street("s-7"),
                Access.foreign("player:domovoy"));

        assertEquals(
                "street:s-7",
                tile.source().text(),
                "Источник печатается ключом группы: по нему строится обратный индекс клетка→группа, иначе второй "
                        + "формат расслоит индекс, и инвалидация не сможет найти группу. "
                        + "При смене формата перепроверь Group.key() и SourceRef.parse.");
        assertEquals(
                SourceRef.street("s-7"),
                SourceRef.parse(tile.source().text()),
                "Разбор текста источника обратен печати: иначе данные плана и журнал заговорят о разных "
                        + "объектах, и сводка стратегу соврёт. "
                        + "При смене формата перепроверь парсер plan-кодека (TownPlanCodec).");
        assertFalse(
                tile.access().touchable(),
                "Чужая клетка не трогается духом: иначе аудит сломает постройку игрока, а право аренды тут ни "
                        + "при чём. При смене прав перепроверь Access.temp для аренды и LeaseData (block 5).");
        assertThrows(
                IllegalArgumentException.class,
                () -> Access.foreign(""),
                "Чужое без владельца невозможно: иначе FOREIGN превратится в невидимость блока, а ТЗ требует "
                        + "держать чужое в виде и не ломать. При смене прав перепроверь, что разрешения не "
                        + "уходят в слой фактов.");
    }
}
