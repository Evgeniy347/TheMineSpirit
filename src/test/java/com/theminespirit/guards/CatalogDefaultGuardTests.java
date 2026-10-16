package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.script.CoreScripts;
import com.theminespirit.core.script.ParamSpec;
import com.theminespirit.core.script.ScriptSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CatalogDefaultGuardTests {
    private static final Map<String, String> OPTIONAL_WITHOUT_DEFAULT = Map.of(
            "scan.filter", "отсутствие фильтра значит «все блоки» — это не подставленное значение",
            "scan_blocks.filter", "отсутствие фильтра значит «все блоки» — это не подставленное значение",
            "scan_entities.filter", "отсутствие фильтра значит «все сущности» — это не подставленное значение",
            "collect_drops.filter", "пусто — все предметы, это не подставленное значение",
            "set_camp.x", "отсутствие оси значит «на месте духа» — позиция духа не выражается умолчанием каталога",
            "set_camp.y", "отсутствие оси значит «на месте духа» — позиция духа не выражается умолчанием каталога",
            "set_camp.z", "отсутствие оси значит «на месте духа» — позиция духа не выражается умолчанием каталога");

    @Test
    void defaultCatalog_OptionalParams_DeclareDefaultInCatalog() {
        List<String> offenders = new ArrayList<>(optionalWithoutDefault(CoreScripts.defaultCatalog().all()));
        offenders.removeAll(OPTIONAL_WITHOUT_DEFAULT.keySet());

        assertEquals(List.of(), offenders,
                "Необязательный параметр без умолчания в каталоге: значение по умолчанию тогда подставляет "
                        + "скрипт или правило fallback (orElse(...) в *Params.from, Map.of(\"count\", 1) в FallbackPlanner). "
                        + "Цена: /v1/capabilities и ИИ не видят умолчания, валидатор его не подставляет, а два места "
                        + "решают одно и то же по-разному. Как надо: умолчание — в ParamSpec (перегрузка с defaultValue), "
                        + "*Params.from требует значение (requireString/requireInt). Исключение — только в "
                        + "OPTIONAL_WITHOUT_DEFAULT с причиной; список только сокращается. Нарушители (скрипт.параметр)");
    }

    @Test
    void optionalWithoutDefault_OptionalParamWithoutDefault_IsReported() {
        ScriptSpec sample = new ScriptSpec("sample", "образец",
                List.of(ParamSpec.text("kind", false), ParamSpec.text("name", false, "any"),
                        ParamSpec.text("id", true)),
                List.of(), List.of());

        assertEquals(List.of("sample.kind"), optionalWithoutDefault(List.of(sample)),
                "Сторож ослеп или ложно срабатывает: ловится только необязательный параметр без умолчания, "
                        + "параметр с умолчанием и обязательный параметр — не нарушение");
    }

    @Test
    void optionalWithoutDefault_AllowListedEntries_ExistInCatalog() {
        List<String> stale = new ArrayList<>(OPTIONAL_WITHOUT_DEFAULT.keySet());
        stale.removeAll(optionalWithoutDefault(CoreScripts.defaultCatalog().all()));

        assertEquals(List.of(), stale,
                "Запись исключения больше не нарушает правило (умолчание появилось или параметр удалён): "
                        + "вычеркни её из OPTIONAL_WITHOUT_DEFAULT — список только сокращается");
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
