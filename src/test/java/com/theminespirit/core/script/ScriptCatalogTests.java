package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.mining.OreKind;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ScriptCatalogTests {
    private static final long WORLD_MIN = -30_000_000L;
    private static final long WORLD_MAX = 30_000_000L;

    record ScriptExpectation(String id, List<String> required, Map<String, long[]> bounds) {
    }

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

    static Stream<Arguments> scriptSpecRows() {
        List<ScriptExpectation> rows = List.of(
                spec("scan", List.of(), Map.of("radius", new long[]{1, 32})),
                spec("goto", List.of("x", "y", "z"),
                        Map.of("x", new long[]{WORLD_MIN, WORLD_MAX}, "y", new long[]{-64, 319},
                                "z", new long[]{WORLD_MIN, WORLD_MAX})),
                spec("follow", List.of("target"), Map.of("radius", new long[]{0, 16})),
                spec("return_home", List.of(), Map.of()),
                spec("patrol", List.of(), Map.of()),
                spec("flee", List.of(), Map.of()),
                spec("chop_tree", List.of(), Map.of("count", new long[]{1, 256})),
                spec("mine_vein", List.of(), Map.of("count", new long[]{1, 256})),
                spec("dig_area", List.of("x", "y", "z", "size"),
                        Map.of("x", new long[]{WORLD_MIN, WORLD_MAX}, "y", new long[]{-64, 319},
                                "z", new long[]{WORLD_MIN, WORLD_MAX})),
                spec("gather", List.of("block", "count"), Map.of("count", new long[]{1, 256})),
                spec("collect_drops", List.of(), Map.of()),
                spec("harvest_crop", List.of("crop"), Map.of()),
                spec("plant_crop", List.of("crop"), Map.of()),
                spec("craft", List.of("item", "count"), Map.of("count", new long[]{1, 256})),
                spec("smelt", List.of("item", "count"), Map.of("count", new long[]{1, 256})),
                spec("equip", List.of("item"), Map.of()),
                spec("deposit", List.of("container", "item", "count"), Map.of("count", new long[]{1, 256})),
                spec("withdraw", List.of("container", "item", "count"), Map.of("count", new long[]{1, 256})),
                spec("sort_chest", List.of(), Map.of()),
                spec("acquire", List.of("item", "count"), Map.of("count", new long[]{1, 256})),
                spec("attack", List.of(), Map.of()),
                spec("defend", List.of("target"), Map.of()),
                spec("retreat", List.of(), Map.of()),
                spec("place_block", List.of("block", "x", "y", "z"),
                        Map.of("x", new long[]{WORLD_MIN, WORLD_MAX}, "y", new long[]{-64, 319},
                                "z", new long[]{WORLD_MIN, WORLD_MAX})),
                spec("fill", List.of("block", "x1", "y1", "z1", "x2", "y2", "z2"),
                        Map.of("x1", new long[]{WORLD_MIN, WORLD_MAX}, "y1", new long[]{-64, 319},
                                "z1", new long[]{WORLD_MIN, WORLD_MAX}, "x2", new long[]{WORLD_MIN, WORLD_MAX},
                                "y2", new long[]{-64, 319}, "z2", new long[]{WORLD_MIN, WORLD_MAX})),
                spec("clear_area", List.of("x1", "y1", "z1", "x2", "y2", "z2"),
                        Map.of("x1", new long[]{WORLD_MIN, WORLD_MAX}, "y1", new long[]{-64, 319},
                                "z1", new long[]{WORLD_MIN, WORLD_MAX}, "x2", new long[]{WORLD_MIN, WORLD_MAX},
                                "y2", new long[]{-64, 319}, "z2", new long[]{WORLD_MIN, WORLD_MAX})),
                spec("build_schematic", List.of("file", "x", "y", "z"),
                        Map.of("x", new long[]{WORLD_MIN, WORLD_MAX}, "y", new long[]{-64, 319},
                                "z", new long[]{WORLD_MIN, WORLD_MAX})),
                spec("deliver", List.of("item", "count", "to"), Map.of("count", new long[]{1, 256})),
                spec("meet", List.of("target"), Map.of()),
                spec("say", List.of("text"), Map.of()),
                spec("wait", List.of("ticks"), Map.of("ticks", new long[]{1, 6000})));

        return rows.stream().map(row -> Arguments.of(row));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scriptSpecRows")
    void catalog_ScriptTable_RowMatchesSpec(ScriptExpectation expected) {
        ScriptSpec spec = CoreScripts.defaultCatalog().find(expected.id())
                .orElseThrow(() -> new AssertionError("В каталоге нет скрипта " + expected.id()));

        List<String> required = spec.params().stream().filter(ParamSpec::required).map(ParamSpec::name).toList();
        assertEquals(expected.required(), required,
                "Набор обязательных параметров скрипта " + expected.id() + " обязан совпадать с таблицей: "
                        + "варианты плана без обязательного параметра валидатор должен отклонять, а лишний "
                        + "обязательный параметр отвергнет легитимный план");

        expected.bounds().forEach((name, minMax) -> {
            ParamSpec param = param(spec, name);
            assertEquals((double) minMax[0], param.min(),
                    "Нижняя граница параметра " + name + " скрипта " + expected.id() + " обязана совпадать с "
                            + "таблицей: отклонённый план между границами или принятый за границами ломает лимиты плана");
            assertEquals((double) minMax[1], param.max(),
                    "Верхняя граница параметра " + name + " скрипта " + expected.id() + " обязана совпадать с "
                            + "таблицей: отклонённый план между границами или принятый за границами ломает лимиты плана");
        });
    }

    @Test
    void defaultCatalog_ScriptTable_CoversExactlyAllIds() {
        ScriptCatalog catalog = CoreScripts.defaultCatalog();
        List<String> tableIds = scriptSpecRows()
                .map(arguments -> arguments.get()[0])
                .map(ScriptExpectation.class::cast)
                .map(ScriptExpectation::id)
                .toList();

        assertEquals(tableIds, catalog.all().stream().map(ScriptSpec::id).toList(),
                "Таблица каталога обязана покрывать ровно все скрипты defaultCatalog: пропущенный скрипт "
                        + "планировщик не найдёт, лишний разойдётся с /v1/capabilities");
    }

    private static ScriptExpectation spec(String id, List<String> required, Map<String, long[]> bounds) {
        return new ScriptExpectation(id, required, bounds);
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