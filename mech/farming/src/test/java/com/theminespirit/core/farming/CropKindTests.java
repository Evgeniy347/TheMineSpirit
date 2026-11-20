package com.theminespirit.core.farming;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.farming.api.CropKind;
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
                Arguments.of(
                        CropKind.parse("sweet_berry_bush"),
                        "minecraft:sweet_berry_bush",
                        3,
                        "sweet_berries",
                        "sweet_berries"),
                Arguments.of(CropKind.parse("sugar_cane"), "minecraft:sugar_cane", 15, "sugar_cane", "sugar_cane"),
                Arguments.of(CropKind.parse("bamboo"), "minecraft:bamboo", 1, "bamboo", "bamboo"),
                Arguments.of(CropKind.parse("cactus"), "minecraft:cactus", 15, "cactus", "cactus"),
                Arguments.of(CropKind.parse("melon"), "minecraft:melon_stem", 7, "melon_seeds", "melon_slice"),
                Arguments.of(CropKind.parse("pumpkin"), "minecraft:pumpkin_stem", 7, "pumpkin_seeds", "pumpkin"),
                Arguments.of(CropKind.parse("cocoa"), "minecraft:cocoa", 2, "cocoa_beans", "cocoa_beans"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("kindsAndBlocks")
    void attributes_EveryPlantableKind_MatchesVanillaTable(
            CropKind kind, String blockId, int maxAge, String plantItem, String produceItem) {
        assertEquals(
                blockId,
                kind.blockId(),
                "Блок вида " + kind + " обязан совпадать с ванильным id: по нему поиск отличает посев от земли, "
                        + "иначе дух не найдёт грядку и план поля встанет. При смене таблицы культур проверь "
                        + "ofBlock_EveryBlockOfKind_ReturnsKind");
        assertEquals(
                maxAge,
                kind.maxAge(),
                "Максимальный возраст вида " + kind + " обязан совпадать с ванилью (пшеница 7, свёкла 3): по "
                        + "нему harvest отличает зрелый посев от растущего, иначе сбор идёт раньше срока. При "
                        + "смене таблицы проверь cropState-строки ниже (свёкла 3, пшеница 7)");
        assertEquals(
                plantItem,
                kind.plantItem(),
                "Посадочный предмет вида " + kind + " тратится из инвентаря: у моркови и картофеля это сам "
                        + "урожай, иначе дух несёт не тот предмет и грядка пустует. При смене таблицы проверь "
                        + "SeedPolicyTests.forPlanting_ItemsAndCells_MatchesCount");
        assertEquals(
                produceItem,
                kind.produceItem(),
                "Урожай вида " + kind + " подбирается фильтром DropCollector: иначе сбор теряет предметы, и "
                        + "урожай пропадает на земле. При смене таблицы проверь живой сбор пшеницы духом");
    }

    @Test
    void names_AllKinds_AreLowercaseInDeclarationOrder() {
        assertEquals(
                List.of(
                        "any",
                        "wheat",
                        "carrot",
                        "potato",
                        "beetroot",
                        "sweet_berry_bush",
                        "sugar_cane",
                        "bamboo",
                        "cactus",
                        "melon",
                        "pumpkin",
                        "cocoa"),
                CropKind.names(),
                "Список значений для каталога обязан идти в порядке объявления и нижним регистром: по нему "
                        + "валидатор плана показывает допустимые виды культур ИИ, иначе стратег пришлёт "
                        + "несуществующий crop. При смене CropKind.names проверь plantableNames_AnyKind_IsExcluded");
    }

    @Test
    void plantableNames_AnyKind_IsExcluded() {
        assertEquals(
                List.of(
                        "wheat",
                        "carrot",
                        "potato",
                        "beetroot",
                        "sweet_berry_bush",
                        "sugar_cane",
                        "bamboo",
                        "cactus",
                        "melon",
                        "pumpkin",
                        "cocoa"),
                CropKind.plantableNames(),
                "plant_crop требует конкретный вид: иначе any в списке позволил бы план «посади что-нибудь», а дух не "
                        + "знает, какие семена тратить, и стоит у поля. При смене CropKind.plantableNames проверь "
                        + "список names выше в этом классе");
    }

    @Test
    void parse_KnownKindName_ReturnsKind() {
        assertEquals(
                CropKind.WHEAT,
                CropKind.parse("wheat"),
                "parse обязан принимать имя вида из каталога: иначе план с crop=wheat не запускается, и работа "
                        + "поля пропускается молча. При смене CropKind.parse проверь соседние parse_* строки");
    }

    @Test
    void parse_KindNameWithCaseAndSpaces_ReturnsKind() {
        assertEquals(
                CropKind.BEETROOT,
                CropKind.parse("  Beetroot "),
                "parse обязан прощать регистр и пробелы: иначе опечатка в регистре отвергает верный вид культуры "
                        + "и план падает на пустом месте. При смене парсера проверь строку Beetroot с пробелами");
    }

    @Test
    void parse_UnknownKind_ThrowsWithAllowedList() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> CropKind.parse("rice"),
                "Неизвестный вид культуры обязан отвергаться: иначе скрипт ищет посевы, которых не знает, и "
                        + "молча завершается без работы. При смене CropKind.parse проверь текст отказа ниже");

        assertTrue(
                error.getMessage().contains("rice"),
                "Отказ обязан называть неизвестное значение: иначе в логе нет исходного crop и опечатку в "
                        + "плане не найти. При смене текста отказа проверь эту строку. Фактическое: "
                        + error.getMessage());
        assertTrue(
                error.getMessage().contains("wheat"),
                "Отказ обязан перечислять допустимые виды культур: иначе стратег не знает, что можно прислать, "
                        + "и повторяет то же. При смене текста проверь список names выше. Фактическое: "
                        + error.getMessage());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("kindsAndBlocks")
    void ofBlock_EveryBlockOfKind_ReturnsKind(
            CropKind kind, String blockId, int maxAge, String plantItem, String produceItem) {
        assertEquals(
                Optional.of(kind),
                CropKind.ofBlock(blockId),
                "ofBlock(" + blockId + ") обязан вернуть " + kind + ": по обратному сопоставлению LevelFarmView "
                        + "отличает посев от сорной травы, иначе прополка путается со сбором. При смене "
                        + "CropKind.ofBlock проверь строки stone и othermod ниже");
    }

    @Test
    void ofBlock_BlockWithoutNamespace_ReturnsKind() {
        assertEquals(
                Optional.of(CropKind.WHEAT),
                CropKind.ofBlock("wheat"),
                "id без namespace читается как minecraft:<id>: иначе шаблон `wheat` не находит посев и план с "
                        + "коротким id пропускает работу. При смене ofBlock проверь строку wheat в таблице выше");
    }

    @Test
    void ofBlock_NonCropBlock_ReturnsEmpty() {
        assertEquals(
                Optional.empty(),
                CropKind.ofBlock("minecraft:stone"),
                "Камень не посев: ofBlock обязан вернуть пусто, иначе любой блок считается целью harvest_crop и "
                        + "дух бьёт камень. При смене ofBlock проверь чужой namespace ниже в этом классе");
    }

    @Test
    void ofBlock_OtherNamespaceSameSuffix_ReturnsEmpty() {
        assertEquals(
                Optional.empty(),
                CropKind.ofBlock("othermod:wheat"),
                "Чужой namespace с тем же суффиксом не ванильный посев: иначе дух собирает блоки чужого мода и "
                        + "ломает чужие постройки. При смене ofBlock проверь строку stone выше в этом классе");
    }

    @Test
    void cropState_BeetrootAgeThree_IsMature() {
        assertTrue(
                new CropState(CropKind.BEETROOT, 3).isMature(),
                "Свёкла зреет на age 3, а не 7: harvest обязан собирать её раньше пшеницы, иначе грядка свёклы "
                        + "никогда не считается зрелой. При смене maxAge свёклы проверь таблицу kindsAndBlocks");
    }

    @Test
    void cropState_BeetrootAgeFour_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new CropState(CropKind.BEETROOT, 4),
                "Возраст 4 у свёклы невозможен в ванили: иначе пропуск создал бы посев, который игра никогда не "
                        + "вырастит, и дух стоял бы над ним. При смене валидации CropState проверь границы 3 и 4");
    }

    @Test
    void cropState_WheatAgeSeven_IsMature() {
        assertTrue(
                new CropState(CropKind.WHEAT, 7).isMature(),
                "Пшеница зреет на age 7: harvest собирает только такие клетки, недозрелые не трогаются, чтобы "
                        + "не снимать зелёный колос, иначе урожай соберут зелёным. При смене проверки зрелости проверь свёклу age 3 выше");
    }

    @Test
    void cropState_NegativeAge_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new CropState(CropKind.CARROT, -1),
                "Отрицательный возраст невозможен: иначе пропуск создал бы посев вне ванильного диапазона и "
                        + "сбор врал бы о зрелости. При смене валидации CropState проверь границы минус один и ноль");
    }

    @Test
    void cropState_AnyKind_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new CropState(CropKind.ANY, 0),
                "any не растёт в клетке: иначе пропуск превратил бы фильтр «любой вид» в конкретный посев "
                        + "и план посадил бы случайное. При смене проверки вида в CropState проверь строку BEETROOT выше");
    }
}
