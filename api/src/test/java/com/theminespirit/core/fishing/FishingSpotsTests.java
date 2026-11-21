package com.theminespirit.core.fishing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FishingSpotsTests {
    private static final FishingData DATA = new FishingData(2, 1);

    @Test
    void find_TwoByTwoOpenWater_ChoosesShoreStandAndWaterCell() {
        WaterView view = view(columns(2, 2, 64, 1, 2), Set.of(new BlockCoord(1, 64, 2), new BlockCoord(2, 64, 1)));

        Optional<FishingSpots.Spot> found = FishingSpots.find(new BlockCoord(0, 64, 0), view, DATA);

        assertTrue(
                found.isPresent(),
                "Рядом пруд 2x2 глубиной 1 — место для ловли обязано найтись: без этого дух не рыбачит"
                        + " При смене find_TwoByTwoOpenWater_ChoosesShoreStandAndWaterCell проверь это утверждение.");
        assertEquals(
                new BlockCoord(1, 64, 2),
                found.orElseThrow().stand(),
                "Из равноблизких стоянок берётся младшая по порядку координат: выбор обязан быть детерминирован"
                        + " Иначе тест find_TwoByTwoOpenWater_ChoosesShoreStandAndWaterCell перестанет ловить эту поломку. При смене find_TwoByTwoOpenWater_ChoosesShoreStandAndWaterCell проверь это утверждение.");
        assertEquals(
                new BlockCoord(2, 64, 2),
                found.orElseThrow().water(),
                "Цель заброса — ближайшая к стоянке клетка воды: удочка летит в пруд, а не мимо"
                        + " Иначе тест find_TwoByTwoOpenWater_ChoosesShoreStandAndWaterCell перестанет ловить эту поломку. При смене find_TwoByTwoOpenWater_ChoosesShoreStandAndWaterCell проверь это утверждение.");
    }

    @Test
    void find_SingleRowOfWater_Refuses() {
        List<WaterView.Column> row = List.of(
                new WaterView.Column(2, 2, 64, 1),
                new WaterView.Column(3, 2, 64, 1),
                new WaterView.Column(4, 2, 64, 1));
        WaterView view = view(row, Set.of(new BlockCoord(1, 64, 2)));

        assertTrue(
                FishingSpots.find(new BlockCoord(0, 64, 0), view, DATA).isEmpty(),
                "Полоса воды шириной в один блок — не 2x2: забрасывать некуда, находка обязана отказать"
                        + " Иначе тест find_SingleRowOfWater_Refuses перестанет ловить эту поломку. При смене find_SingleRowOfWater_Refuses проверь это утверждение.");
    }

    @Test
    void find_DepthBelowData_Refuses() {
        WaterView view = view(columns(2, 2, 64, 1, 2), Set.of(new BlockCoord(1, 64, 2)));

        assertTrue(
                FishingSpots.find(new BlockCoord(0, 64, 0), view, new FishingData(2, 2))
                        .isEmpty(),
                "Вода глубиной 1 при требовании 2 обязана быть отвергнута: порог глубины приходит из данных"
                        + " Иначе тест find_DepthBelowData_Refuses перестанет ловить эту поломку. При смене find_DepthBelowData_Refuses проверь это утверждение.");
    }

    @Test
    void find_WidthBelowData_Refuses() {
        WaterView view = view(columns(2, 2, 64, 1, 2), Set.of(new BlockCoord(1, 64, 2)));

        assertTrue(
                FishingSpots.find(new BlockCoord(0, 64, 0), view, new FishingData(3, 1))
                        .isEmpty(),
                "Пятно 2x2 при требовании 3x3 обязано быть отвергнуто: порог площади приходит из данных"
                        + " Иначе тест find_WidthBelowData_Refuses перестанет ловить эту поломку. При смене find_WidthBelowData_Refuses проверь это утверждение.");
    }

    @Test
    void find_SurfaceStep_Refuses() {
        List<WaterView.Column> stepped = List.of(
                new WaterView.Column(2, 2, 64, 1), new WaterView.Column(3, 2, 64, 1),
                new WaterView.Column(2, 3, 64, 1), new WaterView.Column(3, 3, 65, 1));
        WaterView view = view(stepped, Set.of(new BlockCoord(1, 64, 2)));

        assertTrue(
                FishingSpots.find(new BlockCoord(0, 64, 0), view, DATA).isEmpty(),
                "Ступенька в поверхности воды ломает ровное пятно 2x2: иначе удочка забрасывается в перепад"
                        + " При смене find_SurfaceStep_Refuses проверь это утверждение.");
    }

    @Test
    void find_TwoPonds_ChoosesNearerShore() {
        List<WaterView.Column> water = new ArrayList<>(columns(2, 2, 64, 1, 2));
        water.addAll(columns(10, 10, 64, 1, 2));
        WaterView view = view(water, Set.of(new BlockCoord(1, 64, 2), new BlockCoord(9, 64, 10)));

        FishingSpots.Spot spot =
                FishingSpots.find(new BlockCoord(0, 64, 0), view, DATA).orElseThrow();

        assertEquals(
                new BlockCoord(1, 64, 2),
                spot.stand(),
                "Из двух прудов берётся ближний берег: дух не идёт через полкарты за той же рыбой"
                        + " Иначе тест find_TwoPonds_ChoosesNearerShore перестанет ловить эту поломку. При смене find_TwoPonds_ChoosesNearerShore проверь это утверждение.");
        assertEquals(
                new BlockCoord(2, 64, 2),
                spot.water(),
                "Заброс идёт в ближний пруд, а не в дальний: цель обязана лежать у выбранной стоянки"
                        + " Иначе тест find_TwoPonds_ChoosesNearerShore перестанет ловить эту поломку. При смене find_TwoPonds_ChoosesNearerShore проверь это утверждение.");
    }

    @Test
    void find_NoStandableShore_Refuses() {
        WaterView view = view(columns(2, 2, 64, 1, 2), Set.of());

        assertTrue(
                FishingSpots.find(new BlockCoord(0, 64, 0), view, DATA).isEmpty(),
                "Пруд без пригодного берега не годится: стоять духу негде, находка обязана отказать"
                        + " Иначе тест find_NoStandableShore_Refuses перестанет ловить эту поломку. При смене find_NoStandableShore_Refuses проверь это утверждение.");
    }

    @Test
    void find_ThreeByThreeWater_QualifiesAsMinimumTwoByTwo() {
        WaterView view = view(columns(2, 2, 64, 1, 3), Set.of(new BlockCoord(1, 64, 2)));

        assertTrue(
                FishingSpots.find(new BlockCoord(0, 64, 0), view, DATA).isPresent(),
                "Пруд 3x3 содержит пятно 2x2 — требование «минимум 2x2» выполнено, находка обязана сработать"
                        + " Иначе тест find_ThreeByThreeWater_QualifiesAsMinimumTwoByTwo перестанет ловить эту поломку. При смене find_ThreeByThreeWater_QualifiesAsMinimumTwoByTwo проверь это утверждение.");
    }

    @Test
    void find_NoWater_Refuses() {
        WaterView view = view(List.of(), Set.of(new BlockCoord(1, 64, 2)));

        assertTrue(
                FishingSpots.find(new BlockCoord(0, 64, 0), view, DATA).isEmpty(),
                "Без открытой воды находка обязана отказать: дух не рыбачит в воздухе"
                        + " Иначе тест find_NoWater_Refuses перестанет ловить эту поломку. При смене find_NoWater_Refuses проверь это утверждение.");
    }

    @Test
    void findWorkPoint_FiveByFiveDepthTwo_ChoosesShoreStand() {
        WaterView view = view(columns(2, 2, 64, 2, 5), Set.of(new BlockCoord(1, 64, 2)));

        Optional<FishingSpots.Spot> found =
                FishingSpots.findWorkPoint(new BlockCoord(0, 64, 0), view, DATA, FishingSpots.SpotFilter.ALL);

        assertTrue(
                found.isPresent(),
                "Пятно 5x5 глубиной 2 — рабочая точка у воды: здесь дух рыбачит постоянно"
                        + " Иначе тест findWorkPoint_FiveByFiveDepthTwo_ChoosesShoreStand перестанет ловить эту поломку. При смене findWorkPoint_FiveByFiveDepthTwo_ChoosesShoreStand проверь это утверждение.");
        assertEquals(
                new BlockCoord(1, 64, 2),
                found.orElseThrow().stand(),
                "Рабочая стоянка выбирается у берега ближайшего пятна воды"
                        + " Иначе тест findWorkPoint_FiveByFiveDepthTwo_ChoosesShoreStand перестанет ловить эту поломку. При смене findWorkPoint_FiveByFiveDepthTwo_ChoosesShoreStand проверь это утверждение.");
    }

    @Test
    void findWorkPoint_ShallowFiveByFive_Refuses() {
        WaterView view = view(columns(2, 2, 64, 1, 5), Set.of(new BlockCoord(1, 64, 2)));

        assertTrue(
                FishingSpots.findWorkPoint(new BlockCoord(0, 64, 0), view, DATA, FishingSpots.SpotFilter.ALL)
                        .isEmpty(),
                "Пятно 5x5 глубиной 1 не годится под рабочую точку: работа у воды требует глубины 2 из данных"
                        + " Иначе тест findWorkPoint_ShallowFiveByFive_Refuses перестанет ловить эту поломку. При смене findWorkPoint_ShallowFiveByFive_Refuses проверь это утверждение.");
    }

    @Test
    void findWorkPoint_FilterRejectsNearShore_PicksFarOne() {
        List<WaterView.Column> water = new ArrayList<>(columns(2, 2, 64, 2, 5));
        water.addAll(columns(30, 30, 64, 2, 5));
        WaterView view = view(water, Set.of(new BlockCoord(1, 64, 2), new BlockCoord(29, 64, 30)));

        FishingSpots.Spot spot = FishingSpots.findWorkPoint(
                        new BlockCoord(0, 64, 0), view, DATA, (stand, cell) -> stand.x() >= 29)
                .orElseThrow();

        assertEquals(
                new BlockCoord(29, 64, 30),
                spot.stand(),
                "Отвергнутая фильтром ближняя стоянка не выбирается: рабочая точка идёт от построек подальше"
                        + " Иначе тест findWorkPoint_FilterRejectsNearShore_PicksFarOne перестанет ловить эту поломку. При смене findWorkPoint_FilterRejectsNearShore_PicksFarOne проверь это утверждение.");
    }

    @Test
    void findWorkPoint_AllShoreRejected_Refuses() {
        WaterView view = view(columns(2, 2, 64, 2, 5), Set.of(new BlockCoord(1, 64, 2)));

        assertTrue(
                FishingSpots.findWorkPoint(new BlockCoord(0, 64, 0), view, DATA, (stand, cell) -> false)
                        .isEmpty(),
                "Если вся вода занята постройками, рабочая точка не находится: дух не рыбачит у чужого дома"
                        + " Иначе тест findWorkPoint_AllShoreRejected_Refuses перестанет ловить эту поломку. При смене findWorkPoint_AllShoreRejected_Refuses проверь это утверждение.");
    }

    private static WaterView view(List<WaterView.Column> columns, Set<BlockCoord> stands) {
        return new WaterView() {
            @Override
            public Collection<Column> columns() {
                return columns;
            }

            @Override
            public boolean canStand(BlockCoord feet) {
                return stands.contains(feet);
            }
        };
    }

    private static List<WaterView.Column> columns(int x0, int z0, int y, int depth, int side) {
        List<WaterView.Column> result = new ArrayList<>();
        for (int dx = 0; dx < side; dx++) {
            for (int dz = 0; dz < side; dz++) {
                result.add(new WaterView.Column(x0 + dx, z0 + dz, y, depth));
            }
        }
        return result;
    }
}
