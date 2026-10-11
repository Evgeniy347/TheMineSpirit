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
                "equip", "deposit", "withdraw", "sort_chest", "unload", "acquire", "attack", "defend", "retreat",
                "place_block",
                "fill", "clear_area", "build_schematic", "deliver", "meet", "say", "wait");

        assertEquals(expected, catalog.all().stream().map(ScriptSpec::id).toList(),
                "Каталог по умолчанию обязан содержать ровно оговорённый набор скриптов: пропущенный скрипт "
                        + "планировщик не найдёт, лишний — разойдётся с /v1/capabilities");
    }

    @Test
    void defaultCatalog_DepositAndWithdraw_ContainerIsOptionalNearest() {
        ScriptSpec deposit = CoreScripts.defaultCatalog().find("deposit").orElseThrow();
        ParamSpec container = param(deposit, "container");

        assertEquals(false, container.required(),
                "container в deposit необязателен: по умолчанию дух берёт ближайший подходящий контейнер, "
                        + "и план не обязан каждый раз называть координаты");
        assertEquals("nearest", container.defaultValue(),
                "Значение по умолчанию container определяет, какой контейнер использовать, если план "
                        + "молчит: 'nearest' — ближайший в радиусе");
    }

    @Test
    void defaultCatalog_ContainerScripts_RadiusWithinContainerLimits() {
        ScriptCatalog catalog = CoreScripts.defaultCatalog();
        for (String id : List.of("deposit", "withdraw", "sort_chest", "unload")) {
            ScriptSpec script = catalog.find(id).orElseThrow();
            ParamSpec radius = param(script, "radius");

            assertEquals(false, radius.required(),
                    "radius у скрипта '" + id + "' необязателен: без него действует радиус по умолчанию "
                            + CatalogLimits.CONTAINER_RADIUS_DEFAULT);
            assertEquals(CatalogLimits.CONTAINER_RADIUS_DEFAULT, radius.defaultValue(),
                    "Радиус поиска контейнера у '" + id + "' по умолчанию "
                            + CatalogLimits.CONTAINER_RADIUS_DEFAULT + ": ближайший контейнер обычно "
                            + "рядом с духом");
            assertTrue(ParamType.INT == radius.type() && CatalogLimits.CONTAINER_RADIUS_MIN <=
                            CatalogLimits.CONTAINER_RADIUS_DEFAULT
                            && CatalogLimits.CONTAINER_RADIUS_DEFAULT <= CatalogLimits.CONTAINER_RADIUS_MAX,
                    "Радиус поиска контейнера у '" + id + "' — целое в [" + CatalogLimits.CONTAINER_RADIUS_MIN
                            + ", " + CatalogLimits.CONTAINER_RADIUS_MAX + "] и лежит внутри: иначе "
                            + "валидатор отверг бы значение по умолчанию");
        }
    }

    @Test
    void defaultCatalog_Unload_ForceDefaultsToFalseAndTriggerSlotsDefined() {
        ScriptSpec unload = CoreScripts.defaultCatalog().find("unload").orElseThrow();
        ParamSpec force = param(unload, "force");

        assertEquals(false, force.required(),
                "force у unload необязателен: без него разгрузка срабатывает только при заполненном "
                        + "инвентаре, а не по каждой мелочи");
        assertEquals(false, force.defaultValue(),
                "force по умолчанию false: разгрузка «без фанатизма» не трогает инвентарь, "
                        + "пока занято меньше " + CatalogLimits.UNLOAD_TRIGGER_SLOTS + " слотов");
        assertEquals(24, CatalogLimits.UNLOAD_TRIGGER_SLOTS,
                "Порог разгрузки — 24 слота из 36: дух тащит добро домой, только когда в инвентаре "
                        + "дейсно не осталось места");
    }

    @Test
    void defaultCatalog_CollectDrops_OverflowDefaultsToByValue() {
        ScriptSpec collectDrops = CoreScripts.defaultCatalog().find("collect_drops").orElseThrow();
        ParamSpec overflow = param(collectDrops, "overflow");

        assertEquals(false, overflow.required(),
                "overflow у collect_drops необязателен: по умолчанию действует поведение «берём ценное, "
                        + "дешёвое оставляем»");
        assertEquals(List.of("fail", "by_value"), overflow.values(),
                "У overflow два режима: fail — прежний отказ при полном инвентаре, by_value — подмена "
                        + "по ценности");
        assertEquals("by_value", overflow.defaultValue(),
                "По умолчанию collect_drops работает по ценности: дешёвое оставляется, ценное берётся, "
                        + "а не полный отказ");
    }

    @Test
    void defaultCatalog_Equip_TakesOnlyItemPattern() {
        ScriptSpec equip = CoreScripts.defaultCatalog().find("equip").orElseThrow();

        assertEquals(List.of("item"), equip.params().stream().map(ParamSpec::name).toList(),
                "У equip один обязательный параметр item: по нему шаблону ('*_pickaxe') дух выбирает, "
                        + "что надевать, и берёт самый ценный подходящий");
    }

    private static ParamSpec param(ScriptSpec spec, String name) {
        return spec.params().stream().filter(param -> param.name().equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError("В скрипте " + spec.id() + " нет параметра " + name));
    }
}
