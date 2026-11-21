package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.theminespirit.core.building.api.BuildBox;
import com.theminespirit.core.building.api.BuildRotation;
import com.theminespirit.core.script.api.BuildSchematicParams;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BuildingParamsTests {
    @Test
    void placeBlock_AllParamsPresent_ReturnsParams() {
        PlaceBlockParams params = PlaceBlockParams.from(Map.of("block", "cobblestone", "x", 1, "y", 64, "z", 2));

        assertEquals(
                new PlaceBlockParams("cobblestone", 1, 64, 2),
                params,
                "place_block обязан сохранить блок и точку: по ним адаптер ставит блок"
                        + " Иначе тест placeBlock_AllParamsPresent_ReturnsParams перестанет ловить эту поломку. При смене placeBlock_AllParamsPresent_ReturnsParams проверь это утверждение.");
    }

    @Test
    void placeBlock_BlankBlock_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> PlaceBlockParams.from(Map.of("block", "  ", "x", 1, "y", 64, "z", 2)),
                "Пустой блок обязан отвергаться: по пробелам блок не найти"
                        + " Иначе тест placeBlock_BlankBlock_IsRejected перестанет ловить эту поломку. При смене placeBlock_BlankBlock_IsRejected проверь это утверждение.");
    }

    @Test
    void placeBlock_CoordOutsideWorld_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> PlaceBlockParams.from(Map.of("block", "stone", "x", 1, "y", 400, "z", 2)),
                "Координата вне мира обязана отвергаться: точки вне высоты мира не существует"
                        + " Иначе тест placeBlock_CoordOutsideWorld_IsRejected перестанет ловить эту поломку. При смене placeBlock_CoordOutsideWorld_IsRejected проверь это утверждение.");
    }

    @Test
    void fill_ReversedCorners_AcceptsSameVolume() {
        FillParams forward =
                FillParams.from(Map.of("block", "stone", "x1", 0, "y1", 64, "z1", 0, "x2", 1, "y2", 65, "z2", 1));
        FillParams reversed =
                FillParams.from(Map.of("block", "stone", "x1", 1, "y1", 65, "z1", 1, "x2", 0, "y2", 64, "z2", 0));

        assertEquals(
                8L,
                BuildBox.of(0, 64, 0, 1, 65, 1).volume(),
                "Проверка обязана идти через объём коробки: иначе лимит считает не то число"
                        + " При смене fill_ReversedCorners_AcceptsSameVolume проверь это утверждение.");
        assertEquals(
                forward.block(),
                reversed.block(),
                "Углы в любом порядке обязаны приниматься: иначе стратег вынужден сортировать углы сам"
                        + " При смене fill_ReversedCorners_AcceptsSameVolume проверь это утверждение.");
    }

    @Test
    void fill_VolumeAboveLimit_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> FillParams.from(
                        Map.of("block", "stone", "x1", 0, "y1", 0, "z1", 0, "x2", 63, "y2", 63, "z2", 63)),
                "Объём сверх предела обязан отвергаться до первого блока: иначе стройка начинается и обрывается на середине"
                        + " При смене fill_VolumeAboveLimit_IsRejected проверь это утверждение.");
    }

    @Test
    void clearArea_VolumeAboveLimit_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ClearAreaParams.from(Map.of("x1", 0, "y1", 0, "z1", 0, "x2", 63, "y2", 63, "z2", 63)),
                "Объём clear_area сверх предела обязан отвергаться: иначе дух копает часами"
                        + " При смене clearArea_VolumeAboveLimit_IsRejected проверь это утверждение.");
    }

    @Test
    void clearArea_DemolishFlag_DefaultsFalseAndParsesTrue() {
        ClearAreaParams regular = ClearAreaParams.from(Map.of("x1", 0, "y1", 64, "z1", 0, "x2", 1, "y2", 65, "z2", 1));
        ClearAreaParams demolish =
                ClearAreaParams.from(Map.of("x1", 0, "y1", 64, "z1", 0, "x2", 1, "y2", 65, "z2", 1, "demolish", true));

        assertEquals(
                false,
                regular.demolish(),
                "Без флага разбор идёт обычной защитой: чужую постройку дух не ломает"
                        + " При смене clearArea_DemolishFlag_DefaultsFalseAndParsesTrue проверь это утверждение.");
        assertEquals(
                true,
                demolish.demolish(),
                "С демолишем снос своей постройки проходит защиту: забор и калитка иначе " + "остаются стоять"
                        + " При смене clearArea_DemolishFlag_DefaultsFalseAndParsesTrue проверь это утверждение.");
    }

    @Test
    void clearArea_KeepGroundFlag_DefaultsFalseAndParsesTrue() {
        ClearAreaParams regular = ClearAreaParams.from(Map.of("x1", 0, "y1", 64, "z1", 0, "x2", 1, "y2", 65, "z2", 1));
        ClearAreaParams pit = ClearAreaParams.from(
                Map.of("x1", 0, "y1", 64, "z1", 0, "x2", 1, "y2", 65, "z2", 1, "keep_ground", true));

        assertEquals(
                false,
                regular.keepGround(),
                "Без флага расчистка снимает и землю: так срезается холм"
                        + " Иначе тест clearArea_KeepGroundFlag_DefaultsFalseAndParsesTrue перестанет ловить эту поломку. При смене clearArea_KeepGroundFlag_DefaultsFalseAndParsesTrue проверь это утверждение.");
        assertEquals(
                true,
                pit.keepGround(),
                "keep_ground=true — расчистка ямы подсыпки: землю и камень в ней не трогает, иначе снимает уже "
                        + "засыпанное (INC-77)"
                        + " При смене clearArea_KeepGroundFlag_DefaultsFalseAndParsesTrue проверь это утверждение.");
    }

    @Test
    void digArea_SizeZero_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> DigAreaParams.from(Map.of("x", 0, "y", 64, "z", 0, "size", 0)),
                "Сторона 0 обязана отвергаться: куб нулевого размера копать нечего"
                        + " Иначе тест digArea_SizeZero_IsRejected перестанет ловить эту поломку. При смене digArea_SizeZero_IsRejected проверь это утверждение.");
    }

    @Test
    void digArea_SizeAboveMax_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> DigAreaParams.from(Map.of("x", 0, "y", 64, "z", 0, "size", 17)),
                "Сторона больше 16 обязана отвергаться: иначе dig_area роет область сверх лимита объёма"
                        + " При смене digArea_SizeAboveMax_IsRejected проверь это утверждение.");
    }

    @Test
    void digArea_ValidCube_ReturnsParams() {
        DigAreaParams params = DigAreaParams.from(Map.of("x", 0, "y", 64, "z", 0, "size", 2));

        assertEquals(
                new DigAreaParams(0, 64, 0, 2),
                params,
                "Куб 2 обязан приниматься: по нему адаптер строит коробку от минимального угла"
                        + " Иначе тест digArea_ValidCube_ReturnsParams перестанет ловить эту поломку. При смене digArea_ValidCube_ReturnsParams проверь это утверждение.");
    }

    @Test
    void buildSchematic_BlankFile_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> BuildSchematicParams.from(Map.of("file", "", "x", 0, "y", 64, "z", 0)),
                "Пустой файл схемы обязан отвергаться: без имени схему не найти"
                        + " Иначе тест buildSchematic_BlankFile_IsRejected перестанет ловить эту поломку. При смене buildSchematic_BlankFile_IsRejected проверь это утверждение.");
    }

    @Test
    void buildSchematic_ValidFile_ReturnsParams() {
        BuildSchematicParams params = BuildSchematicParams.from(Map.of("file", "house", "x", 0, "y", 64, "z", 0));

        assertEquals(
                new BuildSchematicParams("house", 0, 64, 0, BuildRotation.NONE),
                params,
                "Схема обязана сохранить файл и якорь: по ним адаптер грузит и ставит схему"
                        + " Иначе тест buildSchematic_ValidFile_ReturnsParams перестанет ловить эту поломку. При смене buildSchematic_ValidFile_ReturnsParams проверь это утверждение.");
    }

    @Test
    void buildSchematic_PreviousScheme_ComesFromTask() {
        BuildSchematicParams params = BuildSchematicParams.from(Map.of(
                "file", "stone_house",
                "x", 0,
                "y", 64,
                "z", 0,
                "previous", "hut_1"));

        assertEquals(
                "hut_1",
                params.previous(),
                "Старая схема едет в задании стройки: по её клеткам снимаются лишние блоки уровня, "
                        + "а не угадываются метками духа в габарите"
                        + " Иначе тест buildSchematic_PreviousScheme_ComesFromTask перестанет ловить эту поломку. При смене buildSchematic_PreviousScheme_ComesFromTask проверь это утверждение.");
    }

    @Test
    void buildSchematic_BlankPrevious_TreatedAsAbsent() {
        BuildSchematicParams params =
                BuildSchematicParams.from(Map.of("file", "stone_house", "x", 0, "y", 64, "z", 0, "previous", " "));

        assertEquals(
                null,
                params.previous(),
                "Пустая старая схема — то же, что отсутствие: умолчание каталога не должно ронять стройку"
                        + " Иначе тест buildSchematic_BlankPrevious_TreatedAsAbsent перестанет ловить эту поломку. При смене buildSchematic_BlankPrevious_TreatedAsAbsent проверь это утверждение.");
    }

    @Test
    void buildSchematic_RotationName_ParsesQuarterTurns() {
        BuildSchematicParams params = BuildSchematicParams.from(
                Map.of("file", "house", "x", 0, "y", 64, "z", 0, "rotation", "clockwise_90", "segments", 0));

        assertEquals(
                BuildRotation.CLOCKWISE_90,
                params.rotation(),
                "Поворот схемы обязан читаться из параметра: без него постройка встанет не той стороной"
                        + " Иначе тест buildSchematic_RotationName_ParsesQuarterTurns перестанет ловить эту поломку. При смене buildSchematic_RotationName_ParsesQuarterTurns проверь это утверждение.");
    }
}
