package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.forestry.TreeSpecies;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ScriptCatalogTests {
    @Test
    void find_KnownId_ReturnsSpec() {
        ScriptCatalog catalog = ScriptCatalog.of(List.of(
                new ScriptSpec("scan", "просканировать", List.of(), List.of(), List.of()),
                new ScriptSpec("goto", "пройти", List.of(), List.of(), List.of())));

        Optional<ScriptSpec> found = catalog.find("goto");

        assertTrue(found.isPresent(),
                "Каталог обязан находить скрипт по id: иначе планировщик не сможет вызвать известный скрипт");
        assertEquals("пройти", found.orElseThrow().summary(),
                "find возвращает описание именно запрошенного скрипта: перепутанный id приводит к вызову чужого скрипта");
    }

    @Test
    void of_DuplicateId_Throws() {
        List<ScriptSpec> scripts = List.of(
                new ScriptSpec("scan", "первый", List.of(), List.of(), List.of()),
                new ScriptSpec("scan", "второй", List.of(), List.of(), List.of()));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> ScriptCatalog.of(scripts),
                "Каталог с дублирующимся id обязан падать: иначе find вернёт случайный скрипт из двух, "
                        + "и план выполнит не то действие");

        assertTrue(error.getMessage().contains("scan"),
                "Сообщение об ошибке обязано называть дублирующийся id 'scan': по нему правят каталог. "
                        + "Фактическое сообщение: " + error.getMessage());
    }

    @Test
    void defaultCatalog_ChopTree_CountDefaultsToOneSpeciesIsOptional() {
        ScriptSpec chopTree = CoreScripts.defaultCatalog().find("chop_tree").orElseThrow();

        ParamSpec count = param(chopTree, "count");
        ParamSpec species = param(chopTree, "species");

        assertEquals(false, count.required(),
                "count в chop_tree необязателен: без числа деревьев рубка берёт одно дерево по умолчанию из каталога, "
                        + "а не из шаблона fallback");
        assertEquals(1, count.defaultValue(),
                "count в chop_tree по умолчанию 1: значение задаёт каталог, чтобы шаблон fallback не дублировал умолчание");
        assertEquals(ParamType.INT, count.type(),
                "count в chop_tree — целое: дробное число деревьев невозможно");
        assertEquals(false, species.required(),
                "species в chop_tree необязателен: без него рубят любое дерево (default any)");
    }

    @Test
    void defaultCatalog_ChopTree_SpeciesIsEnumRadiusAndReplantHaveDefaults() {
        ScriptSpec chopTree = CoreScripts.defaultCatalog().find("chop_tree").orElseThrow();

        ParamSpec species = param(chopTree, "species");
        ParamSpec radius = param(chopTree, "radius");
        ParamSpec replant = param(chopTree, "replant");

        assertEquals(ParamType.ENUM, species.type(),
                "species в chop_tree — enum: список пород объявляется из TreeSpecies один раз, "
                        + "иначе каталог и ядро разойдутся при добавлении новой породы");
        assertEquals(TreeSpecies.names(), species.values(),
                "Список допустимых пород enum обязан браться из TreeSpecies, а не копироваться: "
                        + "копия лишней породы молча принимает несуществующую");
        assertEquals("any", species.defaultValue(),
                "species в chop_tree по умолчанию any: рубить без указания породы можно, а вот несуществующую "
                        + "породу нельзя");
        assertEquals(ParamType.INT, radius.type(),
                "radius в chop_tree — целое: дробный радиус бессмыслен");
        assertEquals(false, radius.required(),
                "radius в chop_tree необязателен: умолчание 16 из каталога достаточно для обычной рубки");
        assertEquals(16, radius.defaultValue(),
                "radius в chop_tree по умолчанию 16: каталог задаёт умолчание, чтобы шаблон fallback его не дублировал");
        assertEquals((double) CatalogLimits.CHOP_RADIUS_MIN, radius.min(),
                "radius в chop_tree не может быть ниже минимума каталога: под духом всегда есть хоть одна клетка поиска");
        assertEquals((double) CatalogLimits.CHOP_RADIUS_MAX, radius.max(),
                "radius в chop_tree не может быть выше максимума каталога: границы заданы в одном месте "
                        + "и ядро и каталог читают их вместе");
        assertEquals(ParamType.BOOL, replant.type(),
                "replant в chop_tree — булев флаг: посадить саженец или нет, третьего не дано");
        assertEquals(false, replant.defaultValue(),
                "replant в chop_tree по умолчанию false: посадка — явное желание игрока, а не случайность");
    }

    @Test
    void defaultCatalog_ExpectedIds_ArePresent() {
        ScriptCatalog catalog = CoreScripts.defaultCatalog();
        List<String> expected = List.of("scan", "goto", "follow", "return_home", "patrol", "flee", "chop_tree",
                "mine_vein", "dig_area", "gather", "collect_drops", "harvest_crop", "plant_crop", "craft", "smelt",
                "equip", "deposit", "withdraw", "sort_chest", "acquire", "attack", "defend", "retreat", "place_block",
                "fill", "clear_area", "build_schematic", "deliver", "meet", "say", "wait");

        assertEquals(expected, catalog.all().stream().map(ScriptSpec::id).toList(),
                "Каталог по умолчанию обязан содержать ровно оговорённый набор скриптов: пропущенный скрипт "
                        + "планировщик не найдёт, лишний — разойдётся с /v1/capabilities");
    }

    private static ParamSpec param(ScriptSpec spec, String name) {
        return spec.params().stream().filter(param -> param.name().equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError("В скрипте " + spec.id() + " нет параметра " + name));
    }
}
