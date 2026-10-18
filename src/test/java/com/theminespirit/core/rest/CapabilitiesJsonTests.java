package com.theminespirit.core.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theminespirit.core.script.CoreScripts;
import com.theminespirit.core.script.ParamSpec;
import com.theminespirit.core.script.ScriptCatalog;
import com.theminespirit.core.script.ScriptSpec;
import java.util.List;
import org.junit.jupiter.api.Test;

class CapabilitiesJsonTests {
    private static final ObjectMapper MAPPER = new ObjectMapper();

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
                + "\"preconditions\":[\"есть цель\"],\"requires\":[],\"effects\":[\"цель достигнута\"]},"
                + "{\"id\":\"idle\",\"summary\":\"ничего не делать\",\"params\":{\"type\":\"object\","
                + "\"properties\":{},\"required\":[]},\"preconditions\":[],\"requires\":[],\"effects\":[]}]}";

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
    void toJson_MineVeinAndGather_ExposePickaxeTiersFromCatalog() throws Exception {
        JsonNode scripts = MAPPER.readTree(CapabilitiesJson.toJson(CoreScripts.defaultCatalog())).get("scripts");

        JsonNode ore = requires(scripts, "mine_vein");
        assertEquals("tool", ore.get("kind").textValue(), "требование кирки — инструментальное");
        assertEquals("pickaxe", ore.get("tool").textValue(), "требование кирки называет инструмент pickaxe");
        assertEquals("ore", ore.get("param").textValue(), "уровень кирки для mine_vein зависит от параметра ore");
        assertEquals(1, ore.get("tiers").get("any").intValue(), "любая руда требует хотя бы деревянную кирку");
        assertEquals(2, ore.get("tiers").get("iron").intValue(), "железная руда требует каменную кирку");
        assertEquals(3, ore.get("tiers").get("diamond").intValue(), "алмазная руда требует железную кирку");
        assertEquals(4, ore.get("tiers").get("netherite").intValue(), "древние обломки требуют алмазную кирку");

        JsonNode block = requires(scripts, "gather");
        assertEquals("block", block.get("param").textValue(), "уровень кирки для gather зависит от параметра block");
        assertEquals(1, block.get("tiers").get("stone").intValue(), "камень требует деревянную кирку");
        assertEquals(2, block.get("tiers").get("iron_ore").intValue(), "железная руда требует каменную кирку");
        assertEquals(4, block.get("tiers").get("obsidian").intValue(), "обсидиан требует алмазную кирку");
        assertFalse(block.get("tiers").has("oak_log"), "бревно добывается топором, кирка для него не требуется");
        assertEquals(0, block.get("fallback").intValue(), "блок вне таблицы кирки не требует");
    }

    private static JsonNode requires(JsonNode scripts, String id) {
        for (JsonNode script : scripts) {
            if (id.equals(script.get("id").textValue())) {
                return script.get("requires").get(0);
            }
        }
        throw new AssertionError("В каталоге нет скрипта " + id);
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
