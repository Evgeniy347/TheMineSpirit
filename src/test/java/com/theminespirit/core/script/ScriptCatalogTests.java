package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.farming.CropKind;
import com.theminespirit.core.forestry.TreeSpecies;
import com.theminespirit.core.mining.OreKind;
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
                        + "перечисляет их в порядке каталога (docs/API.md), а IMPLEMENTED-таблица даёт другой порядок. "
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
                spec("chop_tree", List.of(), Map.of("count", new long[]{1, 256}, "radius", new long[]{1, 32})),
                spec("mine_vein", List.of(), Map.of("count", new long[]{1, 256})),
                spec("dig_area", List.of("x", "y", "z", "size"),
                        Map.of("x", new long[]{WORLD_MIN, WORLD_MAX}, "y", new long[]{-64, 319},
                                "z", new long[]{WORLD_MIN, WORLD_MAX})),
                spec("gather", List.of("block", "count"), Map.of("count", new long[]{1, 256})),
                spec("collect_drops", List.of(), Map.of("radius", new long[]{1, 16})),
                spec("harvest_crop", List.of(), Map.of("radius", new long[]{1, 16})),
                spec("plant_crop", List.of("crop"), Map.of("radius", new long[]{1, 16}, "count", new long[]{1, 256})),
                spec("till", List.of(), Map.of("radius", new long[]{1, 16}, "count", new long[]{1, 256})),
                spec("bone_meal", List.of(), Map.of("radius", new long[]{1, 16}, "count", new long[]{1, 256})),
                spec("craft", List.of("item", "count"), Map.of("count", new long[]{1, 256})),
                spec("smelt", List.of("item", "count"), Map.of("count", new long[]{1, 256})),
                spec("equip", List.of("item"), Map.of()),
                spec("deposit", List.of("item", "count"),
                        Map.of("count", new long[]{1, 256}, "radius", new long[]{1, 16})),
                spec("withdraw", List.of("item", "count"),
                        Map.of("count", new long[]{1, 256}, "radius", new long[]{1, 16})),
                spec("sort_chest", List.of(), Map.of("radius", new long[]{1, 16})),
                spec("unload", List.of(), Map.of("radius", new long[]{1, 16})),
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
                        + "'nearest' — ближайший в радиусе");
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

    private static ParamSpec param(ScriptSpec spec, String name) {
        return spec.params().stream().filter(param -> param.name().equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError("В скрипте " + spec.id() + " нет параметра " + name));
    }
}