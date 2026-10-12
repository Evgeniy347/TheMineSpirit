package com.theminespirit.core.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.script.CoreScripts;
import com.theminespirit.core.script.ParamSpec;
import com.theminespirit.core.script.ScriptCatalog;
import com.theminespirit.core.script.ScriptSpec;
import java.util.List;
import org.junit.jupiter.api.Test;

class CapabilitiesJsonTests {
    private static ScriptCatalog smallCatalog() {
        return ScriptCatalog.of(List.of(
                new ScriptSpec("demo", "демо",
                        List.of(
                                ParamSpec.integer("count", false, 1, 256, 1),
                                ParamSpec.number("ratio", true, 0.5, 2.5),
                                ParamSpec.text("species", false, "any"),
                                ParamSpec.textList("filter", false),
                                ParamSpec.bool("silent", false),
                                ParamSpec.enumeration("mode", true, List.of("fast", "slow"))),
                        List.of("есть цель"), List.of("цель достигнута")),
                new ScriptSpec("idle", "ничего не делать", List.of(), List.of(), List.of())));
    }

    @Test
    void toJson_SmallCatalogWithAllParamTypes_EqualsReferenceJson() {
        String expected = "{\"scripts\":["
                + "{\"id\":\"demo\",\"summary\":\"демо\",\"params\":{\"type\":\"object\",\"properties\":{"
                + "\"count\":{\"type\":\"integer\",\"minimum\":1,\"maximum\":256,\"default\":1},"
                + "\"ratio\":{\"type\":\"number\",\"minimum\":0.5,\"maximum\":2.5},"
                + "\"species\":{\"type\":\"string\",\"default\":\"any\"},"
                + "\"filter\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}},"
                + "\"silent\":{\"type\":\"boolean\"},"
                + "\"mode\":{\"type\":\"string\",\"enum\":[\"fast\",\"slow\"]}},"
                + "\"required\":[\"ratio\",\"mode\"]},"
                + "\"preconditions\":[\"есть цель\"],\"effects\":[\"цель достигнута\"]},"
                + "{\"id\":\"idle\",\"summary\":\"ничего не делать\",\"params\":{\"type\":\"object\","
                + "\"properties\":{},\"required\":[]},\"preconditions\":[],\"effects\":[]}]}";

        String actual = CapabilitiesJson.toJson(smallCatalog());

        assertEquals(expected, actual,
                "Каталог в JSON — контракт docs/API.md для ИИ-планировщика: типы параметров в JSON-Schema "
                        + "(integer/number/string/array/boolean/enum), границы, умолчания, required и порядок скриптов "
                        + "как в каталоге. Меняя формат осознанно, правь docs/API.md");
    }

    @Test
    void toJson_EmptyCatalog_WritesEmptyScriptList() {
        assertEquals("{\"scripts\":[]}", CapabilitiesJson.toJson(ScriptCatalog.of(List.of())),
                "Пустой каталог — пустой список скриптов, а не null или ошибка: клиент отличает его от сбоя");
    }

    @Test
    void toJson_DefaultCatalog_SerializesEveryScript() {
        ScriptCatalog catalog = CoreScripts.defaultCatalog();

        String json = CapabilitiesJson.toJson(catalog);

        for (ScriptSpec spec : catalog.all()) {
            assertTrue(json.contains("\"id\":\"" + spec.id() + "\""),
                    "Скрипт '" + spec.id() + "' из CoreScripts.defaultCatalog() обязан быть в JSON каталога");
        }
    }
}
