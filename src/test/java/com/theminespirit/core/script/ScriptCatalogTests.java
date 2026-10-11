package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void defaultCatalog_CollectDrops_RadiusHasBoundsAndDefaultFilterIsOptionalList() {
        ScriptSpec collectDrops = CoreScripts.defaultCatalog().find("collect_drops").orElseThrow();

        ParamSpec radius = param(collectDrops, "radius");
        ParamSpec filter = param(collectDrops, "filter");

        assertEquals(1.0, radius.min(),
                "Радиус подбора начинается с 1 блока: нулевой радиус подбирал бы только предметы под духом");
        assertEquals(16.0, radius.max(),
                "Радиус подбора ограничен 16 блоками: больший радиус тащит духа за предметами через полкарты");
        assertEquals(8, radius.defaultValue(), "Радиус подбора по умолчанию 8: значение живёт в каталоге");
        assertEquals(false, radius.required(), "radius в collect_drops необязателен: умолчание подставляет валидатор");
        assertEquals(ParamType.STRING_LIST, filter.type(),
                "filter в collect_drops — список шаблонов id предметов");
        assertEquals(false, filter.required(),
                "filter в collect_drops необязателен: без него подбирается всё");
        assertEquals(2, collectDrops.params().size(),
                "У collect_drops ровно два параметра, radius и filter: лишний параметр попал бы в capabilities");
    }

    @Test
    void defaultCatalog_Scan_RadiusUsesScanRadiusLimitsAndFilterIsOptionalList() {
        ScriptSpec scan = CoreScripts.defaultCatalog().find("scan").orElseThrow();

        ParamSpec radius = param(scan, "radius");

        assertEquals((double) CatalogLimits.SCAN_RADIUS_MAX, radius.max(),
                "Радиус scan ограничен ScanRadius.MAX: скан большего радиуса кладёт серверный тик");
        assertEquals(ParamType.STRING_LIST, param(scan, "filter").type(),
                "filter в scan — список шаблонов id блоков");
        assertEquals(false, param(scan, "filter").required(), "filter в scan необязателен: без него видны все блоки");
    }

    private static ParamSpec param(ScriptSpec spec, String name) {
        return spec.params().stream().filter(param -> param.name().equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError("В скрипте " + spec.id() + " нет параметра " + name));
    }
}
