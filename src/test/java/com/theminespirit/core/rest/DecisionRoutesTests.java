package com.theminespirit.core.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.script.ScriptCatalog;
import com.theminespirit.core.script.ScriptSpec;
import com.theminespirit.core.tactics.HoldStatus;
import com.theminespirit.core.tactics.adapter.DecisionFacts;
import com.theminespirit.core.tactics.adapter.DecisionRecord;
import com.theminespirit.core.tactics.adapter.DecisionSnapshot;
import com.theminespirit.core.tactics.adapter.ReasonFactor;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DecisionRoutesTests {
    private static final String ID = "11111111-1111-1111-1111-111111111111";
    private static final String OTHER = "22222222-2222-2222-2222-222222222222";
    private static final List<BotInfo> BOTS = List.of(new BotInfo(ID, "spirit", BotState.IDLE, 10, 64, -3,
            "minecraft:overworld", null, 1.0), new BotInfo(OTHER, "Дух-2", BotState.BUSY, 0, 70, 5,
            "minecraft:overworld", null, 1.0));
    private static final ScriptCatalog CATALOG = ScriptCatalog.of(List.of(
            new ScriptSpec("scan", "просканировать", List.of(), List.of(), List.of())));
    private static final DecisionRecord IGNORE_TO_FIGHT = new DecisionRecord(1000, ID, "spirit",
            DecisionSnapshot.DECISION, "IGNORE", "FIGHT", "score", HoldStatus.NONE,
            List.of(new ReasonFactor("WIN_MARGIN", 0.62, 1.06), new ReasonFactor("PROXIMITY", 0.58, 0.23)),
            new DecisionFacts(1, "minecraft:husk в 5.0", 20, 20, 0, "оружие ближнего боя"));
    private static final DecisionRecord FIGHT_TO_RETREAT = new DecisionRecord(1100, ID, "spirit",
            DecisionSnapshot.DECISION, "FIGHT", "RETREAT", "critical_hp_retreat", HoldStatus.HELD, List.of(),
            new DecisionFacts(6, "minecraft:zombie в 4.0", 3.5, 20, 12, "руки"));

    private static RestResponse send(FakeBotGateway gateway, String method, String path) {
        int query = path.indexOf('?');
        String route = query < 0 ? path : path.substring(0, query);
        Router router = Router.of(V1Routes.routes(gateway, CATALOG));
        return router.handle(new RestRequest(method, route, QueryString.parse(query < 0 ? "" :
                path.substring(query + 1)), Map.of(), new byte[0]));
    }

    private static RestResponse send(FakeBotGateway gateway, String path) {
        return send(gateway, "GET", path);
    }

    private static FakeBotGateway gateway() {
        return new FakeBotGateway(BOTS).deciding(IGNORE_TO_FIGHT, FIGHT_TO_RETREAT);
    }

    @Test
    void decisions_HuskFightRecord_ServedWithReasonAndFacts() {
        RestResponse response = send(gateway(), "/v1/spirits/" + ID + "/decisions?limit=5");

        assertEquals(200, response.status(), "Журнал решений духа отдаётся с 200: " + response.body());
        assertTrue(response.body().startsWith("{\"bot\":\"" + ID + "\",\"limit\":5,\"decisions\":["),
                "Ответ называет духа, предел и записи в этом порядке: " + response.body());
        assertTrue(response.body().contains("\"tick\":1000"), "Запись называет тик решения: " + response.body());
        assertTrue(response.body().contains("\"changed\":\"decision\""), "Запись называет сменившееся поле: "
                + response.body());
        assertTrue(response.body().contains("\"from\":\"IGNORE\",\"to\":\"FIGHT\""),
                "Запись показывает «было → стало»: " + response.body());
        assertTrue(response.body().contains("\"rule\":\"score\",\"hold\":\"none\""),
                "Запись называет причину и удержание: " + response.body());
        assertTrue(response.body().contains("\"factors\":[{\"factor\":\"WIN_MARGIN\",\"value\":0.62,"
                        + "\"weight\":1.06},{\"factor\":\"PROXIMITY\",\"value\":0.58,\"weight\":0.23}]"),
                "Решение по оценке называет факторы с весами: " + response.body());
        assertTrue(response.body().contains("\"facts\":{\"enemies\":1,\"nearest\":\"minecraft:husk в 5.0\","
                        + "\"hp\":20.0,\"max_hp\":20.0,\"armor\":0.0,\"weapon\":\"оружие ближнего боя\"}"),
                "Ключевые факты идут в записи: враги, здоровье, броня, оружие: " + response.body());
    }

    @Test
    void decisions_RuleDecisionWithoutFactors_ServedAsRule() {
        RestResponse response = send(gateway(), "/v1/spirits/" + ID + "/decisions?limit=1");

        assertEquals(200, response.status(), "Одна запись по пределу 1: " + response.body());
        assertTrue(response.body().contains("\"rule\":\"score\""), "Новая запись идёт первой: " + response.body());
        assertTrue(response.body().endsWith("}]}"), "Предел 1 оставляет одну запись: " + response.body());
    }

    @Test
    void decisions_TwoRecords_MatchesReferenceJson() {
        RestResponse response = send(gateway(), "/v1/spirits/" + ID + "/decisions?limit=5");

        assertEquals("{\"bot\":\"" + ID + "\",\"limit\":5,\"decisions\":["
                        + "{\"tick\":1000,\"spirit\":\"" + ID + "\",\"changed\":\"decision\",\"from\":\"IGNORE\","
                        + "\"to\":\"FIGHT\",\"rule\":\"score\",\"hold\":\"none\","
                        + "\"factors\":[{\"factor\":\"WIN_MARGIN\",\"value\":0.62,\"weight\":1.06},"
                        + "{\"factor\":\"PROXIMITY\",\"value\":0.58,\"weight\":0.23}],"
                        + "\"facts\":{\"enemies\":1,\"nearest\":\"minecraft:husk в 5.0\",\"hp\":20.0,\"max_hp\":20.0,"
                        + "\"armor\":0.0,\"weapon\":\"оружие ближнего боя\"}},"
                        + "{\"tick\":1100,\"spirit\":\"" + ID + "\",\"changed\":\"decision\",\"from\":\"FIGHT\","
                        + "\"to\":\"RETREAT\",\"rule\":\"critical_hp_retreat\",\"hold\":\"held\",\"factors\":[],"
                        + "\"facts\":{\"enemies\":6,\"nearest\":\"minecraft:zombie в 4.0\",\"hp\":3.5,\"max_hp\":20.0,"
                        + "\"armor\":12.0,\"weapon\":\"руки\"}}]}",
                response.body(),
                "Журнал решений — контракт REST: bot, limit и записи с тиком, духом, полем, «было → стало», "
                        + "причиной (правило или факторы с весами) и ключевыми фактами. ИИ-агент читает его как "
                        + "объяснение, почему дух так поступил (docs/api/README.md → GET /v1/spirits/{id}/decisions)");
    }

    @Test
    void decisions_NoLimitParameter_TakesFiveFromContract() {
        RestResponse response = send(gateway(), "/v1/spirits/" + ID + "/decisions");

        assertEquals(200, response.status(), "Без limit журнал отдаётся: " + response.body());
        assertTrue(response.body().contains("\"limit\":5"),
                "Умолчание контракта — 5 записей, как в команде /spirit decisions: " + response.body());
    }

    @Test
    void decisions_UnknownSpiritOrBadLimit_ReturnsDocumentedError() {
        RestResponse unknown = send(gateway(), "/v1/spirits/unknown/decisions?limit=5");
        RestResponse zero = send(gateway(), "/v1/spirits/" + ID + "/decisions?limit=0");
        RestResponse word = send(gateway(), "/v1/spirits/" + ID + "/decisions?limit=many");
        RestResponse big = send(gateway(), "/v1/spirits/" + ID + "/decisions?limit=65");
        RestResponse extra = send(gateway(), "/v1/spirits/" + ID + "/decisions?limit=5&offset=1");
        RestResponse wrongMethod = send(gateway(), "POST", "/v1/spirits/" + ID + "/decisions?limit=5");

        assertEquals(404, unknown.status(), "Неизвестный дух — 404 not_found: " + unknown.body());
        assertTrue(unknown.body().contains("\"code\":\"not_found\"") && unknown.body().contains("unknown"),
                "404 называет код и искомый id: " + unknown.body());
        assertEquals(400, zero.status(), "Предел 0 — 400: записей ноль не бывает: " + zero.body());
        assertTrue(zero.body().contains("'limit'"), "400 называет параметр: " + zero.body());
        assertEquals(400, word.status(), "Нечисловой limit — 400: " + word.body());
        assertEquals(400, big.status(), "limit 65 вне контракта 1..64 — 400: " + big.body());
        assertEquals(400, extra.status(), "Лишний параметр запроса — 400, а не молчаливый пропуск: " + extra.body());
        assertEquals(405, wrongMethod.status(), "POST на журнал решений — 405 с Allow: " + wrongMethod.body());
        assertTrue(wrongMethod.body().contains("допустимо GET"), "405 называет допустимый метод: "
                + wrongMethod.body());
    }

    @Test
    void decisions_GatewayFailure_PassesErrorObjectThrough() {
        ApiException failure = new ApiException(ErrorCode.INTERNAL, "Сервер не ответил за 5 с",
                Map.of("timeout_ms", 5000));

        RestResponse response = send(gateway().failingWith(failure), "/v1/spirits/" + ID + "/decisions?limit=5");

        assertEquals(500, response.status(), "Сбой шлюза — 500: " + response.body());
        assertTrue(response.body().contains("\"code\":\"internal\""),
                "Ошибка шлюза уходит клиенту тем же объектом: " + response.body());
    }

    @Test
    void decisions_BodyOfGet_IsNotRead() {
        Router router = Router.of(V1Routes.routes(gateway(), CATALOG));
        RestResponse response = router.handle(new RestRequest("GET", "/v1/spirits/" + ID + "/decisions", Map.of(),
                Map.of(), "{\"limit\":1}".getBytes(StandardCharsets.UTF_8)));

        assertEquals(200, response.status(), "Тело GET не разбирается: журнал читает только query: "
                + response.body());
        assertTrue(response.body().contains("\"limit\":5"),
                "Тело не подменяет умолчание limit: " + response.body());
    }
}
