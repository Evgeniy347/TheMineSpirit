package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.script.CoreScripts;
import com.theminespirit.core.script.api.ParamSpec;
import com.theminespirit.core.script.api.ScriptSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CatalogDefaultGuardTests {
    private static final Map<String, String> OPTIONAL_WITHOUT_DEFAULT = Map.ofEntries(
            Map.entry("scan.filter", "отсутствие фильтра значит «все блоки» — это не подставленное значение"),
            Map.entry("scan_blocks.filter", "отсутствие фильтра значит «все блоки» — это не подставленное значение"),
            Map.entry(
                    "scan_entities.filter", "отсутствие фильтра значит «все сущности» — это не подставленное значение"),
            Map.entry("collect_drops.filter", "пусто — все предметы, это не подставленное значение"),
            Map.entry("eat.item", "отсутствие item значит «лучшее из инвентаря» — это не подставленное значение"),
            Map.entry(
                    "set_camp.x",
                    "отсутствие оси значит «на месте духа» — позиция духа не выражается умолчанием каталога"),
            Map.entry(
                    "set_camp.y",
                    "отсутствие оси значит «на месте духа» — позиция духа не выражается умолчанием каталога"),
            Map.entry(
                    "set_camp.z",
                    "отсутствие оси значит «на месте духа» — позиция духа не выражается умолчанием каталога"),
            Map.entry(
                    "clear_area.boxes",
                    "отсутствие boxes значит один ящик x1..z2 — это другая форма, а не подставленное значение"),
            Map.entry(
                    "clear_area.x1",
                    "координаты нужны только без boxes: умолчания у оси мира нет, форма выбирается вызовом"),
            Map.entry("clear_area.y1", "координаты нужны только без boxes: умолчания у оси мира нет"),
            Map.entry("clear_area.z1", "координаты нужны только без boxes: умолчания у оси мира нет"),
            Map.entry("clear_area.x2", "координаты нужны только без boxes: умолчания у оси мира нет"),
            Map.entry("clear_area.y2", "координаты нужны только без boxes: умолчания у оси мира нет"),
            Map.entry("clear_area.z2", "координаты нужны только без boxes: умолчания у оси мира нет"));

    @Test
    void defaultCatalog_OptionalParams_DeclareDefaultInCatalog() {
        List<String> offenders = new ArrayList<>(
                optionalWithoutDefault(CoreScripts.defaultCatalog().all()));
        offenders.removeAll(OPTIONAL_WITHOUT_DEFAULT.keySet());

        assertEquals(
                List.of(),
                offenders,
                "Необязательный параметр без умолчания в каталоге: значение по умолчанию тогда подставляет "
                        + "скрипт или правило fallback (orElse(...) в *Params.from, Map.of(\"count\", 1) в FallbackPlanner). "
                        + "Цена: /v1/capabilities и ИИ не видят умолчания, валидатор его не подставляет, а два места "
                        + "решают одно и то же по-разному. Как надо: умолчание — в ParamSpec (перегрузка с defaultValue), "
                        + "*Params.from требует значение (requireString/requireInt). Исключение — только в "
                        + "OPTIONAL_WITHOUT_DEFAULT с причиной; список только сокращается. Нарушители (скрипт.параметр)"
                        + " При смене defaultCatalog_OptionalParams_DeclareDefaultInCatalog проверь это утверждение.");
    }

    @Test
    void optionalWithoutDefault_OptionalParamWithoutDefault_IsReported() {
        ScriptSpec sample = new ScriptSpec(
                "sample",
                "образец",
                List.of(
                        ParamSpec.text("kind", false),
                        ParamSpec.text("name", false, "any"),
                        ParamSpec.text("id", true)),
                List.of(),
                List.of());

        assertEquals(
                List.of("sample.kind"),
                optionalWithoutDefault(List.of(sample)),
                "Сторож ослеп или ложно срабатывает: ловится только необязательный параметр без умолчания, "
                        + "параметр с умолчанием и обязательный параметр — не нарушение"
                        + " Иначе тест optionalWithoutDefault_OptionalParamWithoutDefault_IsReported перестанет ловить эту поломку. При смене optionalWithoutDefault_OptionalParamWithoutDefault_IsReported проверь это утверждение.");
    }

    @Test
    void optionalWithoutDefault_AllowListedEntries_ExistInCatalog() {
        List<String> stale = new ArrayList<>(OPTIONAL_WITHOUT_DEFAULT.keySet());
        stale.removeAll(optionalWithoutDefault(CoreScripts.defaultCatalog().all()));

        assertEquals(
                List.of(),
                stale,
                "Запись исключения больше не нарушает правило (умолчание появилось или параметр удалён): "
                        + "вычеркни её из OPTIONAL_WITHOUT_DEFAULT — список только сокращается"
                        + " Иначе тест optionalWithoutDefault_AllowListedEntries_ExistInCatalog перестанет ловить эту поломку. При смене optionalWithoutDefault_AllowListedEntries_ExistInCatalog проверь это утверждение.");
    }

    private static List<String> optionalWithoutDefault(List<ScriptSpec> scripts) {
        List<String> found = new ArrayList<>();
        for (ScriptSpec script : scripts) {
            for (ParamSpec param : script.params()) {
                if (!param.required() && param.defaultValue() == null) {
                    found.add(script.id() + "." + param.name());
                }
            }
        }
        return found;
    }
}
