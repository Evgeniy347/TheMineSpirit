package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.mining.OreKind;
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
    void defaultCatalog_MineVein_OreEnumAndDefaults() {
        ScriptSpec mineVein = CoreScripts.defaultCatalog().find("mine_vein").orElseThrow();

        ParamSpec ore = param(mineVein, "ore");
        ParamSpec count = param(mineVein, "count");
        ParamSpec radius = param(mineVein, "radius");

        assertEquals(ParamType.ENUM, ore.type(),
                "ore в mine_vein — enum: валидатор обязан ловить опечатку до запуска, а не искать несуществующую жилу");
        assertEquals(OreKind.names(), ore.values(),
                "Список допустимых ore обязан совпадать с видами OreKind: иначе каталог обещает один набор, а ядро знает другой");
        assertEquals(false, ore.required(),
                "ore в mine_vein необязателен: без него добывается любая руда");
        assertEquals("any", ore.defaultValue(),
                "enum ore по умолчанию 'any': умолчание живёт в каталоге, а не в маппинге — его видит "
                        + "/v1/capabilities и подставляет валидатор");
        assertEquals(1, count.defaultValue(),
                "count в mine_vein по умолчанию 1: без числа добывается один блок руды");
        assertEquals(CatalogLimits.HARVEST_RADIUS_DEFAULT, radius.defaultValue(),
                "radius в mine_vein по умолчанию " + CatalogLimits.HARVEST_RADIUS_DEFAULT
                        + ": значение задаёт каталог, чтобы шаблон не дублировал умолчание");
    }

    @Test
    void defaultCatalog_Gather_CountRequiredRadiusDefaults() {
        ScriptSpec gather = CoreScripts.defaultCatalog().find("gather").orElseThrow();

        ParamSpec count = param(gather, "count");
        ParamSpec radius = param(gather, "radius");

        assertEquals(true, count.required(),
                "count в gather обязателен: без числа сбор не имеет цели, в отличие от mine_vein с умолчанием 1");
        assertEquals(false, radius.required(),
                "radius в gather необязателен: по умолчанию поиск идёт в радиусе из каталога");
        assertEquals(CatalogLimits.HARVEST_RADIUS_DEFAULT, radius.defaultValue(),
                "radius в gather по умолчанию " + CatalogLimits.HARVEST_RADIUS_DEFAULT
                        + ": значение задаёт каталог, чтобы адаптер не дублировал умолчание");
    }

    private static ParamSpec param(ScriptSpec spec, String name) {
        return spec.params().stream().filter(param -> param.name().equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError("В скрипте " + spec.id() + " нет параметра " + name));
    }
}
