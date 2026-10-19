package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.farming.CropKind;
import com.theminespirit.core.forestry.TreeSpecies;
import com.theminespirit.core.mining.DescentData;
import com.theminespirit.core.mining.DescentDirection;
import com.theminespirit.core.mining.MiningTuning;
import com.theminespirit.core.mining.OreKind;
import com.theminespirit.core.mining.PickaxeTiers;
import java.util.LinkedHashSet;
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
    void restrictedTo_SetInDifferentOrder_KeepsCatalogOrder() {
        ScriptCatalog catalog = ScriptCatalog.of(List.of(
                new ScriptSpec("scan", "первый", List.of(), List.of(), List.of()),
                new ScriptSpec("goto", "второй", List.of(), List.of(), List.of()),
                new ScriptSpec("follow", "третий", List.of(), List.of(), List.of())));

        ScriptCatalog subset = catalog.restrictedTo(new LinkedHashSet<>(List.of("follow", "scan")));

        assertEquals(List.of("scan", "follow"), subset.all().stream().map(ScriptSpec::id).toList(),
                "Скрипты подкаталога обязаны идти в порядке полного каталога, а не набора: /v1/capabilities "
                        + "перечисляет их в порядке каталога (docs/api/README.md), а IMPLEMENTED-таблица даёт другой порядок. "
                        + "Проверьте, что restrictedTo идёт по каталогу и отбирает по набору");
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
    void defaultCatalog_Requirements_ReferenceExistingParamAndKnownTiers() {
        for (ScriptSpec spec : CoreScripts.defaultCatalog().all()) {
            List<String> names = spec.params().stream().map(ParamSpec::name).toList();
            for (ScriptRequirement requirement : spec.requires()) {
                assertTrue(names.contains(requirement.param()),
                        "Требование скрипта " + spec.id() + " ссылается на параметр '" + requirement.param()
                                + "', которого нет в каталоге: проверка плана не найдёт значение и пропустит шаг");
                requirement.tiers().values().forEach(tier -> assertTrue(
                        tier >= ScriptRequirement.MIN_TIER && tier <= ScriptRequirement.MAX_TIER,
                        "Уровень инструмента " + tier + " в требовании скрипта " + spec.id()
                                + " вне диапазона " + ScriptRequirement.MIN_TIER + ".." + ScriptRequirement.MAX_TIER));
            }
        }
        assertEquals(OreKind.names(), List.copyOf(PickaxeTiers.oreTiers().keySet()),
                "Таблица уровней руды обязана покрывать ровно виды OreKind в их порядке: пропущенная руда "
                        + "получала бы уровень по умолчанию, лишняя — никогда не срабатывала");
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
    void defaultCatalog_PillarUp_DownIsOptionalFalse() {
        ScriptSpec pillarUp = CoreScripts.defaultCatalog().find("pillar_up").orElseThrow();

        ParamSpec down = param(pillarUp, "down");

        assertEquals(ParamType.BOOL, down.type(),
                "down в pillar_up — булев флаг: разобрать столб или оставить, третьего не дано");
        assertEquals(false, down.required(),
                "down в pillar_up необязателен: обычный подъём оставляет столб стоять");
        assertEquals(false, down.defaultValue(),
                "down по умолчанию false: разбор столба — явное намерение, а не побочный эффект подъёма");
    }

    @Test
    void defaultCatalog_PillarDown_HasNoRequiredParams() {
        ScriptSpec pillarDown = CoreScripts.defaultCatalog().find("pillar_down").orElseThrow();

        List<String> required = pillarDown.params().stream().filter(ParamSpec::required)
                .map(ParamSpec::name).toList();

        assertEquals(List.of(), required,
                "pillar_down не требует параметров: столб ищется по своим помеченным блокам под духом, "
                        + "а лишний обязательный параметр отверг бы легитимный план");
    }

    @Test
    void defaultCatalog_ChopTarget_CoordinatesRequiredReplantOptional() {
        ScriptSpec chopTarget = CoreScripts.defaultCatalog().find("chop_target").orElseThrow();

        assertEquals(List.of("x", "y", "z"),
                chopTarget.params().stream().filter(ParamSpec::required).map(ParamSpec::name).toList(),
                "У chop_target координаты ствола обязательны, а replant — нет: без клетки дерево не определить, "
                        + "а посадка саженца необязательна");

        ParamSpec replant = param(chopTarget, "replant");
        assertEquals(ParamType.BOOL, replant.type(),
                "replant в chop_target — булев флаг: посадить саженец или нет, третьего не дано");
        assertEquals(false, replant.defaultValue(),
                "replant в chop_target по умолчанию false, как у chop_tree: посадка — явное желание, а не случайность");
        assertEquals(4, chopTarget.params().size(),
                "У chop_target ровно четыре параметра: x, y, z и replant. Лишний попал бы в capabilities, "
                        + "а забытый не подставил бы умолчание");
    }

    static Stream<Arguments> scriptSpecRows() {
        List<ScriptExpectation> rows = List.of(
                spec("scan", List.of(), Map.of("radius", new long[]{1, 32})),
                spec("scan_blocks", List.of(), Map.of("radius", new long[]{1, 32})),
                spec("scan_entities", List.of(), Map.of("radius", new long[]{1, 32})),
                spec("sense_hazard", List.of(), Map.of("radius", new long[]{1, 32})),
                spec("locate_block", List.of("filter"), Map.of("radius", new long[]{1, 32})),
                spec("locate_tree", List.of(), Map.of("radius", new long[]{1, 32})),
                spec("locate_container", List.of(), Map.of("radius", new long[]{1, 32})),
                spec("look_at", List.of("x", "y", "z"),
                        Map.of("x", new long[]{WORLD_MIN, WORLD_MAX}, "y", new long[]{-64, 319},
                                "z", new long[]{WORLD_MIN, WORLD_MAX})),
                spec("remember", List.of("name", "x", "y", "z", "type"),
                        Map.of("x", new long[]{WORLD_MIN, WORLD_MAX}, "y", new long[]{-64, 319},
                                "z", new long[]{WORLD_MIN, WORLD_MAX})),
                spec("recall", List.of("name"), Map.of()),
                spec("recall_all", List.of("type"), Map.of()),
                spec("forget", List.of("name"), Map.of()),
                spec("goto", List.of("x", "y", "z"),
                        Map.of("x", new long[]{WORLD_MIN, WORLD_MAX}, "y", new long[]{-64, 319},
                                "z", new long[]{WORLD_MIN, WORLD_MAX})),
                spec("follow", List.of("target"), Map.of("radius", new long[]{0, 16})),
                spec("escort", List.of("target"),
                        Map.of("radius", new long[]{0, 16}, "until", new long[]{0, 6000})),
                spec("return_home", List.of(), Map.of()),
                spec("patrol", List.of("points"), Map.of("loops", new long[]{1, 16})),
                spec("goto_block", List.of("block"),
                        Map.of("radius", new long[]{1, 32}, "count", new long[]{1, 256})),
                spec("come_to_player", List.of(), Map.of()),
                spec("wander", List.of(),
                        Map.of("radius", new long[]{1, 32}, "duration_ticks", new long[]{20, 6000})),
                spec("explore", List.of(),
                        Map.of("radius", new long[]{1, 32}, "budget", new long[]{1, 64})),
                spec("keep_distance", List.of("target"), Map.of("distance", new long[]{1, 16})),
                spec("walk_careful", List.of("x", "y", "z"),
                        Map.of("x", new long[]{WORLD_MIN, WORLD_MAX}, "y", new long[]{-64, 319},
                                "z", new long[]{WORLD_MIN, WORLD_MAX})),
                spec("step_over", List.of("dx", "dz"),
                        Map.of("dx", new long[]{-1, 1}, "dz", new long[]{-1, 1})),
                spec("approach", List.of("x", "y", "z"),
                        Map.of("x", new long[]{WORLD_MIN, WORLD_MAX}, "y", new long[]{-64, 319},
                                "z", new long[]{WORLD_MIN, WORLD_MAX})),
                spec("swim", List.of("x", "y", "z"),
                        Map.of("x", new long[]{WORLD_MIN, WORLD_MAX}, "y", new long[]{-64, 319},
                                "z", new long[]{WORLD_MIN, WORLD_MAX})),
                spec("cross_water", List.of("x", "y", "z"),
                        Map.of("x", new long[]{WORLD_MIN, WORLD_MAX}, "y", new long[]{-64, 319},
                                "z", new long[]{WORLD_MIN, WORLD_MAX})),
                spec("bridge", List.of("x", "y", "z", "length"),
                        Map.of("x", new long[]{WORLD_MIN, WORLD_MAX}, "y", new long[]{-64, 319},
                                "z", new long[]{WORLD_MIN, WORLD_MAX},
                                "length", new long[]{CatalogLimits.BRIDGE_LENGTH_MIN,
                                        CatalogLimits.BRIDGE_LENGTH_MAX})),
                spec("flee", List.of(), Map.of()),
                spec("chop_tree", List.of(), Map.of("count", new long[]{1, 256}, "radius", new long[]{1, 32})),
                spec("chop_target", List.of("x", "y", "z"),
                        Map.of("x", new long[]{WORLD_MIN, WORLD_MAX}, "y", new long[]{-64, 319},
                                "z", new long[]{WORLD_MIN, WORLD_MAX})),
                spec("mine_vein", List.of(), Map.of("count", new long[]{1, 256})),
                spec("dig_area", List.of("x", "y", "z", "size"),
                        Map.of("x", new long[]{WORLD_MIN, WORLD_MAX}, "y", new long[]{-64, 319},
                                "z", new long[]{WORLD_MIN, WORLD_MAX}, "size", new long[]{1, 16})),
                spec("dig_descent", List.of("direction"),
                        Map.of("y", new long[]{-64, 319})),
                spec("dig_descent_widen", List.of("direction"),
                        Map.of("y", new long[]{-64, 319})),
                spec("place_descent_stairs", List.of("direction"),
                        Map.of("y", new long[]{-64, 319})),
                spec("safe_mine", List.of("mode", "direction"),
                        Map.of("y", new long[]{-64, 319},
                                "length", new long[]{CatalogLimits.MINE_LENGTH_MIN, CatalogLimits.MINE_LENGTH_MAX},
                                "torchInterval", new long[]{MiningTuning.DESCENT_TORCH_INTERVAL_MIN,
                                        MiningTuning.DESCENT_TORCH_INTERVAL_MAX})),
                spec("gather", List.of("block", "count"), Map.of("count", new long[]{1, 256})),
                spec("collect_drops", List.of(), Map.of("radius", new long[]{1, 16})),
                spec("harvest_crop", List.of(), Map.of("radius", new long[]{1, 16})),
                spec("plant_crop", List.of("crop"), Map.of("radius", new long[]{1, 16}, "count", new long[]{1, 256})),
                spec("till", List.of(), Map.of("radius", new long[]{1, 16}, "count", new long[]{1, 256})),
                spec("bone_meal", List.of(), Map.of("radius", new long[]{1, 16}, "count", new long[]{1, 256})),
                spec("breed", List.of("species"),
                        Map.of("radius", new long[]{1, 16}, "pairs", new long[]{1, 8})),
                spec("feed", List.of("species"),
                        Map.of("radius", new long[]{1, 16}, "count", new long[]{1, 64})),
                spec("shear", List.of(), Map.of("radius", new long[]{1, 16}, "count", new long[]{1, 64})),
                spec("milk", List.of(), Map.of("radius", new long[]{1, 16}, "count", new long[]{1, 64})),
                spec("collect_eggs", List.of(), Map.of("radius", new long[]{1, 16})),
                spec("harvest_hive", List.of(),
                        Map.of("radius", new long[]{1, 16}, "count", new long[]{1, 64})),
                spec("cull", List.of("species"),
                        Map.of("keep", new long[]{2, 64}, "radius", new long[]{1, 16})),
                spec("herd_check", List.of(), Map.of("radius", new long[]{1, 16})),
                spec("hunt", List.of("species"),
                        Map.of("radius", new long[]{1, 48}, "count", new long[]{1, 64})),
                spec("capture", List.of("species"), Map.of("radius", new long[]{1, 16})),
                spec("fish", List.of(),
                        Map.of("count", new long[]{1, 256},
                                "duration_ticks", new long[]{CatalogLimits.FISH_DURATION_MIN,
                                        CatalogLimits.FISH_DURATION_MAX},
                                "radius", new long[]{1, 32})),
                spec("bucket_fish", List.of(),
                        Map.of("count", new long[]{1, 256}, "radius", new long[]{1, 32})),
                spec("craft", List.of("item", "count"),
                        Map.of("count", new long[]{1, 256}, "radius", new long[]{1, 32})),
                spec("smelt", List.of("item", "count"),
                        Map.of("count", new long[]{1, 256}, "radius", new long[]{1, 32})),
                spec("equip", List.of("item"), Map.of()),
                spec("deposit", List.of("item", "count"),
                        Map.of("count", new long[]{1, 256}, "radius", new long[]{1, 16})),
                spec("withdraw", List.of("item", "count"),
                        Map.of("count", new long[]{1, 256}, "radius", new long[]{1, 16})),
                spec("eat", List.of(), Map.of()),
                spec("sort_chest", List.of(), Map.of("radius", new long[]{1, 16})),
                spec("unload", List.of(), Map.of("radius", new long[]{1, 16})),
                spec("acquire", List.of("item", "count"), Map.of("count", new long[]{1, 256})),
                spec("attack", List.of(), Map.of()),
                spec("defend", List.of("target"), Map.of()),
                spec("retreat", List.of(), Map.of()),
                spec("disengage", List.of(), Map.of("distance", new long[]{1, 16})),
                spec("pillar_up", List.of("height"), Map.of("height", new long[]{1, 256})),
                spec("pillar_down", List.of(), Map.of()),
                spec("climb", List.of(), Map.of()),
                spec("place_block", List.of("block", "x", "y", "z"),
                        Map.of("x", new long[]{WORLD_MIN, WORLD_MAX}, "y", new long[]{-64, 319},
                                "z", new long[]{WORLD_MIN, WORLD_MAX})),
                spec("light_area", List.of("x", "y", "z"),
                        Map.of("x", new long[]{WORLD_MIN, WORLD_MAX}, "y", new long[]{-64, 319},
                                "z", new long[]{WORLD_MIN, WORLD_MAX},
                                "radius", new long[]{CatalogLimits.LIGHT_RADIUS_MIN, CatalogLimits.LIGHT_RADIUS_MAX})),
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
                spec("build_farm", List.of("crop", "x1", "z1", "x2", "z2"),
                        Map.of("x1", new long[]{WORLD_MIN, WORLD_MAX}, "z1", new long[]{WORLD_MIN, WORLD_MAX},
                                "x2", new long[]{WORLD_MIN, WORLD_MAX}, "z2", new long[]{WORLD_MIN, WORLD_MAX})),
                spec("build_fence", List.of("block", "points"), Map.of()),
                spec("build_shelter", List.of(), Map.of("side", new long[]{3, 4})),
                spec("fill_crater", List.of("crater"), Map.of()),
                spec("deliver", List.of("item", "count", "to"), Map.of("count", new long[]{1, 256})),
                spec("meet", List.of("target"), Map.of()),
                spec("say", List.of("text"), Map.of()),
                spec("wait", List.of("ticks"), Map.of("ticks", new long[]{1, 6000})),
                spec("sleep", List.of(), Map.of()),
                spec("close_door", List.of("x", "y", "z"),
                        Map.of("x", new long[]{WORLD_MIN, WORLD_MAX}, "y", new long[]{-64, 319},
                                "z", new long[]{WORLD_MIN, WORLD_MAX})),
                spec("set_camp", List.of(),
                        Map.of("x", new long[]{WORLD_MIN, WORLD_MAX}, "y", new long[]{-64, 319},
                                "z", new long[]{WORLD_MIN, WORLD_MAX},
                                "radius", new long[]{CatalogLimits.CAMP_RADIUS_MIN, CatalogLimits.CAMP_RADIUS_MAX})),
                spec("clear_camp", List.of(), Map.of()),
                spec("repair_tool", List.of("item"), Map.of()),
                spec("use_block", List.of("x", "y", "z"),
                        Map.of("x", new long[]{WORLD_MIN, WORLD_MAX}, "y", new long[]{-64, 319},
                                "z", new long[]{WORLD_MIN, WORLD_MAX})),
                spec("open", List.of("x", "y", "z"),
                        Map.of("x", new long[]{WORLD_MIN, WORLD_MAX}, "y", new long[]{-64, 319},
                                "z", new long[]{WORLD_MIN, WORLD_MAX})),
                spec("set_combat_mode", List.of("mode"), Map.of()),
                spec("drop_to_player", List.of("item", "count", "to"), Map.of("count", new long[]{1, 256})),
                spec("give", List.of("item", "count", "to"), Map.of("count", new long[]{1, 256})),
                spec("take", List.of("item", "count", "from"), Map.of("count", new long[]{1, 256})),
                spec("report", List.of(), Map.of()),
                spec("swap_tool", List.of("block"), Map.of()),
                spec("drink_potion", List.of("type"), Map.of()));

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
    void defaultCatalog_ClearArea_DemolishOptionalFalse() {
        ScriptSpec clearArea = CoreScripts.defaultCatalog().find("clear_area").orElseThrow();
        ParamSpec demolish = param(clearArea, "demolish");

        assertEquals(ParamType.BOOL, demolish.type(),
                "demolish в clear_area — флаг: перенос сносит свою постройку, обычный разбор — нет");
        assertEquals(false, demolish.required(),
                "demolish необязателен: старые планы clear_area без флага остаются валидными");
        assertEquals(false, demolish.defaultValue(),
                "По умолчанию demolish=false: защита от сноса чужого не должна отключаться молча");
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
    void defaultCatalog_DigDescent_DirectionRequiredYOptionalWithDefaultFromData() {
        ScriptSpec digDescent = CoreScripts.defaultCatalog().find("dig_descent").orElseThrow();

        ParamSpec direction = param(digDescent, "direction");
        assertEquals(ParamType.ENUM, direction.type(),
                "direction в dig_descent — enum: валидатор обязан ловить опечатку до запуска, а не копать в никуда");
        assertEquals(DescentDirection.names(), direction.values(),
                "Список direction обязан совпадать с осями DescentDirection: иначе каталог обещает направление, "
                        + "которое ядро не знает");
        assertEquals(true, direction.required(),
                "direction в dig_descent обязателен: без направления неизвестно, в какую сторону копать тоннель");

        ParamSpec y = param(digDescent, "y");
        assertEquals(false, y.required(),
                "y в dig_descent необязателен: без него спуск идёт до целевого этажа из данных");
        assertEquals(DescentData.defaults().targetY(), y.defaultValue(),
                "Default y обязан совпадать с данными спуска: PlanValidator подставляет его в план, "
                        + "а игрок видит умолчание в /v1/capabilities");
        assertEquals((double) CatalogLimits.WORLD_Y_MIN, y.min(),
                "Нижняя граница y обязана совпадать с дном мира каталога");
        assertEquals((double) CatalogLimits.WORLD_Y_MAX, y.max(),
                "Верхняя граница y обязана совпадать с потолком мира каталога");
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

    @Test
    void defaultCatalog_CraftAndSmelt_StationRadiusIsOptionalIntWithDefaultSixteen() {
        for (String id : List.of("craft", "smelt")) {
            ParamSpec radius = param(CoreScripts.defaultCatalog().find(id).orElseThrow(), "radius");

            assertEquals(false, radius.required(),
                    "radius у '" + id + "' необязателен: умолчание задаёт каталог, и план не обязан называть радиус");
            assertEquals(16, radius.defaultValue(),
                    "Радиус поиска станции у '" + id + "' по умолчанию 16: верстак или печь обычно на базе, рядом");
            assertEquals(ParamType.INT, radius.type(),
                    "Радиус поиска станции у '" + id + "' — целое: дробный радиус бессмыслен");
            assertEquals(1.0, radius.min(),
                    "Радиус поиска станции у '" + id + "' начинается с 1: нулевой не найдёт и станцию под ногами");
            assertEquals(32.0, radius.max(),
                    "Радиус поиска станции у '" + id + "' ограничен 32: дальше поиск станции бьёт по серверному тику");
        }
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
        assertEquals(3, collectDrops.params().size(),
                "У collect_drops ровно три параметра, radius, filter и overflow: лишний параметр попал бы "
                        + "в capabilities, а забытый не подставил бы умолчание переполнения");
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

    @Test
    void defaultCatalog_HarvestCrop_CropEnumDefaultsToAnyWithReplant() {
        ScriptSpec harvestCrop = CoreScripts.defaultCatalog().find("harvest_crop").orElseThrow();

        ParamSpec crop = param(harvestCrop, "crop");
        ParamSpec radius = param(harvestCrop, "radius");
        ParamSpec replant = param(harvestCrop, "replant");

        assertEquals(ParamType.ENUM, crop.type(),
                "crop в harvest_crop — enum: валидатор обязан ловить опечатку до запуска, а не искать чужой посев");
        assertEquals(CropKind.names(), crop.values(),
                "Список допустимых crop обязан совпадать с видами CropKind: иначе каталог обещает один набор, "
                        + "а ядро знает другой");
        assertEquals(false, crop.required(),
                "crop в harvest_crop необязателен: без него собирается любой зрелый посев");
        assertEquals("any", crop.defaultValue(),
                "crop по умолчанию 'any': умолчание живёт в каталоге, а не в маппинге");
        assertEquals(false, radius.required(),
                "radius в harvest_crop необязателен: по умолчанию поиск идёт в радиусе из каталога");
        assertEquals(CatalogLimits.FARM_RADIUS_DEFAULT, radius.defaultValue(),
                "radius в harvest_crop по умолчанию " + CatalogLimits.FARM_RADIUS_DEFAULT
                        + ": значение задаёт каталог, чтобы адаптер не дублировал умолчание");
        assertEquals(ParamType.BOOL, replant.type(),
                "replant в harvest_crop — bool: пересадка либо есть, либо нет");
        assertEquals(false, replant.required(),
                "replant в harvest_crop необязателен: самоподдерживающаяся грядка — поведение по умолчанию");
        assertEquals(true, replant.defaultValue(),
                "replant по умолчанию true: каждая собранная клетка тут же засаживается заново");
    }

    @Test
    void defaultCatalog_PlantCrop_CropRequiredWithoutAny() {
        ScriptSpec plantCrop = CoreScripts.defaultCatalog().find("plant_crop").orElseThrow();

        ParamSpec crop = param(plantCrop, "crop");
        ParamSpec count = param(plantCrop, "count");

        assertEquals(ParamType.ENUM, crop.type(),
                "crop в plant_crop — enum: опечатка в виде обязана отклоняться валидатором до запуска");
        assertEquals(CropKind.plantableNames(), crop.values(),
                "Список допустимых crop обязан совпадать с сажаемыми видами без any: план «посади что-нибудь» "
                        + "не говорит, какие семена тратить");
        assertEquals(true, crop.required(),
                "crop в plant_crop обязателен: без вида скрипт не знает, какие семена брать из инвентаря");
        assertEquals(CatalogLimits.PLANT_COUNT_DEFAULT, count.defaultValue(),
                "count в plant_crop по умолчанию " + CatalogLimits.PLANT_COUNT_DEFAULT
                        + ": значение задаёт каталог, чтобы шаблон не дублировал умолчание");
    }

    @Test
    void defaultCatalog_TillAndBoneMeal_RadiusAndCountDefaults() {
        ScriptSpec till = CoreScripts.defaultCatalog().find("till").orElseThrow();
        ScriptSpec boneMeal = CoreScripts.defaultCatalog().find("bone_meal").orElseThrow();

        assertEquals(CatalogLimits.FARM_RADIUS_DEFAULT, param(till, "radius").defaultValue(),
                "radius в till по умолчанию " + CatalogLimits.FARM_RADIUS_DEFAULT
                        + ": значение задаёт каталог, чтобы адаптер не дублировал умолчание");
        assertEquals(CatalogLimits.TILL_COUNT_DEFAULT, param(till, "count").defaultValue(),
                "count в till по умолчанию " + CatalogLimits.TILL_COUNT_DEFAULT);
        assertEquals("any", param(boneMeal, "crop").defaultValue(),
                "crop в bone_meal по умолчанию 'any': без вида подкармливается любой растущий посев");
        assertEquals(CatalogLimits.BONE_MEAL_COUNT_DEFAULT, param(boneMeal, "count").defaultValue(),
                "count в bone_meal по умолчанию " + CatalogLimits.BONE_MEAL_COUNT_DEFAULT);
        assertEquals(2, till.params().size(),
                "У till ровно два параметра, radius и count: лишний параметр попал бы в capabilities");
    }

    @Test
    void defaultCatalog_DepositAndWithdraw_ContainerIsOptionalNearest() {
        ScriptSpec deposit = CoreScripts.defaultCatalog().find("deposit").orElseThrow();
        ParamSpec container = param(deposit, "container");

        assertEquals(false, container.required(),
                "container в deposit необязателен: по умолчанию дух берёт ближайший подходящий контейнер, "
                        + "и план не обязан каждый раз называть координаты");
        assertEquals("nearest", container.defaultValue(),
                "Значение по умолчанию container решает, какой контейнер использовать, если план молчит: "
                        + "'nearest' — ближайший в радиусе. Для deposit оставлен ближайший, 'auto' доступен явно");
    }

    @Test
    void defaultCatalog_Unload_ContainerDefaultsToAuto() {
        ScriptSpec unload = CoreScripts.defaultCatalog().find("unload").orElseThrow();
        ParamSpec container = param(unload, "container");

        assertEquals(false, container.required(),
                "container в unload необязателен: при разгрузке дух сам раскладывает предметы по сундукам");
        assertEquals("auto", container.defaultValue(),
                "Значение по умолчанию container у unload — 'auto': вернувшись из шахты, дух докладывает "
                        + "стакующиеся предметы в сундук, где уже лежит такой предмет с неполным стаком, "
                        + "а не в ближайший попавшийся");
    }

    @Test
    void defaultCatalog_ContainerScripts_RadiusWithinContainerLimits() {
        ScriptCatalog catalog = CoreScripts.defaultCatalog();
        for (String id : List.of("deposit", "withdraw", "sort_chest", "unload")) {
            ScriptSpec script = catalog.find(id).orElseThrow();
            ParamSpec radius = param(script, "radius");

            assertEquals(false, radius.required(),
                    "radius у скрипта '" + id + "' необязателен: без него действует радиус 8, "
                            + "и план не обязан его называть");
            assertEquals(8, radius.defaultValue(),
                    "Радиус поиска контейнера у '" + id + "' по умолчанию 8: ближайший контейнер "
                            + "обычно рядом с духом");
            assertEquals(ParamType.INT, radius.type(),
                    "Радиус поиска контейнера у '" + id + "' — целое: дробный радиус сундука не имеет смысла");
            assertEquals(1.0, radius.min(),
                    "Радиус поиска контейнера у '" + id + "' начинается с 1: нулевой радиус нашёл бы "
                            + "только сундук в ногах духа");
            assertEquals(16.0, radius.max(),
                    "Радиус поиска контейнера у '" + id + "' ограничен 16: дальше дух уходит из базовой зоны");
        }
    }

    @Test
    void defaultCatalog_Unload_ForceDefaultsToFalseAndTriggerSlotsDefined() {
        ScriptSpec unload = CoreScripts.defaultCatalog().find("unload").orElseThrow();
        ParamSpec force = param(unload, "force");

        assertEquals(false, force.required(),
                "force у unload необязателен: без него разгрузка срабатывает только при заполненном инвентаре, "
                        + "а не по каждой мелочи");
        assertEquals(false, force.defaultValue(),
                "force по умолчанию false: разгрузка «без фанатизма» не трогает инвентарь, пока занято "
                        + "меньше 24 слотов");
        assertEquals(24, CatalogLimits.UNLOAD_TRIGGER_SLOTS,
                "Порог разгрузки — 24 слота из 36: дух тащит добро на базу, только когда в инвентаре "
                        + "действительно не осталось места");
    }

    @Test
    void defaultCatalog_CollectDrops_OverflowDefaultsToByValue() {
        ScriptSpec collectDrops = CoreScripts.defaultCatalog().find("collect_drops").orElseThrow();
        ParamSpec overflow = param(collectDrops, "overflow");

        assertEquals(false, overflow.required(),
                "overflow у collect_drops необязателен: по умолчанию действует поведение «берём ценное, "
                        + "дешёвое оставляем»");
        assertEquals(List.of("fail", "by_value"), overflow.values(),
                "У overflow два режима: fail — прежний отказ при полном инвентаре, by_value — подмена по ценности");
        assertEquals("by_value", overflow.defaultValue(),
                "По умолчанию collect_drops работает по ценности: дешёвое остаётся, ценное берётся, "
                        + "а не полный отказ");
    }

    @Test
    void defaultCatalog_Equip_TakesOnlyItemPattern() {
        ScriptSpec equip = CoreScripts.defaultCatalog().find("equip").orElseThrow();

        assertEquals(List.of("item"), equip.params().stream().map(ParamSpec::name).toList(),
                "У equip один обязательный параметр item: по его шаблону ('*_pickaxe') дух выбирает, "
                        + "что надевать, и берёт самый ценный подходящий");
    }

    @Test
    void defaultCatalog_NavScripts_ParamsAndDefaults() {
        ScriptSpec gotoBlock = CoreScripts.defaultCatalog().find("goto_block").orElseThrow();
        ScriptSpec comeToPlayer = CoreScripts.defaultCatalog().find("come_to_player").orElseThrow();
        ScriptSpec patrol = CoreScripts.defaultCatalog().find("patrol").orElseThrow();
        ScriptSpec wander = CoreScripts.defaultCatalog().find("wander").orElseThrow();
        ScriptSpec keepDistance = CoreScripts.defaultCatalog().find("keep_distance").orElseThrow();

        assertEquals(16, param(gotoBlock, "radius").defaultValue(),
                "radius в goto_block по умолчанию 16: каталог задаёт умолчание, чтобы маппинг его не дублировал");
        assertEquals(1, param(gotoBlock, "count").defaultValue(),
                "count в goto_block по умолчанию 1: без числа дух идёт к ближайшему блоку вида");
        assertEquals("nearest", param(comeToPlayer, "player").defaultValue(),
                "player в come_to_player по умолчанию 'nearest': без имени дух идёт к ближайшему игроку");
        assertEquals(ParamType.ENUM, param(patrol, "mode").type(),
                "mode в patrol — enum: опечатка в режиме обязана отклоняться валидатором до запуска");
        assertEquals(List.of("loop", "ping_pong"), param(patrol, "mode").values(),
                "У patrol два режима обхода: loop — по кругу, ping_pong — туда-обратно");
        assertEquals("loop", param(patrol, "mode").defaultValue(),
                "mode в patrol по умолчанию 'loop': умолчание живёт в каталоге, а не в маппинге");
        assertEquals(1, param(patrol, "loops").defaultValue(),
                "loops в patrol по умолчанию 1: без числа кругов патруль проходит маршрут один раз");
        assertEquals(ParamType.STRING_LIST, param(patrol, "points").type(),
                "points в patrol — список строк 'x,y,z': точки маршрута задаются параметрами");
        assertEquals(16, param(wander, "radius").defaultValue(),
                "radius в wander по умолчанию 16: значение задаёт каталог, чтобы маппинг его не дублировал");
        assertEquals(600, param(wander, "duration_ticks").defaultValue(),
                "duration_ticks в wander по умолчанию 600: без длительности блуждание не знает, когда кончить");
        assertEquals(5, param(keepDistance, "distance").defaultValue(),
                "distance в keep_distance по умолчанию 5: без дистанции дух не знает, где встать");
    }

    @Test
    void defaultCatalog_NavLimits_MatchAgreedBounds() {
        assertEquals(1, CatalogLimits.NAV_RADIUS_MIN,
                "Нижняя граница радиуса навигации — 1: нулевой радиус искал бы только под ногами духа");
        assertEquals(32, CatalogLimits.NAV_RADIUS_MAX,
                "Верхняя граница радиуса навигации — 32: больший поиск кладёт серверный тик");
        assertEquals(16, CatalogLimits.NAV_RADIUS_DEFAULT,
                "Радиус навигации по умолчанию — 16: значение задаёт каталог, чтобы маппинги его не дублировали");
        assertEquals(16, CatalogLimits.NAV_PATROL_POINTS_MAX,
                "Точек маршрута patrol — не больше 16: длинный маршрут режется на части");
        assertEquals(1, CatalogLimits.NAV_PATROL_LOOPS_MIN,
                "Кругов patrol — не меньше 1: ноль кругов завершается сразу и врёт стратегу");
        assertEquals(16, CatalogLimits.NAV_PATROL_LOOPS_MAX,
                "Кругов patrol — не больше 16: лимит держит патруль в разумном времени");
        assertEquals(1, CatalogLimits.NAV_PATROL_LOOPS_DEFAULT,
                "Кругов patrol по умолчанию — 1: без числа патруль проходит маршрут один раз");
        assertEquals("loop", CatalogLimits.NAV_PATROL_MODE_DEFAULT,
                "Режим patrol по умолчанию — 'loop': умолчание живёт в каталоге и подставляется валидатором");
        assertEquals(20, CatalogLimits.NAV_WANDER_DURATION_MIN,
                "Блуждание короче 20 тиков бессмысленно: за секунду дух не успевает сдвинуться");
        assertEquals(6000, CatalogLimits.NAV_WANDER_DURATION_MAX,
                "Блуждание дольше 6000 тиков (5 минут) не планируется одним шагом");
        assertEquals(600, CatalogLimits.NAV_WANDER_DURATION_DEFAULT,
                "Длительность блуждания по умолчанию — 600 тиков: без неё скрипт не знает, когда кончить");
        assertEquals(1, CatalogLimits.NAV_KEEP_DISTANCE_MIN,
                "Дистанция keep_distance — не меньше 1: нулевая дистанция — стояние внутри цели");
        assertEquals(16, CatalogLimits.NAV_KEEP_DISTANCE_MAX,
                "Дистанция keep_distance — не больше 16: дальше дух уходит из тикающей зоны");
        assertEquals(5, CatalogLimits.NAV_KEEP_DISTANCE_DEFAULT,
                "Дистанция keep_distance по умолчанию — 5: без неё дух не знает, где встать");
        assertEquals(1.25, CatalogLimits.NAV_ARRIVAL_TOLERANCE,
                "Допуск прибытия навигации — 1.25 блока, как у goto: иначе прибытие считается то здесь, то там");
    }

    private static ParamSpec param(ScriptSpec spec, String name) {
        return spec.params().stream().filter(param -> param.name().equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError("В скрипте " + spec.id() + " нет параметра " + name));
    }
}