package com.theminespirit.core.script;

import static com.theminespirit.core.script.ScriptCatalogFixturesData.*;
import static com.theminespirit.core.script.ScriptCatalogTestsSupport.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.farming.api.CropKind;
import com.theminespirit.core.script.api.CatalogLimits;
import com.theminespirit.core.script.api.ParamSpec;
import com.theminespirit.core.script.api.ParamType;
import com.theminespirit.core.script.api.ScriptCatalog;
import com.theminespirit.core.script.api.ScriptSpec;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScriptCatalogPart4Tests {
    @Test
    void defaultCatalog_HarvestCrop_CropEnumDefaultsToAnyWithReplant() {
        ScriptSpec harvestCrop =
                CoreScripts.defaultCatalog().find("harvest_crop").orElseThrow();
        ParamSpec crop = param(harvestCrop, "crop");
        ParamSpec radius = param(harvestCrop, "radius");
        ParamSpec replant = param(harvestCrop, "replant");
        assertEquals(
                ParamType.ENUM,
                crop.type(),
                "crop в harvest_crop — enum: валидатор обязан ловить опечатку до запуска, а не искать чужой посев Иначе тест defaultCatalog_HarvestCrop_CropEnumDefaultsToAnyWithReplant перестанет ловить эту поломку. При смене defaultCatalog_HarvestCrop_CropEnumDefaultsToAnyWithReplant проверь это утверждение.");
        assertEquals(
                CropKind.names(),
                crop.values(),
                "Список допустимых crop обязан совпадать с видами CropKind: иначе каталог обещает один набор, "
                        + "а ядро знает другой При смене defaultCatalog_HarvestCrop_CropEnumDefaultsToAnyWithReplant проверь это утверждение.");
        assertEquals(
                false,
                crop.required(),
                "crop в harvest_crop необязателен: без него собирается любой зрелый посев Иначе тест defaultCatalog_HarvestCrop_CropEnumDefaultsToAnyWithReplant перестанет ловить эту поломку. При смене defaultCatalog_HarvestCrop_CropEnumDefaultsToAnyWithReplant проверь это утверждение.");
        assertEquals(
                "any",
                crop.defaultValue(),
                "crop по умолчанию 'any': умолчание живёт в каталоге, а не в маппинге Иначе тест defaultCatalog_HarvestCrop_CropEnumDefaultsToAnyWithReplant перестанет ловить эту поломку. При смене defaultCatalog_HarvestCrop_CropEnumDefaultsToAnyWithReplant проверь это утверждение.");
        assertEquals(
                false,
                radius.required(),
                "radius в harvest_crop необязателен: по умолчанию поиск идёт в радиусе из каталога Иначе тест defaultCatalog_HarvestCrop_CropEnumDefaultsToAnyWithReplant перестанет ловить эту поломку. При смене defaultCatalog_HarvestCrop_CropEnumDefaultsToAnyWithReplant проверь это утверждение.");
        assertEquals(
                CatalogLimits.FARM_RADIUS_DEFAULT,
                radius.defaultValue(),
                "radius в harvest_crop по умолчанию " + CatalogLimits.FARM_RADIUS_DEFAULT
                        + ": значение задаёт каталог, чтобы адаптер не дублировал умолчание Иначе тест defaultCatalog_HarvestCrop_CropEnumDefaultsToAnyWithReplant перестанет ловить эту поломку. При смене defaultCatalog_HarvestCrop_CropEnumDefaultsToAnyWithReplant проверь это утверждение.");
        assertEquals(
                ParamType.BOOL,
                replant.type(),
                "replant в harvest_crop — bool: пересадка либо есть, либо нет Иначе тест defaultCatalog_HarvestCrop_CropEnumDefaultsToAnyWithReplant перестанет ловить эту поломку. При смене defaultCatalog_HarvestCrop_CropEnumDefaultsToAnyWithReplant проверь это утверждение.");
        assertEquals(
                false,
                replant.required(),
                "replant в harvest_crop необязателен: самоподдерживающаяся грядка — поведение по умолчанию Иначе тест defaultCatalog_HarvestCrop_CropEnumDefaultsToAnyWithReplant перестанет ловить эту поломку. При смене defaultCatalog_HarvestCrop_CropEnumDefaultsToAnyWithReplant проверь это утверждение.");
        assertEquals(
                true,
                replant.defaultValue(),
                "replant по умолчанию true: каждая собранная клетка тут же засаживается заново Иначе тест defaultCatalog_HarvestCrop_CropEnumDefaultsToAnyWithReplant перестанет ловить эту поломку. При смене defaultCatalog_HarvestCrop_CropEnumDefaultsToAnyWithReplant проверь это утверждение.");
    }

    @Test
    void defaultCatalog_PlantCrop_CropRequiredWithoutAny() {
        ScriptSpec plantCrop = CoreScripts.defaultCatalog().find("plant_crop").orElseThrow();
        ParamSpec crop = param(plantCrop, "crop");
        ParamSpec count = param(plantCrop, "count");
        assertEquals(
                ParamType.ENUM,
                crop.type(),
                "crop в plant_crop — enum: опечатка в виде обязана отклоняться валидатором до запуска Иначе тест defaultCatalog_PlantCrop_CropRequiredWithoutAny перестанет ловить эту поломку. При смене defaultCatalog_PlantCrop_CropRequiredWithoutAny проверь это утверждение.");
        assertEquals(
                CropKind.plantableNames(),
                crop.values(),
                "Список допустимых crop обязан совпадать с сажаемыми видами без any: план «посади что-нибудь» "
                        + "не говорит, какие семена тратить Иначе тест defaultCatalog_PlantCrop_CropRequiredWithoutAny перестанет ловить эту поломку. При смене defaultCatalog_PlantCrop_CropRequiredWithoutAny проверь это утверждение.");
        assertEquals(
                true,
                crop.required(),
                "crop в plant_crop обязателен: без вида скрипт не знает, какие семена брать из инвентаря Иначе тест defaultCatalog_PlantCrop_CropRequiredWithoutAny перестанет ловить эту поломку. При смене defaultCatalog_PlantCrop_CropRequiredWithoutAny проверь это утверждение.");
        assertEquals(
                CatalogLimits.PLANT_COUNT_DEFAULT,
                count.defaultValue(),
                "count в plant_crop по умолчанию " + CatalogLimits.PLANT_COUNT_DEFAULT
                        + ": значение задаёт каталог, чтобы шаблон не дублировал умолчание Иначе тест defaultCatalog_PlantCrop_CropRequiredWithoutAny перестанет ловить эту поломку. При смене defaultCatalog_PlantCrop_CropRequiredWithoutAny проверь это утверждение.");
    }

    @Test
    void defaultCatalog_TillAndBoneMeal_RadiusAndCountDefaults() {
        ScriptSpec till = CoreScripts.defaultCatalog().find("till").orElseThrow();
        ScriptSpec boneMeal = CoreScripts.defaultCatalog().find("bone_meal").orElseThrow();
        assertEquals(
                CatalogLimits.FARM_RADIUS_DEFAULT,
                param(till, "radius").defaultValue(),
                "radius в till по умолчанию " + CatalogLimits.FARM_RADIUS_DEFAULT
                        + ": значение задаёт каталог, чтобы адаптер не дублировал умолчание Иначе тест defaultCatalog_TillAndBoneMeal_RadiusAndCountDefaults перестанет ловить эту поломку. При смене defaultCatalog_TillAndBoneMeal_RadiusAndCountDefaults проверь это утверждение.");
        assertEquals(
                CatalogLimits.TILL_COUNT_DEFAULT,
                param(till, "count").defaultValue(),
                "count в till по умолчанию  Иначе тест defaultCatalog_TillAndBoneMeal_RadiusAndCountDefaults перестанет ловить эту поломку. При смене defaultCatalog_TillAndBoneMeal_RadiusAndCountDefaults проверь это утверждение."
                        + CatalogLimits.TILL_COUNT_DEFAULT);
        assertEquals(
                "any",
                param(boneMeal, "crop").defaultValue(),
                "crop в bone_meal по умолчанию 'any': без вида подкармливается любой растущий посев Иначе тест defaultCatalog_TillAndBoneMeal_RadiusAndCountDefaults перестанет ловить эту поломку. При смене defaultCatalog_TillAndBoneMeal_RadiusAndCountDefaults проверь это утверждение.");
        assertEquals(
                CatalogLimits.BONE_MEAL_COUNT_DEFAULT,
                param(boneMeal, "count").defaultValue(),
                "count в bone_meal по умолчанию  Иначе тест defaultCatalog_TillAndBoneMeal_RadiusAndCountDefaults перестанет ловить эту поломку. При смене defaultCatalog_TillAndBoneMeal_RadiusAndCountDefaults проверь это утверждение."
                        + CatalogLimits.BONE_MEAL_COUNT_DEFAULT);
        assertEquals(
                2,
                till.params().size(),
                "У till ровно два параметра, radius и count: лишний параметр попал бы в capabilities Иначе тест defaultCatalog_TillAndBoneMeal_RadiusAndCountDefaults перестанет ловить эту поломку. При смене defaultCatalog_TillAndBoneMeal_RadiusAndCountDefaults проверь это утверждение.");
    }

    @Test
    void defaultCatalog_DepositAndWithdraw_ContainerIsOptionalNearest() {
        ScriptSpec deposit = CoreScripts.defaultCatalog().find("deposit").orElseThrow();
        ParamSpec container = param(deposit, "container");
        assertEquals(
                false,
                container.required(),
                "container в deposit необязателен: по умолчанию дух берёт ближайший подходящий контейнер, "
                        + "и план не обязан каждый раз называть координаты Иначе тест defaultCatalog_DepositAndWithdraw_ContainerIsOptionalNearest перестанет ловить эту поломку. При смене defaultCatalog_DepositAndWithdraw_ContainerIsOptionalNearest проверь это утверждение.");
        assertEquals(
                "nearest",
                container.defaultValue(),
                "Значение по умолчанию container решает, какой контейнер использовать, если план молчит: "
                        + "'nearest' — ближайший в радиусе. Для deposit оставлен ближайший, 'auto' доступен явно Иначе тест defaultCatalog_DepositAndWithdraw_ContainerIsOptionalNearest перестанет ловить эту поломку. При смене defaultCatalog_DepositAndWithdraw_ContainerIsOptionalNearest проверь это утверждение.");
    }

    @Test
    void defaultCatalog_Unload_ContainerDefaultsToAuto() {
        ScriptSpec unload = CoreScripts.defaultCatalog().find("unload").orElseThrow();
        ParamSpec container = param(unload, "container");
        assertEquals(
                false,
                container.required(),
                "container в unload необязателен: при разгрузке дух сам раскладывает предметы по сундукам Иначе тест defaultCatalog_Unload_ContainerDefaultsToAuto перестанет ловить эту поломку. При смене defaultCatalog_Unload_ContainerDefaultsToAuto проверь это утверждение.");
        assertEquals(
                "auto",
                container.defaultValue(),
                "Значение по умолчанию container у unload — 'auto': вернувшись из шахты, дух докладывает "
                        + "стакующиеся предметы в сундук, где уже лежит такой предмет с неполным стаком, "
                        + "а не в ближайший попавшийся Иначе тест defaultCatalog_Unload_ContainerDefaultsToAuto перестанет ловить эту поломку. При смене defaultCatalog_Unload_ContainerDefaultsToAuto проверь это утверждение.");
    }

    @Test
    void defaultCatalog_ContainerScripts_RadiusWithinContainerLimits() {
        ScriptCatalog catalog = CoreScripts.defaultCatalog();
        for (String id : List.of("deposit", "withdraw", "sort_chest", "unload")) {
            ScriptSpec script = catalog.find(id).orElseThrow();
            ParamSpec radius = param(script, "radius");
            assertEquals(
                    false,
                    radius.required(),
                    "radius у скрипта '" + id + "' необязателен: без него действует радиус 8, "
                            + "и план не обязан его называть Иначе тест defaultCatalog_ContainerScripts_RadiusWithinContainerLimits перестанет ловить эту поломку. При смене defaultCatalog_ContainerScripts_RadiusWithinContainerLimits проверь это утверждение.");
            assertEquals(
                    8,
                    radius.defaultValue(),
                    "Радиус поиска контейнера у '" + id + "' по умолчанию 8: ближайший контейнер "
                            + "обычно рядом с духом Иначе тест defaultCatalog_ContainerScripts_RadiusWithinContainerLimits перестанет ловить эту поломку. При смене defaultCatalog_ContainerScripts_RadiusWithinContainerLimits проверь это утверждение.");
            assertEquals(
                    ParamType.INT,
                    radius.type(),
                    "Радиус поиска контейнера у '" + id
                            + "' — целое: дробный радиус сундука не имеет смысла Иначе тест defaultCatalog_ContainerScripts_RadiusWithinContainerLimits перестанет ловить эту поломку. При смене defaultCatalog_ContainerScripts_RadiusWithinContainerLimits проверь это утверждение.");
            assertEquals(
                    1.0,
                    radius.min(),
                    "Радиус поиска контейнера у '" + id + "' начинается с 1: нулевой радиус нашёл бы "
                            + "только сундук в ногах духа Иначе тест defaultCatalog_ContainerScripts_RadiusWithinContainerLimits перестанет ловить эту поломку. При смене defaultCatalog_ContainerScripts_RadiusWithinContainerLimits проверь это утверждение.");
            assertEquals(
                    16.0,
                    radius.max(),
                    "Радиус поиска контейнера у '" + id
                            + "' ограничен 16: дальше дух уходит из базовой зоны Иначе тест defaultCatalog_ContainerScripts_RadiusWithinContainerLimits перестанет ловить эту поломку. При смене defaultCatalog_ContainerScripts_RadiusWithinContainerLimits проверь это утверждение.");
        }
    }
}
