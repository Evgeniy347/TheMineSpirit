package com.theminespirit.core.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.mining.api.OreKind;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class OreKindTests {
    static Stream<Arguments> kindsAndBlocks() {
        return Stream.of(
                Arguments.of(OreKind.COAL, Set.of("minecraft:coal_ore", "minecraft:deepslate_coal_ore")),
                Arguments.of(OreKind.IRON, Set.of("minecraft:iron_ore", "minecraft:deepslate_iron_ore")),
                Arguments.of(OreKind.COPPER, Set.of("minecraft:copper_ore", "minecraft:deepslate_copper_ore")),
                Arguments.of(
                        OreKind.GOLD,
                        Set.of("minecraft:gold_ore", "minecraft:deepslate_gold_ore", "minecraft:nether_gold_ore")),
                Arguments.of(OreKind.REDSTONE, Set.of("minecraft:redstone_ore", "minecraft:deepslate_redstone_ore")),
                Arguments.of(OreKind.LAPIS, Set.of("minecraft:lapis_ore", "minecraft:deepslate_lapis_ore")),
                Arguments.of(OreKind.DIAMOND, Set.of("minecraft:diamond_ore", "minecraft:deepslate_diamond_ore")),
                Arguments.of(OreKind.EMERALD, Set.of("minecraft:emerald_ore", "minecraft:deepslate_emerald_ore")),
                Arguments.of(OreKind.QUARTZ, Set.of("minecraft:nether_quartz_ore")),
                Arguments.of(OreKind.NETHERITE, Set.of("minecraft:ancient_debris")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("kindsAndBlocks")
    void blocks_EverySpecificKind_MatchesVanillaOreTable(OreKind kind, Set<String> expected) {
        assertEquals(
                expected,
                OreKind.blocks(kind),
                "Набор блоков вида " + kind + " обязан совпадать с ванильными id: по нему ищется цель добычи, "
                        + "а пропущенный каменный/глубинносланцевый вариант оставляет часть жилы недобытой"
                        + " Иначе тест blocks_EverySpecificKind_MatchesVanillaOreTable перестанет ловить эту поломку. При смене blocks_EverySpecificKind_MatchesVanillaOreTable проверь это утверждение.");
    }

    @Test
    void blocks_AnyKind_IsUnionOfSpecificKinds() {
        Set<String> union = new HashSet<>();
        for (OreKind kind : OreKind.values()) {
            if (kind != OreKind.ANY) {
                union.addAll(OreKind.blocks(kind));
            }
        }

        assertEquals(
                union,
                OreKind.blocks(OreKind.ANY),
                "any обязан покрывать все руды: иначе mine_vein(ore=any) молча пропускает часть руд"
                        + " При смене blocks_AnyKind_IsUnionOfSpecificKinds проверь это утверждение.");
    }

    @Test
    void names_AllKinds_AreLowercaseInDeclarationOrder() {
        assertEquals(
                List.of(
                        "any",
                        "coal",
                        "iron",
                        "copper",
                        "gold",
                        "redstone",
                        "lapis",
                        "diamond",
                        "emerald",
                        "quartz",
                        "netherite"),
                OreKind.names(),
                "Список значений для каталога обязан идти в порядке объявления и нижним регистром: "
                        + "по нему валидатор плана показывает допустимые виды руды ИИ"
                        + " Иначе тест names_AllKinds_AreLowercaseInDeclarationOrder перестанет ловить эту поломку. При смене names_AllKinds_AreLowercaseInDeclarationOrder проверь это утверждение.");
    }

    @Test
    void parse_KnownKindName_ReturnsKind() {
        assertEquals(
                OreKind.IRON,
                OreKind.parse("iron"),
                "parse обязан принимать имя вида руды из каталога: иначе план с ore=iron не запускается"
                        + " При смене parse_KnownKindName_ReturnsKind проверь это утверждение.");
    }

    @Test
    void parse_KindNameWithCaseAndSpaces_ReturnsKind() {
        assertEquals(
                OreKind.NETHERITE,
                OreKind.parse("  Netherite "),
                "parse обязан прощать регистр и пробелы: иначе опечатка в регистре отвергает верный вид руды"
                        + " При смене parse_KindNameWithCaseAndSpaces_ReturnsKind проверь это утверждение.");
    }

    @Test
    void parse_UnknownKind_ThrowsWithAllowedList() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> OreKind.parse("mithril"),
                "Неизвестный вид руды обязан отвергаться: иначе mine_vein ищет блоки, которых нет, и молча "
                        + "завершается без добычи"
                        + " При смене parse_UnknownKind_ThrowsWithAllowedList проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("mithril"),
                "Отказ обязан называть неизвестное значение. Фактическое: " + error.getMessage()
                        + " Иначе тест parse_UnknownKind_ThrowsWithAllowedList перестанет ловить эту поломку. При смене parse_UnknownKind_ThrowsWithAllowedList проверь это утверждение.");
        assertTrue(
                error.getMessage().contains("iron"),
                "Отказ обязан перечислять допустимые виды руды. Фактическое: " + error.getMessage()
                        + " Иначе тест parse_UnknownKind_ThrowsWithAllowedList перестанет ловить эту поломку. При смене parse_UnknownKind_ThrowsWithAllowedList проверь это утверждение.");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("kindsAndBlocks")
    void ofBlock_EveryBlockOfKind_ReturnsKind(OreKind kind, Set<String> blocks) {
        for (String block : blocks) {
            assertEquals(
                    Optional.of(kind),
                    OreKind.ofBlock(block),
                    "ofBlock(" + block + ") обязан вернуть " + kind + ": по обратному сопоставлению "
                            + "LevelOreView отвечает, руда ли это"
                            + " Иначе тест ofBlock_EveryBlockOfKind_ReturnsKind перестанет ловить эту поломку. При смене ofBlock_EveryBlockOfKind_ReturnsKind проверь это утверждение.");
        }
    }

    @Test
    void ofBlock_BlockWithoutNamespace_ReturnsKind() {
        assertEquals(
                Optional.of(OreKind.DIAMOND),
                OreKind.ofBlock("diamond_ore"),
                "id без namespace читается как minecraft:<id>: иначе шаблон `diamond_ore` не находит руду"
                        + " При смене ofBlock_BlockWithoutNamespace_ReturnsKind проверь это утверждение.");
    }

    @Test
    void ofBlock_NonOreBlock_ReturnsEmpty() {
        assertEquals(
                Optional.empty(),
                OreKind.ofBlock("minecraft:stone"),
                "Камень не руда: ofBlock обязан вернуть пусто, иначе любой камень считается целью mine_vein"
                        + " При смене ofBlock_NonOreBlock_ReturnsEmpty проверь это утверждение.");
    }

    @Test
    void ofBlock_OtherNamespaceSameSuffix_ReturnsEmpty() {
        assertEquals(
                Optional.empty(),
                OreKind.ofBlock("othermod:iron_ore"),
                "Чужой namespace с тем же суффиксом не ванильная руда: иначе дух гоняется за блоком чужого мода"
                        + " При смене ofBlock_OtherNamespaceSameSuffix_ReturnsEmpty проверь это утверждение.");
    }
}
