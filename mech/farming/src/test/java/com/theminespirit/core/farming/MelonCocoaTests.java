package com.theminespirit.core.farming;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.farming.api.CropKind;
import com.theminespirit.core.farming.api.HarvestMethod;
import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MelonCocoaTests {
    private static final BlockCoord FROM = new BlockCoord(-2, 1, -2);

    @Test
    void harvestMethod_MelonAndPumpkin_AreBreakFruit() {
        for (CropKind kind : List.of(CropKind.MELON, CropKind.PUMPKIN)) {
            assertEquals(
                    HarvestMethod.BREAK_FRUIT,
                    kind.harvestMethod(),
                    "Плод " + kind + " обязан собираться BREAK_FRUIT: стебель и пашня остаются, иначе дух сносит "
                            + "стебель и теряет грядку. При смене таблицы культур проверь "
                            + "planner_HarvestMelonWithFruit_IsEligible");
        }
    }

    @Test
    void attributes_MelonAndPumpkin_FruitDiffersFromStem() {
        assertEquals(
                "minecraft:melon",
                CropKind.MELON.fruitBlockId(),
                "Плод арбуза — блок minecraft:melon: по нему сбор ищет плод на соседней клетке, иначе "
                        + "BREAK_FRUIT бьёт стебель. При смене таблицы проверь "
                        + "planner_HarvestMelonWithoutFruit_IsIneligible");
        assertEquals(
                "minecraft:pumpkin",
                CropKind.PUMPKIN.fruitBlockId(),
                "Плод тыквы — блок minecraft:pumpkin: иначе сбор тыквы не находит плод и грядка перестаёт давать "
                        + "урожай. При смене таблицы проверь harvestMethod_MelonAndPumpkin_AreBreakFruit");
        assertNotEquals(
                CropKind.MELON.blockId(),
                CropKind.MELON.fruitBlockId(),
                "Блок стебля арбуза и блок плода обязаны отличаться: иначе сбор снесёт стебель вместо плода и "
                        + "грядка пропадёт. При смене CropKind.MELON проверь fruitBlockId выше в тесте");
        assertTrue(
                CropKind.MELON.hasFruit() && CropKind.PUMPKIN.hasFruit(),
                "У арбуза и тыквы обязан быть плод: иначе BREAK_FRUIT нечего ломать, и урожай теряется. При "
                        + "смене hasFruit проверь строку COCOA ниже в тесте");
        assertFalse(
                CropKind.COCOA.hasFruit(),
                "У какао нет отдельного блока плода: плод — сам блок какао на стволе, иначе сбор ищет "
                        + "несуществующий блок и стоит. При смене hasFruit проверь строку MELON выше в тесте");
        assertThrows(
                IllegalArgumentException.class,
                () -> CropKind.WHEAT.fruitBlockId(),
                "У пшеницы нет плода на соседней клетке: запрос обязан явно отвергаться, а не возвращать null, "
                        + "иначе NPE в сборе. При смене fruitBlockId проверь эту строку отказа");
    }

    @ParameterizedTest(name = "age {0} -> mature {1}")
    @CsvSource({"0,false", "1,false", "2,false", "3,false", "4,false", "5,false", "6,false", "7,true"})
    void isMature_MelonStem_OnlyAtMaxAge(int age, boolean mature) {
        CropState state = new CropState(CropKind.MELON, age);
        assertEquals(
                mature,
                state.isMature(),
                "Стебель арбуза зреет на age 7: при age=" + age + " mature=" + mature
                        + ", иначе сбор берёт недозрелый стебель без плода. При смене age-таблицы проверь "
                        + "planner_HarvestMelonWithFruit_IsEligible");
    }

    @Test
    void planner_HarvestMelonWithFruit_IsEligible() {
        MapFarmView farm = melonStemBed().fruit(new BlockCoord(0, 0, 0), new BlockCoord(1, 1, 0));

        FarmPlanner.Outcome outcome = FarmPlanner.next(
                FarmJob.HARVEST,
                CropKind.MELON,
                FROM,
                List.of(new BlockCoord(0, 0, 0)),
                farm,
                openField(),
                Hydration.of(Set.of()));

        assertTrue(
                outcome instanceof FarmPlanner.Next next && next.target().equals(new BlockCoord(0, 0, 0)),
                "Зрелый стебель с плодом на соседней клетке обязан быть целью сбора, иначе зрелый плод гниёт на "
                        + "грядке. При смене FarmPlanner.next проверь соседний тест без плода. Итог: " + outcome);
    }

    @Test
    void planner_HarvestMelonWithoutFruit_IsIneligible() {
        MapFarmView farm = melonStemBed();

        FarmPlanner.Outcome outcome = FarmPlanner.next(
                FarmJob.HARVEST,
                CropKind.MELON,
                FROM,
                List.of(new BlockCoord(0, 0, 0)),
                farm,
                openField(),
                Hydration.of(Set.of()));

        assertEquals(
                new FarmPlanner.Exhausted(1, 0, 0),
                outcome,
                "Зрелый стебель без плода не цель: плод ещё не вырос, ломать нечего, иначе дух снесёт живой "
                        + "стебель. При смене FarmPlanner.next проверь planner_HarvestMelonWithFruit_IsEligible. "
                        + "Итог: " + outcome);
    }

    @Test
    void planner_PlantMelonSoilNeighbor_IsEligible() {
        MapFarmView farm = new MapFarmView()
                .set(0, 0, 0, new FarmTile(Ground.FARMLAND, Above.AIR, Optional.empty()))
                .set(1, 0, 0, new FarmTile(Ground.DIRT, Above.AIR, Optional.empty()));

        FarmPlanner.Outcome outcome = FarmPlanner.next(
                FarmJob.PLANT,
                CropKind.MELON,
                FROM,
                List.of(new BlockCoord(0, 0, 0)),
                farm,
                openField(),
                Hydration.of(Set.of()));

        assertTrue(
                outcome instanceof FarmPlanner.Next,
                "Пашня со свободной соседней землёй под плод обязана быть целью посадки арбуза, иначе плоду некуда "
                        + "расти. При смене FarmPlanner.next проверь соседний тест без свободной клетки. Итог: "
                        + outcome);
    }

    @Test
    void planner_PlantMelonNoFruitSpace_IsIneligible() {
        MapFarmView farm = new MapFarmView().set(0, 0, 0, new FarmTile(Ground.FARMLAND, Above.AIR, Optional.empty()));

        FarmPlanner.Outcome outcome = FarmPlanner.next(
                FarmJob.PLANT,
                CropKind.MELON,
                FROM,
                List.of(new BlockCoord(0, 0, 0)),
                farm,
                openField(),
                Hydration.of(Set.of()));

        assertEquals(
                new FarmPlanner.Exhausted(1, 0, 0),
                outcome,
                "Без свободной соседней клетки плоду некуда расти: планировщик обязан оставить место и пропустить "
                        + "клетку, иначе посадка уходит в никуда. При смене FarmPlanner.next проверь соседний тест "
                        + "со свободной клеткой. Итог: " + outcome);
    }

    @Test
    void harvestMethod_Cocoa_IsBreak() {
        assertEquals(
                HarvestMethod.BREAK,
                CropKind.COCOA.harvestMethod(),
                "Какао собирается ломкой блока: боб падает, на том же боку сажается заново, иначе снятие боба "
                        + "теряет урожай. При смене таблицы проверь isMature_Cocoa_AgeTwoOnly");
    }

    @Test
    void attributes_Cocoa_GrowsOnJungleLogSide() {
        assertTrue(
                CropKind.COCOA.hasSupport(),
                "Какао растёт на боку ствола: без опоры посадка сядет в пустоту, иначе боб не вырастет и грядка "
                        + "стоит. При смене hasSupport проверь supportBlockId ниже в тесте");
        assertEquals(
                "minecraft:jungle_log",
                CropKind.COCOA.supportBlockId(),
                "Опора какао — ствол джунглей minecraft:jungle_log: другой ствол какао не принимает, иначе "
                        + "посадка идёт на дуб и пропадает. При смене таблицы проверь ofSupportBlock ниже");
        assertEquals(
                Optional.of(CropKind.COCOA),
                CropKind.ofSupportBlock("minecraft:jungle_log"),
                "Обратное сопоставление опоры обязано находить какао: по нему вид отличает ствол от земли, иначе "
                        + "опора не находится. При смене ofSupportBlock проверь строку oak ниже в тесте");
        assertEquals(
                Optional.empty(),
                CropKind.ofSupportBlock("minecraft:oak_log"),
                "Дубовый ствол какао не принимает: иначе дух сажает какао не на тот ствол и урожая нет. При "
                        + "смене ofSupportBlock проверь строку jungle выше в тесте");
        assertEquals(
                2,
                CropKind.COCOA.maxAge(),
                "Какао зреет на age 2: возраст 0–2, иначе спелый боб не собрать. При смене maxAge какао проверь "
                        + "isMature_Cocoa_AgeTwoOnly");
    }

    @ParameterizedTest(name = "age {0} -> mature {1}")
    @CsvSource({"0,false", "1,false", "2,true"})
    void isMature_Cocoa_AgeTwoOnly(int age, boolean mature) {
        CropState state = new CropState(CropKind.COCOA, age);
        assertEquals(
                mature,
                state.isMature(),
                "Какао зреет на age 2: при age=" + age + " mature=" + mature
                        + ", иначе дух снимает незрелый боб или пропускает спелый. При смене таблицы проверь "
                        + "eligible_CocoaOnLog_MatureIsHarvestable");
    }

    @Test
    void eligible_CocoaOnLog_MatureIsHarvestable() {
        FarmTile mature = new FarmTile(Ground.LOG, Above.CROP, Optional.of(new CropState(CropKind.COCOA, 2)));
        FarmTile young = new FarmTile(Ground.LOG, Above.CROP, Optional.of(new CropState(CropKind.COCOA, 1)));
        assertTrue(
                FarmJob.HARVEST.eligible(mature, CropKind.COCOA, false),
                "Спелое какао (age 2) на стволе обязано быть целью сбора, иначе спелый боб висит и пропадает. "
                        + "При смене FarmJob.HARVEST.eligible проверь соседний young в тесте");
        assertFalse(
                FarmJob.HARVEST.eligible(young, CropKind.COCOA, false),
                "Незрелое какао (age 1) не цель сбора: боб ещё растёт, иначе сбор снимает зелёный боб. При смене "
                        + "FarmJob.HARVEST.eligible проверь mature выше в тесте");
    }

    @Test
    void planner_PlantCocoaFreeSide_IsEligible() {
        MapFarmView farm = new MapFarmView()
                .set(0, 0, 0, new FarmTile(Ground.LOG, Above.AIR, Optional.empty()))
                .attachedSpot(new BlockCoord(0, 0, 0), new BlockCoord(1, 0, 0));

        FarmPlanner.Outcome outcome = FarmPlanner.next(
                FarmJob.PLANT,
                CropKind.COCOA,
                FROM,
                List.of(new BlockCoord(0, 0, 0)),
                farm,
                openField(),
                Hydration.of(Set.of()));

        assertTrue(
                outcome instanceof FarmPlanner.Next,
                "Ствол со свободным боком обязан быть целью посадки какао, иначе свободный бок пропадает. При "
                        + "смене FarmPlanner.next проверь соседний тест без свободного бока. Итог: " + outcome);
    }

    @Test
    void planner_PlantCocoaNoFreeSide_IsIneligible() {
        MapFarmView farm = new MapFarmView().set(0, 0, 0, new FarmTile(Ground.LOG, Above.AIR, Optional.empty()));

        FarmPlanner.Outcome outcome = FarmPlanner.next(
                FarmJob.PLANT,
                CropKind.COCOA,
                FROM,
                List.of(new BlockCoord(0, 0, 0)),
                farm,
                openField(),
                Hydration.of(Set.of()));

        assertEquals(
                new FarmPlanner.Exhausted(1, 0, 0),
                outcome,
                "Ствол без свободного бока не принимает какао: сажать некуда, иначе дух планирует посадку в "
                        + "занятый бок. При смене FarmPlanner.next проверь соседний тест со свободным боком. "
                        + "Итог: " + outcome);
    }

    private static MapFarmView melonStemBed() {
        return new MapFarmView()
                .set(0, 0, 0, new FarmTile(Ground.FARMLAND, Above.CROP, Optional.of(new CropState(CropKind.MELON, 7))));
    }

    private static CountingSafety openField() {
        return new CountingSafety().fillFloor(0, -6, -6, 10, 10);
    }
}
