package com.theminespirit.core.farming;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CropKindTests {
    static Stream<Arguments> kindsAndBlocks() {
        return Stream.of(
                Arguments.of(CropKind.WHEAT, "minecraft:wheat", 7, "wheat_seeds", "wheat"),
                Arguments.of(CropKind.CARROT, "minecraft:carrots", 7, "carrot", "carrot"),
                Arguments.of(CropKind.POTATO, "minecraft:potatoes", 7, "potato", "potato"),
                Arguments.of(CropKind.BEETROOT, "minecraft:beetroots", 3, "beetroot_seeds", "beetroot"),
                Arguments.of(CropKind.parse("sweet_berry_bush"), "minecraft:sweet_berry_bush", 3, "sweet_berries",
                        "sweet_berries"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("kindsAndBlocks")
    void attributes_EveryPlantableKind_MatchesVanillaTable(CropKind kind, String blockId, int maxAge,
            String plantItem, String produceItem) {
        assertEquals(blockId, kind.blockId(),
                "Блок вида " + kind + " обязан совпадать с ванильным id: по нему поиск отличает посев от земли");
        assertEquals(maxAge, kind.maxAge(),
                "Максимальный возраст вида " + kind + " обязан совпадать с ванилью (пшеница 7, свёкла 3): "
                        + "по нему harvest отличает зрелый посев от растущего");
        assertEquals(plantItem, kind.plantItem(),
                "Посадочный предмет вида " + kind + " тратится из инвентаря: у моркови и картофеля это сам урожай");
        assertEquals(produceItem, kind.produceItem(),
                "Урожай вида " + kind + " подбирается фильтром DropCollector: без него сбор теряет предметы");
    }

    @Test
    void names_AllKinds_AreLowercaseInDeclarationOrder() {
        assertEquals(List.of("any", "wheat", "carrot", "potato", "beetroot", "sweet_berry_bush"),
                CropKind.names(),
                "Список значений для каталога обязан идти в порядке объявления и нижним регистром: "
                        + "по нему валидатор плана показывает допустимые виды культур ИИ");
    }

    @Test
    void plantableNames_AnyKind_IsExcluded() {
        assertEquals(List.of("wheat", "carrot", "potato", "beetroot", "sweet_berry_bush"),
                CropKind.plantableNames(),
                "plant_crop требует конкретный вид: any в списке позволил бы план «посади что-нибудь», "
                        + "а дух не знает, какие семена тратить");
    }

    @Test
    void parse_KnownKindName_ReturnsKind() {
        assertEquals(CropKind.WHEAT, CropKind.parse("wheat"),
                "parse обязан принимать имя вида из каталога: иначе план с crop=wheat не запускается");
    }

    @Test
    void parse_KindNameWithCaseAndSpaces_ReturnsKind() {
        assertEquals(CropKind.BEETROOT, CropKind.parse("  Beetroot "),
                "parse обязан прощать регистр и пробелы: иначе опечатка в регистре отвергает верный вид культуры");
    }

    @Test
    void parse_UnknownKind_ThrowsWithAllowedList() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> CropKind.parse("melon"),
                "Неизвестный вид культуры обязан отвергаться: иначе скрипт ищет посевы, которых не знает, "
                        + "и молча завершается без работы");

        assertTrue(error.getMessage().contains("melon"),
                "Отказ обязан называть неизвестное значение. Фактическое: " + error.getMessage());
        assertTrue(error.getMessage().contains("wheat"),
                "Отказ обязан перечислять допустимые виды культур. Фактическое: " + error.getMessage());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("kindsAndBlocks")
    void ofBlock_EveryBlockOfKind_ReturnsKind(CropKind kind, String blockId, int maxAge, String plantItem,
            String produceItem) {
        assertEquals(Optional.of(kind), CropKind.ofBlock(blockId),
                "ofBlock(" + blockId + ") обязан вернуть " + kind + ": по обратному сопоставлению "
                        + "LevelFarmView отличает посев от сорной травы");
    }

    @Test
    void ofBlock_BlockWithoutNamespace_ReturnsKind() {
        assertEquals(Optional.of(CropKind.WHEAT), CropKind.ofBlock("wheat"),
                "id без namespace читается как minecraft:<id>: иначе шаблон `wheat` не находит посев");
    }

    @Test
    void ofBlock_NonCropBlock_ReturnsEmpty() {
        assertEquals(Optional.empty(), CropKind.ofBlock("minecraft:stone"),
                "Камень не посев: ofBlock обязан вернуть пусто, иначе любой блок считается целью harvest_crop");
    }

    @Test
    void ofBlock_OtherNamespaceSameSuffix_ReturnsEmpty() {
        assertEquals(Optional.empty(), CropKind.ofBlock("othermod:wheat"),
                "Чужой namespace с тем же суффиксом не ванильный посев: иначе дух собирает блоки чужого мода");
    }

    @Test
    void cropState_BeetrootAgeThree_IsMature() {
        assertTrue(new CropState(CropKind.BEETROOT, 3).isMature(),
                "Свёкла зреет на age 3, а не 7: harvest обязан собирать её раньше пшеницы, "
                        + "иначе грядка свёклы никогда не считается зрелой");
    }

    @Test
    void cropState_BeetrootAgeFour_IsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new CropState(CropKind.BEETROOT, 4),
                "Возраст 4 у свёклы невозможен в ванили: пропуск создал бы посев, который игра никогда не вырастит");
    }

    @Test
    void cropState_WheatAgeSeven_IsMature() {
        assertTrue(new CropState(CropKind.WHEAT, 7).isMature(),
                "Пшеница зреет на age 7: harvest собирает только такие клетки, недозрелые не трогаются");
    }

    @Test
    void cropState_NegativeAge_IsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new CropState(CropKind.CARROT, -1),
                "Отрицательный возраст невозможен: пропуск создал бы посев вне ванильного диапазона");
    }

    @Test
    void cropState_AnyKind_IsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new CropState(CropKind.ANY, 0),
                "any не растёт в клетке: пропуск превратил бы фильтр «любой вид» в конкретный посев");
    }
}
