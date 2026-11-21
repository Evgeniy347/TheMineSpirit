package com.theminespirit.core.script;

import static com.theminespirit.core.script.ScriptCatalogFixturesData.*;
import static com.theminespirit.core.script.ScriptCatalogTestsSupport.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.forestry.api.TreeSpecies;
import com.theminespirit.core.mining.PickaxeTiers;
import com.theminespirit.core.mining.api.OreKind;
import com.theminespirit.core.script.api.CatalogLimits;
import com.theminespirit.core.script.api.ParamSpec;
import com.theminespirit.core.script.api.ParamType;
import com.theminespirit.core.script.api.ScriptCatalog;
import com.theminespirit.core.script.api.ScriptRequirement;
import com.theminespirit.core.script.api.ScriptSpec;
import java.util.LinkedHashSet;
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
        assertTrue(
                found.isPresent(),
                "Каталог обязан находить скрипт по id: иначе планировщик не сможет вызвать известный скрипт При смене find_KnownId_ReturnsSpec проверь это утверждение.");
        assertEquals(
                "пройти",
                found.orElseThrow().summary(),
                "find возвращает описание именно запрошенного скрипта: перепутанный id приводит к вызову чужого скрипта Иначе тест find_KnownId_ReturnsSpec перестанет ловить эту поломку. При смене find_KnownId_ReturnsSpec проверь это утверждение.");
    }

    @Test
    void restrictedTo_SetInDifferentOrder_KeepsCatalogOrder() {
        ScriptCatalog catalog = ScriptCatalog.of(List.of(
                new ScriptSpec("scan", "первый", List.of(), List.of(), List.of()),
                new ScriptSpec("goto", "второй", List.of(), List.of(), List.of()),
                new ScriptSpec("follow", "третий", List.of(), List.of(), List.of())));

        ScriptCatalog subset = catalog.restrictedTo(new LinkedHashSet<>(List.of("follow", "scan")));

        assertEquals(
                List.of("scan", "follow"),
                subset.all().stream().map(ScriptSpec::id).toList(),
                "Скрипты подкаталога обязаны идти в порядке полного каталога, а не набора: /v1/capabilities "
                        + "перечисляет их в порядке каталога (docs/api/README.md), а IMPLEMENTED-таблица даёт другой порядок. "
                        + "Проверьте, что restrictedTo идёт по каталогу и отбирает по набору Иначе тест restrictedTo_SetInDifferentOrder_KeepsCatalogOrder перестанет ловить эту поломку. При смене restrictedTo_SetInDifferentOrder_KeepsCatalogOrder проверь это утверждение.");
    }

    @Test
    void defaultCatalog_Requirements_ReferenceExistingParamAndKnownTiers() {
        for (ScriptSpec spec : CoreScripts.defaultCatalog().all()) {
            List<String> names = spec.params().stream().map(ParamSpec::name).toList();
            for (ScriptRequirement requirement : spec.requires()) {
                assertTrue(
                        names.contains(requirement.param()),
                        "Требование скрипта " + spec.id() + " ссылается на параметр '" + requirement.param()
                                + "', которого нет в каталоге: проверка плана не найдёт значение и пропустит шаг При смене defaultCatalog_Requirements_ReferenceExistingParamAndKnownTiers проверь это утверждение.");
                requirement
                        .tiers()
                        .values()
                        .forEach(tier -> assertTrue(
                                tier >= ScriptRequirement.MIN_TIER && tier <= ScriptRequirement.MAX_TIER,
                                "Уровень инструмента " + tier + " в требовании скрипта " + spec.id() + " вне диапазона "
                                        + ScriptRequirement.MIN_TIER
                                        + ".. Иначе тест defaultCatalog_Requirements_ReferenceExistingParamAndKnownTiers перестанет ловить эту поломку. При смене defaultCatalog_Requirements_ReferenceExistingParamAndKnownTiers проверь это утверждение."
                                        + ScriptRequirement.MAX_TIER));
            }
        }
        assertEquals(
                OreKind.names(),
                List.copyOf(PickaxeTiers.oreTiers().keySet()),
                "Таблица уровней руды обязана покрывать ровно виды OreKind в их порядке: пропущенная руда "
                        + "получала бы уровень по умолчанию, лишняя — никогда не срабатывала Иначе тест defaultCatalog_Requirements_ReferenceExistingParamAndKnownTiers перестанет ловить эту поломку. При смене defaultCatalog_Requirements_ReferenceExistingParamAndKnownTiers проверь это утверждение.");
    }

    @Test
    void defaultCatalog_ChopTree_CountDefaultsToOneSpeciesIsOptional() {
        ScriptSpec chopTree = CoreScripts.defaultCatalog().find("chop_tree").orElseThrow();

        ParamSpec count = param(chopTree, "count");
        ParamSpec species = param(chopTree, "species");
        assertEquals(
                false,
                count.required(),
                "count в chop_tree необязателен: без числа деревьев рубка берёт одно дерево по умолчанию из каталога, "
                        + "а не из шаблона fallback Иначе тест defaultCatalog_ChopTree_CountDefaultsToOneSpeciesIsOptional перестанет ловить эту поломку. При смене defaultCatalog_ChopTree_CountDefaultsToOneSpeciesIsOptional проверь это утверждение.");
        assertEquals(
                1,
                count.defaultValue(),
                "count в chop_tree по умолчанию 1: значение задаёт каталог, чтобы шаблон fallback не дублировал умолчание Иначе тест defaultCatalog_ChopTree_CountDefaultsToOneSpeciesIsOptional перестанет ловить эту поломку. При смене defaultCatalog_ChopTree_CountDefaultsToOneSpeciesIsOptional проверь это утверждение.");
        assertEquals(
                ParamType.INT,
                count.type(),
                "count в chop_tree — целое: дробное число деревьев невозможно Иначе тест defaultCatalog_ChopTree_CountDefaultsToOneSpeciesIsOptional перестанет ловить эту поломку. При смене defaultCatalog_ChopTree_CountDefaultsToOneSpeciesIsOptional проверь это утверждение.");
        assertEquals(
                false,
                species.required(),
                "species в chop_tree необязателен: без него рубят любое дерево (default any) Иначе тест defaultCatalog_ChopTree_CountDefaultsToOneSpeciesIsOptional перестанет ловить эту поломку. При смене defaultCatalog_ChopTree_CountDefaultsToOneSpeciesIsOptional проверь это утверждение.");
    }

    @Test
    void defaultCatalog_ChopTree_SpeciesIsEnumRadiusAndReplantHaveDefaults() {
        ScriptSpec chopTree = CoreScripts.defaultCatalog().find("chop_tree").orElseThrow();

        ParamSpec species = param(chopTree, "species");
        ParamSpec radius = param(chopTree, "radius");
        ParamSpec replant = param(chopTree, "replant");

        assertEquals(
                ParamType.ENUM,
                species.type(),
                "species в chop_tree — enum: список пород объявляется из TreeSpecies один раз, "
                        + "иначе каталог и ядро разойдутся при добавлении новой породы При смене defaultCatalog_ChopTree_SpeciesIsEnumRadiusAndReplantHaveDefaults проверь это утверждение.");
        assertEquals(
                TreeSpecies.names(),
                species.values(),
                "Список допустимых пород enum обязан браться из TreeSpecies, а не копироваться: "
                        + "копия лишней породы молча принимает несуществующую Иначе тест defaultCatalog_ChopTree_SpeciesIsEnumRadiusAndReplantHaveDefaults перестанет ловить эту поломку. При смене defaultCatalog_ChopTree_SpeciesIsEnumRadiusAndReplantHaveDefaults проверь это утверждение.");
        assertEquals(
                "any",
                species.defaultValue(),
                "species в chop_tree по умолчанию any: рубить без указания породы можно, а вот несуществующую "
                        + "породу нельзя Иначе тест defaultCatalog_ChopTree_SpeciesIsEnumRadiusAndReplantHaveDefaults перестанет ловить эту поломку. При смене defaultCatalog_ChopTree_SpeciesIsEnumRadiusAndReplantHaveDefaults проверь это утверждение.");
        assertEquals(
                ParamType.INT,
                radius.type(),
                "radius в chop_tree — целое: дробный радиус бессмыслен Иначе тест defaultCatalog_ChopTree_SpeciesIsEnumRadiusAndReplantHaveDefaults перестанет ловить эту поломку. При смене defaultCatalog_ChopTree_SpeciesIsEnumRadiusAndReplantHaveDefaults проверь это утверждение.");
        assertEquals(
                false,
                radius.required(),
                "radius в chop_tree необязателен: умолчание 16 из каталога достаточно для обычной рубки Иначе тест defaultCatalog_ChopTree_SpeciesIsEnumRadiusAndReplantHaveDefaults перестанет ловить эту поломку. При смене defaultCatalog_ChopTree_SpeciesIsEnumRadiusAndReplantHaveDefaults проверь это утверждение.");
        assertEquals(
                16,
                radius.defaultValue(),
                "radius в chop_tree по умолчанию 16: каталог задаёт умолчание, чтобы шаблон fallback его не дублировал Иначе тест defaultCatalog_ChopTree_SpeciesIsEnumRadiusAndReplantHaveDefaults перестанет ловить эту поломку. При смене defaultCatalog_ChopTree_SpeciesIsEnumRadiusAndReplantHaveDefaults проверь это утверждение.");
        assertEquals(
                (double) CatalogLimits.CHOP_RADIUS_MIN,
                radius.min(),
                "radius в chop_tree не может быть ниже минимума каталога: под духом всегда есть хоть одна клетка поиска Иначе тест defaultCatalog_ChopTree_SpeciesIsEnumRadiusAndReplantHaveDefaults перестанет ловить эту поломку. При смене defaultCatalog_ChopTree_SpeciesIsEnumRadiusAndReplantHaveDefaults проверь это утверждение.");
        assertEquals(
                (double) CatalogLimits.CHOP_RADIUS_MAX,
                radius.max(),
                "radius в chop_tree не может быть выше максимума каталога: границы заданы в одном месте "
                        + "и ядро и каталог читают их вместе Иначе тест defaultCatalog_ChopTree_SpeciesIsEnumRadiusAndReplantHaveDefaults перестанет ловить эту поломку. При смене defaultCatalog_ChopTree_SpeciesIsEnumRadiusAndReplantHaveDefaults проверь это утверждение.");
        assertEquals(
                ParamType.BOOL,
                replant.type(),
                "replant в chop_tree — булев флаг: посадить саженец или нет, третьего не дано Иначе тест defaultCatalog_ChopTree_SpeciesIsEnumRadiusAndReplantHaveDefaults перестанет ловить эту поломку. При смене defaultCatalog_ChopTree_SpeciesIsEnumRadiusAndReplantHaveDefaults проверь это утверждение.");
        assertEquals(
                false,
                replant.defaultValue(),
                "replant в chop_tree по умолчанию false: посадка — явное желание игрока, а не случайность Иначе тест defaultCatalog_ChopTree_SpeciesIsEnumRadiusAndReplantHaveDefaults перестанет ловить эту поломку. При смене defaultCatalog_ChopTree_SpeciesIsEnumRadiusAndReplantHaveDefaults проверь это утверждение.");
    }

    @Test
    void defaultCatalog_PillarUp_DownIsOptionalFalse() {
        ScriptSpec pillarUp = CoreScripts.defaultCatalog().find("pillar_up").orElseThrow();

        ParamSpec down = param(pillarUp, "down");

        assertEquals(
                ParamType.BOOL,
                down.type(),
                "down в pillar_up — булев флаг: разобрать столб или оставить, третьего не дано Иначе тест defaultCatalog_PillarUp_DownIsOptionalFalse перестанет ловить эту поломку. При смене defaultCatalog_PillarUp_DownIsOptionalFalse проверь это утверждение.");
        assertEquals(
                false,
                down.required(),
                "down в pillar_up необязателен: обычный подъём оставляет столб стоять Иначе тест defaultCatalog_PillarUp_DownIsOptionalFalse перестанет ловить эту поломку. При смене defaultCatalog_PillarUp_DownIsOptionalFalse проверь это утверждение.");
        assertEquals(
                false,
                down.defaultValue(),
                "down по умолчанию false: разбор столба — явное намерение, а не побочный эффект подъёма Иначе тест defaultCatalog_PillarUp_DownIsOptionalFalse перестанет ловить эту поломку. При смене defaultCatalog_PillarUp_DownIsOptionalFalse проверь это утверждение.");
    }
}
