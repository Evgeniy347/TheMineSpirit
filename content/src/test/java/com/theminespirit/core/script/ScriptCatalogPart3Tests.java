package com.theminespirit.core.script;

import static com.theminespirit.core.script.ScriptCatalogFixturesData.*;
import static com.theminespirit.core.script.ScriptCatalogTestsSupport.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.mining.DescentData;
import com.theminespirit.core.mining.DescentDirection;
import com.theminespirit.core.observation.ScanRadius;
import com.theminespirit.core.script.api.CatalogLimits;
import com.theminespirit.core.script.api.ParamSpec;
import com.theminespirit.core.script.api.ParamType;
import com.theminespirit.core.script.api.ScriptSpec;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScriptCatalogPart3Tests {
    @Test
    void defaultCatalog_DigDescent_DirectionRequiredYOptionalWithDefaultFromData() {
        ScriptSpec digDescent = CoreScripts.defaultCatalog().find("dig_descent").orElseThrow();
        ParamSpec direction = param(digDescent, "direction");
        assertEquals(
                ParamType.ENUM,
                direction.type(),
                "direction в dig_descent — enum: валидатор обязан ловить опечатку до запуска, а не копать в никуда Иначе тест defaultCatalog_DigDescent_DirectionRequiredYOptionalWithDefaultFromData перестанет ловить эту поломку. При смене defaultCatalog_DigDescent_DirectionRequiredYOptionalWithDefaultFromData проверь это утверждение.");
        assertEquals(
                DescentDirection.names(),
                direction.values(),
                "Список direction обязан совпадать с осями DescentDirection: иначе каталог обещает направление, "
                        + "которое ядро не знает При смене defaultCatalog_DigDescent_DirectionRequiredYOptionalWithDefaultFromData проверь это утверждение.");
        assertEquals(
                true,
                direction.required(),
                "direction в dig_descent обязателен: без направления неизвестно, в какую сторону копать тоннель Иначе тест defaultCatalog_DigDescent_DirectionRequiredYOptionalWithDefaultFromData перестанет ловить эту поломку. При смене defaultCatalog_DigDescent_DirectionRequiredYOptionalWithDefaultFromData проверь это утверждение.");
        ParamSpec y = param(digDescent, "y");
        assertEquals(
                false,
                y.required(),
                "y в dig_descent необязателен: без него спуск идёт до целевого этажа из данных Иначе тест defaultCatalog_DigDescent_DirectionRequiredYOptionalWithDefaultFromData перестанет ловить эту поломку. При смене defaultCatalog_DigDescent_DirectionRequiredYOptionalWithDefaultFromData проверь это утверждение.");
        assertEquals(
                DescentData.defaults().targetY(),
                y.defaultValue(),
                "Default y обязан совпадать с данными спуска: PlanValidator подставляет его в план, "
                        + "а игрок видит умолчание в /v1/capabilities Иначе тест defaultCatalog_DigDescent_DirectionRequiredYOptionalWithDefaultFromData перестанет ловить эту поломку. При смене defaultCatalog_DigDescent_DirectionRequiredYOptionalWithDefaultFromData проверь это утверждение.");
        assertEquals(
                (double) CatalogLimits.WORLD_Y_MIN,
                y.min(),
                "Нижняя граница y обязана совпадать с дном мира каталога Иначе тест defaultCatalog_DigDescent_DirectionRequiredYOptionalWithDefaultFromData перестанет ловить эту поломку. При смене defaultCatalog_DigDescent_DirectionRequiredYOptionalWithDefaultFromData проверь это утверждение.");
        assertEquals(
                (double) CatalogLimits.WORLD_Y_MAX,
                y.max(),
                "Верхняя граница y обязана совпадать с потолком мира каталога Иначе тест defaultCatalog_DigDescent_DirectionRequiredYOptionalWithDefaultFromData перестанет ловить эту поломку. При смене defaultCatalog_DigDescent_DirectionRequiredYOptionalWithDefaultFromData проверь это утверждение.");
    }

    @Test
    void defaultCatalog_Gather_CountRequiredRadiusDefaults() {
        ScriptSpec gather = CoreScripts.defaultCatalog().find("gather").orElseThrow();
        ParamSpec count = param(gather, "count");
        ParamSpec radius = param(gather, "radius");
        assertEquals(
                true,
                count.required(),
                "count в gather обязателен: без числа сбор не имеет цели, в отличие от mine_vein с умолчанием 1 Иначе тест defaultCatalog_Gather_CountRequiredRadiusDefaults перестанет ловить эту поломку. При смене defaultCatalog_Gather_CountRequiredRadiusDefaults проверь это утверждение.");
        assertEquals(
                false,
                radius.required(),
                "radius в gather необязателен: по умолчанию поиск идёт в радиусе из каталога Иначе тест defaultCatalog_Gather_CountRequiredRadiusDefaults перестанет ловить эту поломку. При смене defaultCatalog_Gather_CountRequiredRadiusDefaults проверь это утверждение.");
        assertEquals(
                CatalogLimits.HARVEST_RADIUS_DEFAULT,
                radius.defaultValue(),
                "radius в gather по умолчанию " + CatalogLimits.HARVEST_RADIUS_DEFAULT
                        + ": значение задаёт каталог, чтобы адаптер не дублировал умолчание Иначе тест defaultCatalog_Gather_CountRequiredRadiusDefaults перестанет ловить эту поломку. При смене defaultCatalog_Gather_CountRequiredRadiusDefaults проверь это утверждение.");
    }

    @Test
    void defaultCatalog_CraftAndSmelt_StationRadiusIsOptionalIntWithDefaultSixteen() {
        for (String id : List.of("craft", "smelt")) {
            ParamSpec radius = param(CoreScripts.defaultCatalog().find(id).orElseThrow(), "radius");
            assertEquals(
                    false,
                    radius.required(),
                    "radius у '" + id
                            + "' необязателен: умолчание задаёт каталог, и план не обязан называть радиус Иначе тест defaultCatalog_CraftAndSmelt_StationRadiusIsOptionalIntWithDefaultSixteen перестанет ловить эту поломку. При смене defaultCatalog_CraftAndSmelt_StationRadiusIsOptionalIntWithDefaultSixteen проверь это утверждение.");
            assertEquals(
                    16,
                    radius.defaultValue(),
                    "Радиус поиска станции у '" + id
                            + "' по умолчанию 16: верстак или печь обычно на базе, рядом Иначе тест defaultCatalog_CraftAndSmelt_StationRadiusIsOptionalIntWithDefaultSixteen перестанет ловить эту поломку. При смене defaultCatalog_CraftAndSmelt_StationRadiusIsOptionalIntWithDefaultSixteen проверь это утверждение.");
            assertEquals(
                    ParamType.INT,
                    radius.type(),
                    "Радиус поиска станции у '" + id
                            + "' — целое: дробный радиус бессмыслен Иначе тест defaultCatalog_CraftAndSmelt_StationRadiusIsOptionalIntWithDefaultSixteen перестанет ловить эту поломку. При смене defaultCatalog_CraftAndSmelt_StationRadiusIsOptionalIntWithDefaultSixteen проверь это утверждение.");
            assertEquals(
                    1.0,
                    radius.min(),
                    "Радиус поиска станции у '" + id
                            + "' начинается с 1: нулевой не найдёт и станцию под ногами При смене defaultCatalog_CraftAndSmelt_StationRadiusIsOptionalIntWithDefaultSixteen проверь это утверждение.");
            assertEquals(
                    32.0,
                    radius.max(),
                    "Радиус поиска станции у '" + id
                            + "' ограничен 32: дальше поиск станции бьёт по серверному тику Иначе тест defaultCatalog_CraftAndSmelt_StationRadiusIsOptionalIntWithDefaultSixteen перестанет ловить эту поломку. При смене defaultCatalog_CraftAndSmelt_StationRadiusIsOptionalIntWithDefaultSixteen проверь это утверждение.");
        }
    }

    @Test
    void defaultCatalog_CollectDrops_RadiusHasBoundsAndDefaultFilterIsOptionalList() {
        ScriptSpec collectDrops =
                CoreScripts.defaultCatalog().find("collect_drops").orElseThrow();
        ParamSpec radius = param(collectDrops, "radius");
        ParamSpec filter = param(collectDrops, "filter");
        assertEquals(
                1.0,
                radius.min(),
                "Радиус подбора начинается с 1 блока: нулевой радиус подбирал бы только предметы под духом Иначе тест defaultCatalog_CollectDrops_RadiusHasBoundsAndDefaultFilterIsOptionalList перестанет ловить эту поломку. При смене defaultCatalog_CollectDrops_RadiusHasBoundsAndDefaultFilterIsOptionalList проверь это утверждение.");
        assertEquals(
                16.0,
                radius.max(),
                "Радиус подбора ограничен 16 блоками: больший радиус тащит духа за предметами через полкарты Иначе тест defaultCatalog_CollectDrops_RadiusHasBoundsAndDefaultFilterIsOptionalList перестанет ловить эту поломку. При смене defaultCatalog_CollectDrops_RadiusHasBoundsAndDefaultFilterIsOptionalList проверь это утверждение.");
        assertEquals(
                8,
                radius.defaultValue(),
                "Радиус подбора по умолчанию 8: значение живёт в каталоге Иначе тест defaultCatalog_CollectDrops_RadiusHasBoundsAndDefaultFilterIsOptionalList перестанет ловить эту поломку. При смене defaultCatalog_CollectDrops_RadiusHasBoundsAndDefaultFilterIsOptionalList проверь это утверждение.");
        assertEquals(
                false,
                radius.required(),
                "radius в collect_drops необязателен: умолчание подставляет валидатор Иначе тест defaultCatalog_CollectDrops_RadiusHasBoundsAndDefaultFilterIsOptionalList перестанет ловить эту поломку. При смене defaultCatalog_CollectDrops_RadiusHasBoundsAndDefaultFilterIsOptionalList проверь это утверждение.");
        assertEquals(
                ParamType.STRING_LIST,
                filter.type(),
                "filter в collect_drops — список шаблонов id предметов Иначе тест defaultCatalog_CollectDrops_RadiusHasBoundsAndDefaultFilterIsOptionalList перестанет ловить эту поломку. При смене defaultCatalog_CollectDrops_RadiusHasBoundsAndDefaultFilterIsOptionalList проверь это утверждение.");
        assertEquals(
                false,
                filter.required(),
                "filter в collect_drops необязателен: без него подбирается всё Иначе тест defaultCatalog_CollectDrops_RadiusHasBoundsAndDefaultFilterIsOptionalList перестанет ловить эту поломку. При смене defaultCatalog_CollectDrops_RadiusHasBoundsAndDefaultFilterIsOptionalList проверь это утверждение.");
        assertEquals(
                3,
                collectDrops.params().size(),
                "У collect_drops ровно три параметра, radius, filter и overflow: лишний параметр попал бы "
                        + "в capabilities, а забытый не подставил бы умолчание переполнения Иначе тест defaultCatalog_CollectDrops_RadiusHasBoundsAndDefaultFilterIsOptionalList перестанет ловить эту поломку. При смене defaultCatalog_CollectDrops_RadiusHasBoundsAndDefaultFilterIsOptionalList проверь это утверждение.");
    }

    @Test
    void defaultCatalog_Scan_RadiusUsesScanRadiusLimitsAndFilterIsOptionalList() {
        ScriptSpec scan = CoreScripts.defaultCatalog().find("scan").orElseThrow();
        ParamSpec radius = param(scan, "radius");
        assertEquals(
                (double) ScanRadius.MAX,
                radius.max(),
                "Радиус scan ограничен ScanRadius.MAX: скан большего радиуса кладёт серверный тик Иначе тест defaultCatalog_Scan_RadiusUsesScanRadiusLimitsAndFilterIsOptionalList перестанет ловить эту поломку. При смене defaultCatalog_Scan_RadiusUsesScanRadiusLimitsAndFilterIsOptionalList проверь это утверждение.");
        assertEquals(
                ParamType.STRING_LIST,
                param(scan, "filter").type(),
                "filter в scan — список шаблонов id блоков Иначе тест defaultCatalog_Scan_RadiusUsesScanRadiusLimitsAndFilterIsOptionalList перестанет ловить эту поломку. При смене defaultCatalog_Scan_RadiusUsesScanRadiusLimitsAndFilterIsOptionalList проверь это утверждение.");
        assertEquals(
                false,
                param(scan, "filter").required(),
                "filter в scan необязателен: без него видны все блоки Иначе тест defaultCatalog_Scan_RadiusUsesScanRadiusLimitsAndFilterIsOptionalList перестанет ловить эту поломку. При смене defaultCatalog_Scan_RadiusUsesScanRadiusLimitsAndFilterIsOptionalList проверь это утверждение.");
    }
}
