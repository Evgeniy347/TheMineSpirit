package com.theminespirit.core.script;

import static com.theminespirit.core.script.ScriptCatalogFixturesData.*;
import static com.theminespirit.core.script.ScriptCatalogTestsSupport.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.mining.api.OreKind;
import com.theminespirit.core.script.api.CatalogLimits;
import com.theminespirit.core.script.api.ParamSpec;
import com.theminespirit.core.script.api.ParamType;
import com.theminespirit.core.script.api.ScriptCatalog;
import com.theminespirit.core.script.api.ScriptSpec;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ScriptCatalogPart2Tests {
    @Test
    void defaultCatalog_PillarDown_HasNoRequiredParams() {
        ScriptSpec pillarDown = CoreScripts.defaultCatalog().find("pillar_down").orElseThrow();
        List<String> required = pillarDown.params().stream()
                .filter(ParamSpec::required)
                .map(ParamSpec::name)
                .toList();
        assertEquals(
                List.of(),
                required,
                "pillar_down не требует параметров: столб ищется по своим помеченным блокам под духом, "
                        + "а лишний обязательный параметр отверг бы легитимный план Иначе тест defaultCatalog_PillarDown_HasNoRequiredParams перестанет ловить эту поломку. При смене defaultCatalog_PillarDown_HasNoRequiredParams проверь это утверждение.");
    }

    @Test
    void defaultCatalog_ChopTarget_CoordinatesRequiredReplantOptional() {
        ScriptSpec chopTarget = CoreScripts.defaultCatalog().find("chop_target").orElseThrow();
        assertEquals(
                List.of("x", "y", "z"),
                chopTarget.params().stream()
                        .filter(ParamSpec::required)
                        .map(ParamSpec::name)
                        .toList(),
                "У chop_target координаты ствола обязательны, а replant — нет: без клетки дерево не определить, "
                        + "а посадка саженца необязательна Иначе тест defaultCatalog_ChopTarget_CoordinatesRequiredReplantOptional перестанет ловить эту поломку. При смене defaultCatalog_ChopTarget_CoordinatesRequiredReplantOptional проверь это утверждение.");
        ParamSpec replant = param(chopTarget, "replant");
        assertEquals(
                ParamType.BOOL,
                replant.type(),
                "replant в chop_target — булев флаг: посадить саженец или нет, третьего не дано Иначе тест defaultCatalog_ChopTarget_CoordinatesRequiredReplantOptional перестанет ловить эту поломку. При смене defaultCatalog_ChopTarget_CoordinatesRequiredReplantOptional проверь это утверждение.");
        assertEquals(
                false,
                replant.defaultValue(),
                "replant в chop_target по умолчанию false, как у chop_tree: посадка — явное желание, а не случайность Иначе тест defaultCatalog_ChopTarget_CoordinatesRequiredReplantOptional перестанет ловить эту поломку. При смене defaultCatalog_ChopTarget_CoordinatesRequiredReplantOptional проверь это утверждение.");
        assertEquals(
                5,
                chopTarget.params().size(),
                "У chop_target ровно пять параметров: x, y, z, replant и orchard. Лишний попал бы в capabilities, "
                        + "а забытый не подставил бы умолчание Иначе тест defaultCatalog_ChopTarget_CoordinatesRequiredReplantOptional перестанет ловить эту поломку. При смене defaultCatalog_ChopTarget_CoordinatesRequiredReplantOptional проверь это утверждение.");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("com.theminespirit.core.script.ScriptCatalogTestsSupport#scriptSpecRows")
    void catalog_ScriptTable_RowMatchesSpec(ScriptExpectation expected) {
        ScriptSpec spec = CoreScripts.defaultCatalog()
                .find(expected.id())
                .orElseThrow(() -> new AssertionError("В каталоге нет скрипта " + expected.id()));
        List<String> required = spec.params().stream()
                .filter(ParamSpec::required)
                .map(ParamSpec::name)
                .toList();
        assertEquals(
                expected.required(),
                required,
                "Набор обязательных параметров скрипта " + expected.id() + " обязан совпадать с таблицей: "
                        + "варианты плана без обязательного параметра валидатор должен отклонять, а лишний "
                        + "обязательный параметр отвергнет легитимный план Иначе тест catalog_ScriptTable_RowMatchesSpec перестанет ловить эту поломку. При смене catalog_ScriptTable_RowMatchesSpec проверь это утверждение.");
        expected.bounds().forEach((name, minMax) -> {
            ParamSpec param = param(spec, name);
            assertEquals(
                    (double) minMax[0],
                    param.min(),
                    "Нижняя граница параметра " + name + " скрипта " + expected.id() + " обязана совпадать с "
                            + "таблицей: отклонённый план между границами или принятый за границами ломает лимиты плана При смене catalog_ScriptTable_RowMatchesSpec проверь это утверждение.");
            assertEquals(
                    (double) minMax[1],
                    param.max(),
                    "Верхняя граница параметра " + name + " скрипта " + expected.id() + " обязана совпадать с "
                            + "таблицей: отклонённый план между границами или принятый за границами ломает лимиты плана При смене catalog_ScriptTable_RowMatchesSpec проверь это утверждение.");
        });
    }

    @Test
    void defaultCatalog_ScriptTable_CoversExactlyAllIds() {
        ScriptCatalog catalog = CoreScripts.defaultCatalog();
        List<String> tableIds = scriptSpecRows()
                .map(arguments -> ((ScriptExpectation) arguments.get()[0]).id())
                .toList();
        assertEquals(
                tableIds,
                catalog.all().stream().map(ScriptSpec::id).toList(),
                "Таблица каталога обязана покрывать ровно все скрипты defaultCatalog: пропущенный скрипт "
                        + "планировщик не найдёт, лишний разойдётся с /v1/capabilities При смене defaultCatalog_ScriptTable_CoversExactlyAllIds проверь это утверждение.");
    }

    @Test
    void defaultCatalog_ClearArea_DemolishOptionalFalse() {
        ScriptSpec clearArea = CoreScripts.defaultCatalog().find("clear_area").orElseThrow();
        ParamSpec demolish = param(clearArea, "demolish");
        assertEquals(
                ParamType.BOOL,
                demolish.type(),
                "demolish в clear_area — флаг: перенос сносит свою постройку, обычный разбор — нет Иначе тест defaultCatalog_ClearArea_DemolishOptionalFalse перестанет ловить эту поломку. При смене defaultCatalog_ClearArea_DemolishOptionalFalse проверь это утверждение.");
        assertEquals(
                false,
                demolish.required(),
                "demolish необязателен: старые планы clear_area без флага остаются валидными Иначе тест defaultCatalog_ClearArea_DemolishOptionalFalse перестанет ловить эту поломку. При смене defaultCatalog_ClearArea_DemolishOptionalFalse проверь это утверждение.");
        assertEquals(
                false,
                demolish.defaultValue(),
                "По умолчанию demolish=false: защита от сноса чужого не должна отключаться молча Иначе тест defaultCatalog_ClearArea_DemolishOptionalFalse перестанет ловить эту поломку. При смене defaultCatalog_ClearArea_DemolishOptionalFalse проверь это утверждение.");
    }

    @Test
    void defaultCatalog_ClearArea_KeepGroundOptionalFalse() {
        ParamSpec keepGround =
                param(CoreScripts.defaultCatalog().find("clear_area").orElseThrow(), "keep_ground");
        assertEquals(
                ParamType.BOOL,
                keepGround.type(),
                "keep_ground в clear_area — флаг ямы подсыпки Иначе тест defaultCatalog_ClearArea_KeepGroundOptionalFalse перестанет ловить эту поломку. При смене defaultCatalog_ClearArea_KeepGroundOptionalFalse проверь это утверждение.");
        assertEquals(
                false,
                keepGround.required(),
                "keep_ground необязателен: старые планы остаются валидными Иначе тест defaultCatalog_ClearArea_KeepGroundOptionalFalse перестанет ловить эту поломку. При смене defaultCatalog_ClearArea_KeepGroundOptionalFalse проверь это утверждение.");
        assertEquals(
                false,
                keepGround.defaultValue(),
                "По умолчанию keep_ground=false: срезка холма обязана снимать землю Иначе тест defaultCatalog_ClearArea_KeepGroundOptionalFalse перестанет ловить эту поломку. При смене defaultCatalog_ClearArea_KeepGroundOptionalFalse проверь это утверждение.");
    }

    @Test
    void defaultCatalog_MineVein_OreEnumAndDefaults() {
        ScriptSpec mineVein = CoreScripts.defaultCatalog().find("mine_vein").orElseThrow();
        ParamSpec ore = param(mineVein, "ore");
        ParamSpec count = param(mineVein, "count");
        ParamSpec radius = param(mineVein, "radius");
        assertEquals(
                ParamType.ENUM,
                ore.type(),
                "ore в mine_vein — enum: валидатор обязан ловить опечатку до запуска, а не искать несуществующую жилу Иначе тест defaultCatalog_MineVein_OreEnumAndDefaults перестанет ловить эту поломку. При смене defaultCatalog_MineVein_OreEnumAndDefaults проверь это утверждение.");
        assertEquals(
                OreKind.names(),
                ore.values(),
                "Список допустимых ore обязан совпадать с видами OreKind: иначе каталог обещает один набор, а ядро знает другой При смене defaultCatalog_MineVein_OreEnumAndDefaults проверь это утверждение.");
        assertEquals(
                false,
                ore.required(),
                "ore в mine_vein необязателен: без него добывается любая руда Иначе тест defaultCatalog_MineVein_OreEnumAndDefaults перестанет ловить эту поломку. При смене defaultCatalog_MineVein_OreEnumAndDefaults проверь это утверждение.");
        assertEquals(
                "any",
                ore.defaultValue(),
                "enum ore по умолчанию 'any': умолчание живёт в каталоге, а не в маппинге — его видит "
                        + "/v1/capabilities и подставляет валидатор Иначе тест defaultCatalog_MineVein_OreEnumAndDefaults перестанет ловить эту поломку. При смене defaultCatalog_MineVein_OreEnumAndDefaults проверь это утверждение.");
        assertEquals(
                1,
                count.defaultValue(),
                "count в mine_vein по умолчанию 1: без числа добывается один блок руды Иначе тест defaultCatalog_MineVein_OreEnumAndDefaults перестанет ловить эту поломку. При смене defaultCatalog_MineVein_OreEnumAndDefaults проверь это утверждение.");
        assertEquals(
                CatalogLimits.HARVEST_RADIUS_DEFAULT,
                radius.defaultValue(),
                "radius в mine_vein по умолчанию " + CatalogLimits.HARVEST_RADIUS_DEFAULT
                        + ": значение задаёт каталог, чтобы шаблон не дублировал умолчание Иначе тест defaultCatalog_MineVein_OreEnumAndDefaults перестанет ловить эту поломку. При смене defaultCatalog_MineVein_OreEnumAndDefaults проверь это утверждение.");
    }
}
