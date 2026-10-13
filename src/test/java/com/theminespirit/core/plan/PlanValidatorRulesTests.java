package com.theminespirit.core.plan;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.farming.CropKind;
import com.theminespirit.core.mining.OreKind;
import com.theminespirit.core.script.ConditionCatalog;
import com.theminespirit.core.script.ConditionSpec;
import com.theminespirit.core.script.CoreScripts;
import com.theminespirit.core.script.ParamSpec;
import com.theminespirit.core.script.ParamType;
import com.fasterxml.jackson.databind.JsonNode;
import com.theminespirit.core.script.ScriptCatalog;
import com.theminespirit.core.script.ScriptSpec;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PlanValidatorRulesTests {
    private static final ScriptCatalog CATALOG = CoreScripts.defaultCatalog();
    private static final ScriptCatalog DEFAULTS_CATALOG = ScriptCatalog.of(List.of(new ScriptSpec("harvest_test",
            "скрипт с булевым и перечислимым параметром", List.of(
                    ParamSpec.bool("replant", false, true),
                    ParamSpec.enumeration("species", false, List.of("any", "oak", "birch"), "any")),
            List.of(), List.of())));

    static Stream<Arguments> validationRules() {
        return Stream.of(
                Arguments.of("тип INT: строка вместо числа",
                        "{\"type\":\"script\",\"script\":\"wait\",\"params\":{\"ticks\":\"5\"}}"),
                Arguments.of("диапазон снизу: wait.ticks < 1",
                        "{\"type\":\"script\",\"script\":\"wait\",\"params\":{\"ticks\":0}}"),
                Arguments.of("диапазон сверху: wait.ticks > 6000",
                        "{\"type\":\"script\",\"script\":\"wait\",\"params\":{\"ticks\":6001}}"),
                Arguments.of("диапазон count снизу: < 1",
                        "{\"type\":\"script\",\"script\":\"chop_tree\",\"params\":{\"species\":\"any\",\"count\":0}}"),
                Arguments.of("лишний параметр",
                        "{\"type\":\"script\",\"script\":\"return_home\",\"params\":{\"x\":1}}"),
                Arguments.of("отсутствующий обязательный",
                        "{\"type\":\"script\",\"script\":\"wait\",\"params\":{}}"),
                Arguments.of("repeat.times < 1",
                        "{\"type\":\"repeat\",\"times\":0,\"step\":{\"type\":\"script\",\"script\":\"return_home\"}}"),
                Arguments.of("repeat.times > 64",
                        "{\"type\":\"repeat\",\"times\":65,\"step\":{\"type\":\"script\",\"script\":\"return_home\"}}"),
                Arguments.of("неизвестная проверка условия",
                        "{\"type\":\"condition\",\"check\":\"unknown_check\",\"then\":{\"type\":\"script\",\"script\":\"return_home\"}}"),
                Arguments.of("count верхняя граница: > 256",
                        "{\"type\":\"script\",\"script\":\"chop_tree\",\"params\":{\"species\":\"any\",\"count\":257}}"),
                Arguments.of("y ниже минимума: < -64",
                        "{\"type\":\"script\",\"script\":\"dig_area\",\"params\":{\"x\":0,\"y\":-65,\"z\":0}}"),
                Arguments.of("y выше максимума: > 319",
                        "{\"type\":\"script\",\"script\":\"dig_area\",\"params\":{\"x\":0,\"y\":320,\"z\":0}}"),
                Arguments.of("x ниже минимума: < -30000000",
                        "{\"type\":\"script\",\"script\":\"dig_area\",\"params\":{\"x\":-30000001,\"y\":0,\"z\":0}}"),
                Arguments.of("x выше максимума: > 30000000",
                        "{\"type\":\"script\",\"script\":\"dig_area\",\"params\":{\"x\":30000001,\"y\":0,\"z\":0}}"),
                Arguments.of("z ниже минимума: < -30000000",
                        "{\"type\":\"script\",\"script\":\"dig_area\",\"params\":{\"x\":0,\"y\":0,\"z\":-30000001}}"),
                Arguments.of("z выше максимума: > 30000000",
                        "{\"type\":\"script\",\"script\":\"dig_area\",\"params\":{\"x\":0,\"y\":0,\"z\":30000001}}"),
                Arguments.of("dig_area size ниже минимума: 0",
                        "{\"type\":\"script\",\"script\":\"dig_area\",\"params\":{\"x\":0,\"y\":64,\"z\":0,\"size\":0}}"),
                Arguments.of("dig_area size выше максимума: 17",
                        "{\"type\":\"script\",\"script\":\"dig_area\",\"params\":{\"x\":0,\"y\":64,\"z\":0,\"size\":17}}"),
                Arguments.of("follow radius выше максимума: > 16",
                        "{\"type\":\"script\",\"script\":\"follow\",\"params\":{\"radius\":17}}"),
                Arguments.of("species вне enum: pine",
                        "{\"type\":\"script\",\"script\":\"chop_tree\",\"params\":{\"species\":\"pine\"}}")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("validationRules")
    void planValidation_InvalidInput_IsRejected(String description, String json) {
        assertThrows(PlanValidationException.class,
                () -> PlanParser.parse(json, CATALOG),
                description + ": план должен быть отклонён валидатором");
    }

    @Test
    void mineVeinUnknownOre_Plan_IsRejectedWithAllowedList() {
        PlanValidationException error = assertThrows(PlanValidationException.class,
                () -> PlanParser.parse(
                        "{\"type\":\"script\",\"script\":\"mine_vein\",\"params\":{\"ore\":\"mithril\"}}", CATALOG),
                "ore=\"mithril\" обязан отвергаться валидатором: enum из каталога обязан перечислять допустимые виды");

        assertTrue(error.getMessage().contains("mithril"),
                "Отказ обязан называть неизвестный вид 'mithril'. Фактическое: " + error.getMessage());
        for (String allowed : OreKind.names()) {
            assertTrue(error.getMessage().contains(allowed),
                    "Отказ обязан перечислять допустимый вид '" + allowed + "': по списку правят план. Фактическое: "
                            + error.getMessage());
        }
    }

    @Test
    void harvestCropUnknownCrop_Plan_IsRejectedWithAllowedList() {
        PlanValidationException error = assertThrows(PlanValidationException.class,
                () -> PlanParser.parse(
                        "{\"type\":\"script\",\"script\":\"harvest_crop\",\"params\":{\"crop\":\"melon\"}}", CATALOG),
                "crop=\"melon\" обязан отвергаться валидатором: enum из каталога обязан перечислять допустимые виды");

        assertTrue(error.getMessage().contains("melon"),
                "Отказ обязан называть неизвестный вид 'melon'. Фактическое: " + error.getMessage());
        for (String allowed : CropKind.names()) {
            assertTrue(error.getMessage().contains(allowed),
                    "Отказ обязан перечислять допустимый вид '" + allowed + "': по списку правят план. Фактическое: "
                            + error.getMessage());
        }
    }

    @Test
    void plantCropWithoutCrop_Plan_IsRejected() {
        assertThrows(PlanValidationException.class,
                () -> PlanParser.parse(
                        "{\"type\":\"script\",\"script\":\"plant_crop\",\"params\":{\"radius\":8}}", CATALOG),
                "plant_crop без crop обязан отвергаться: вид обязателен, без него неизвестно, какие семена тратить");
    }

    @Test
    void plantCropAnyCrop_Plan_IsRejected() {
        assertThrows(PlanValidationException.class,
                () -> PlanParser.parse(
                        "{\"type\":\"script\",\"script\":\"plant_crop\",\"params\":{\"crop\":\"any\"}}", CATALOG),
                "plant_crop с crop=any обязан отвергаться: каталог разрешает только сажаемые виды без any");
    }

    @Test
    void harvestCropWithoutParams_Plan_ContainsCatalogDefaults() {
        TaskNode plan = PlanParser.parse("{\"type\":\"script\",\"script\":\"harvest_crop\"}", CATALOG);

        assertEquals(new ScriptCall("harvest_crop", Map.of("crop", "any", "radius", 8, "replant", true)), plan,
                "Валидатор обязан подставлять умолчания каталога для отсутствующих необязательных параметров: "
                        + "иначе обещание default в /v1/capabilities остаётся неправдой, а маппинг получит план без вида");
    }

    @Test
    void mineVeinWithoutOre_Plan_ContainsCatalogDefault() {
        TaskNode plan = PlanParser.parse(
                "{\"type\":\"script\",\"script\":\"mine_vein\",\"params\":{\"count\":2,\"radius\":8}}", CATALOG);

        assertEquals(new ScriptCall("mine_vein",
                        Map.of("ore", "any", "count", 2, "radius", 8)), plan,
                "Валидатор обязан подставлять default 'any' для отсутствующего ore: иначе обещание default "
                        + "в /v1/capabilities остаётся неправдой, а маппинг скрипта получит план без вида руды");
    }

    @Test
    void followWithoutRadius_Plan_ContainsCatalogDefault() {
        TaskNode plan = PlanParser.parse(
                "{\"type\":\"script\",\"script\":\"follow\",\"params\":{\"target\":\"player\"}}", CATALOG);

        assertEquals(new ScriptCall("follow", Map.of("target", "player", "radius", 3)), plan,
                "Валидатор обязан подставлять default из каталога для отсутствующего необязательного параметра: "
                        + "иначе обещание default в /v1/capabilities остаётся неправдой, а скрипт получает неполный план");
    }

    @Test
    void waitScript_TicksInsideRange_IsAccepted() {
        String json = "{\"type\":\"script\",\"script\":\"wait\",\"params\":{\"ticks\":3000}}";
        PlanParser.parse(json, CATALOG);
    }

    @Test
    void chopTree_UnknownSpecies_IsRejectedListingAllowed() {
        PlanValidationException error = assertThrows(PlanValidationException.class,
                () -> PlanParser.parse(
                        "{\"type\":\"script\",\"script\":\"chop_tree\",\"params\":{\"species\":\"pine\",\"count\":1,"
                                + "\"radius\":16,\"replant\":false}}",
                        CATALOG),
                "План с несуществующей породой обязан отклоняться: опечатка species иначе заставит духа "
                        + "рубить любое дерево вместо берёзы");

        assertTrue(error.getMessage().contains("oak") && error.getMessage().contains("any"),
                "Сообщение обязано перечислить допустимые породы с 'any': по нему правят species. "
                        + "Фактическое: " + error.getMessage());
    }

    @Property
    void waitScript_TicksOutsideRange_IsRejected(@ForAll int ticks) {
        if (ticks >= 1 && ticks <= 6000) {
            String json = "{\"type\":\"script\",\"script\":\"wait\",\"params\":{\"ticks\":" + ticks + "}}";
            PlanParser.parse(json, CATALOG);
            return;
        }
        String json = "{\"type\":\"script\",\"script\":\"wait\",\"params\":{\"ticks\":" + ticks + "}}";
        assertThrows(PlanValidationException.class, () -> PlanParser.parse(json, CATALOG),
                "параметр wait.ticks за пределами допустимого диапазона: валидатор плана обязан отклонять значение");
    }

    @Test
    void patrolUnknownMode_Plan_IsRejectedWithAllowedList() {
        PlanValidationException error = assertThrows(PlanValidationException.class,
                () -> PlanParser.parse("{\"type\":\"script\",\"script\":\"patrol\","
                        + "\"params\":{\"points\":[\"0,64,0\"],\"mode\":\"circle\",\"loops\":1}}", CATALOG),
                "mode=\"circle\" обязан отвергаться валидатором: enum из каталога обязан перечислять допустимые режимы");

        assertTrue(error.getMessage().contains("circle"),
                "Отказ обязан называть неизвестный режим 'circle'. Фактическое: " + error.getMessage());
        assertTrue(error.getMessage().contains("loop") && error.getMessage().contains("ping_pong"),
                "Отказ обязан перечислять допустимые режимы 'loop' и 'ping_pong': по списку правят план. "
                        + "Фактическое: " + error.getMessage());
    }

    @Test
    void patrolWithoutModeAndLoops_Plan_ContainsCatalogDefaults() {
        TaskNode plan = PlanParser.parse(
                "{\"type\":\"script\",\"script\":\"patrol\",\"params\":{\"points\":[\"0,64,0\"]}}", CATALOG);

        assertEquals(new ScriptCall("patrol", Map.of("points", List.of("0,64,0"), "mode", "loop", "loops", 1)),
                plan,
                "Валидатор обязан подставлять умолчания каталога для отсутствующих mode и loops: "
                        + "иначе обещание default в /v1/capabilities остаётся неправдой, а маппинг получит план без режима");
    }

    @Test
    void comeToPlayerWithoutPlayer_Plan_ContainsNearestDefault() {
        TaskNode plan = PlanParser.parse("{\"type\":\"script\",\"script\":\"come_to_player\"}", CATALOG);

        assertEquals(new ScriptCall("come_to_player", Map.of("player", "nearest")), plan,
                "Валидатор обязан подставлять default 'nearest' для отсутствующего player: без имени "
                        + "дух идёт к ближайшему игроку, а не стоит");
    }

    @Test
    void booleanAndEnumDefaults_ParamsOmitted_AreSubstitutedIntoPlan() {
        TaskNode plan = PlanParser.parse("{\"type\":\"script\",\"script\":\"harvest_test\"}", DEFAULTS_CATALOG);

        assertEquals(new ScriptCall("harvest_test", Map.of("replant", true, "species", "any")), plan,
                "Валидатор обязан подставить умолчания bool и enum из каталога: иначе скрипты рубки и добычи получат "
                        + "неполную карту параметров, а ИИ не узнает, что делает план без явного значения");
    }

    @Test
    void enumParam_ValueOutsideList_IsRejectedWithAllowedValuesNamed() {
        PlanValidationException error = assertThrows(PlanValidationException.class,
                () -> PlanParser.parse("{\"type\":\"script\",\"script\":\"harvest_test\","
                        + "\"params\":{\"species\":\"spruce\"}}", DEFAULTS_CATALOG),
                "Значение enum вне списка обязано отвергаться валидатором: скрипт не должен гадать, что такое spruce");

        assertTrue(error.getMessage().contains("oak") && error.getMessage().contains("spruce"),
                "Сообщение обязано называть недопустимое значение и допустимые. Фактическое: " + error.getMessage());
    }

    @Test
    void booleanParam_StringValue_IsRejected() {
        assertThrows(PlanValidationException.class,
                () -> PlanParser.parse("{\"type\":\"script\",\"script\":\"harvest_test\","
                        + "\"params\":{\"replant\":\"yes\"}}", DEFAULTS_CATALOG),
                "Строка вместо boolean обязана отвергаться валидатором: скрипт читает значение через requireBoolean");
    }

    @Test
    void booleanParam_ExplicitFalse_OverridesDefault() {
        TaskNode plan = PlanParser.parse("{\"type\":\"script\",\"script\":\"harvest_test\","
                + "\"params\":{\"replant\":false,\"species\":\"oak\"}}", DEFAULTS_CATALOG);

        assertEquals(new ScriptCall("harvest_test", Map.of("replant", false, "species", "oak")), plan,
                "Явное значение плана обязано побеждать умолчание каталога: иначе ИИ не сможет отключить replant");
    }

    @Provide
    Arbitrary<JsonNode> jsonTrees() {
        return JsonTrees.any();
    }

    @Property
    void parse_AnyJsonTree_SameInputSameOutputOrException(@ForAll("jsonTrees") JsonNode tree) {
        String json = tree.toString();
        try {
            TaskNode first = PlanParser.parse(json, CATALOG);
            TaskNode second = PlanParser.parse(json, CATALOG);
            assertEquals(first, second,
                    "одинаковый вход обязан давать одинаковый план: если валидатор зависит от порядка или "
                            + "состояния, один и тот же план в разное время выполнит разные действия. JSON=" + json);
        } catch (PlanValidationException firstError) {
            PlanValidationException secondError = assertThrows(PlanValidationException.class,
                    () -> PlanParser.parse(json, CATALOG),
                    "одинаковый вход обязан бросать одинаковое исключение: нестабильный валидатор то примет, "
                            + "то отклонит один план. JSON=" + json);
            assertEquals(firstError.getMessage(), secondError.getMessage(),
                    "исключение должно быть тем же самым при том же входе: детерминизм валидатора — контракт, "
                            + "иначе ИИ не сможет воспроизвести и починить сбой. JSON=" + json);
        }
    }
}
