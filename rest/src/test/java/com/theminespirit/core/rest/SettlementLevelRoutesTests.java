package com.theminespirit.core.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SettlementLevelRoutesTests {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private FakeLevelGateway gateway;
    private Router router;

    @BeforeEach
    void setUp() {
        gateway = new FakeLevelGateway();
        router = Router.of(new SettlementLevelRoutes(gateway, new IdempotencyCache()).routes());
    }

    private RestResponse send(String method, String path, String body) {
        return router.handle(new RestRequest(method, path, Map.of(), Map.of(), body.getBytes(StandardCharsets.UTF_8)));
    }

    private static String body(String requestId, int level) {
        return "{\"request_id\":\"" + requestId + "\",\"level\":" + level + "}";
    }

    @Test
    void get_Level_ReturnsCurrentHeightAndRule() throws Exception {
        gateway.view = Optional.of(new SettlementLevelView(68, 67, 10, -20, "SettlementLevel.v1"));

        RestResponse response = send("GET", "/v1/settlement/level", "");

        assertEquals(
                200,
                response.status(),
                "Чтение уровня обязано отвечать 200: " + response.body()
                        + " Иначе тест get_Level_ReturnsCurrentHeightAndRule перестанет ловить эту поломку. "
                        + "При смене контракта GET /v1/settlement/level проверь клиента стратега.");
        JsonNode parsed = MAPPER.readTree(response.body());
        assertEquals(
                68,
                parsed.get("level").asInt(),
                "GET обязан отдавать уровень ног anchor.y: " + response.body()
                        + " Иначе тест get_Level_ReturnsCurrentHeightAndRule перестанет ловить эту поломку. "
                        + "При смене семантики level/y0 проверь SettlementLevelView.");
        assertEquals(
                67,
                parsed.get("y0").asInt(),
                "GET обязан отдавать верх грунта y0 = level - 1: " + response.body()
                        + " Иначе тест get_Level_ReturnsCurrentHeightAndRule перестанет ловить эту поломку. "
                        + "При смене семантики level/y0 проверь SettlementLevelView.");
    }

    @Test
    void get_WithoutLevel_AnswersNotFound() {
        gateway.view = Optional.empty();

        RestResponse response = send("GET", "/v1/settlement/level", "");

        assertEquals(
                404,
                response.status(),
                "Без выбранного уровня GET обязан отвечать 404, а не выдумывать высоту: " + response.body()
                        + " Иначе тест get_WithoutLevel_AnswersNotFound перестанет ловить эту поломку. "
                        + "При смене статуса непомеченного поселения проверь SettlementLevelRoutes.read.");
    }

    @Test
    void preview_Level_ShowsStockAndWaterLimit() throws Exception {
        gateway.preview = new SettlementLevelPreview(68, 67, 120, 80, 25, 5000, true, 14, 62, 63, 900, 60, 75, 10, -20);

        RestResponse response = send("POST", "/v1/settlement/level/preview", "{}");

        assertEquals(
                200,
                response.status(),
                "Сухой расчёт уровня обязан отвечать 200: " + response.body()
                        + " Иначе тест preview_Level_ShowsStockAndWaterLimit перестанет ловить эту поломку. "
                        + "При смене контракта preview проверь SettlementLevelJson.previewJson.");
        JsonNode parsed = MAPPER.readTree(response.body());
        assertEquals(
                5000,
                parsed.get("stock").get("blocks").asInt(),
                "Preview обязан показывать учтённую землю склада: " + response.body()
                        + " Иначе тест preview_Level_ShowsStockAndWaterLimit перестанет ловить эту поломку. "
                        + "При смене учёта земли проверь SettlementLevelService.earthStock.");
        assertEquals(
                62,
                parsed.get("water").get("median").asInt(),
                "Preview обязан показывать медиану воды: " + response.body()
                        + " Иначе тест preview_Level_ShowsStockAndWaterLimit перестанет ловить эту поломку. "
                        + "При смене поиска воды проверь SettlementGround.waterScan.");
        assertEquals(
                63,
                parsed.get("water").get("floor").asInt(),
                "Preview обязан показывать минимальный уровень ног выше воды: " + response.body()
                        + " Иначе тест preview_Level_ShowsStockAndWaterLimit перестанет ловить эту поломку. "
                        + "При смене WaterLevel.minSettlementLevel проверь это утверждение.");
    }

    @Test
    void put_Level_IsIdempotentAndReturnsPrevious() throws Exception {
        String requestId = UUID.randomUUID().toString();
        gateway.result = new SettlementLevelChangeResult(68, 70, true);

        RestResponse first = send("PUT", "/v1/settlement/level", body(requestId, 70));
        RestResponse replay = send("PUT", "/v1/settlement/level", body(requestId, 70));

        assertEquals(
                200,
                first.status(),
                "Выбор уровня обязан отвечать 200: " + first.body()
                        + " Иначе тест put_Level_IsIdempotentAndReturnsPrevious перестанет ловить эту поломку. "
                        + "При смене контракта PUT /v1/settlement/level проверь клиента стратега.");
        assertEquals(
                first.body(),
                replay.body(),
                "Повтор request_id обязан возвращать прежний ответ: " + replay.body()
                        + " Иначе тест put_Level_IsIdempotentAndReturnsPrevious перестанет ловить эту поломку. "
                        + "При смене идемпотентности уровня проверь SettlementLevelRoutes.change.");
        assertEquals(
                1,
                gateway.sets,
                "Повтор с тем же request_id не должен снова менять мир: " + gateway.sets
                        + " Иначе тест put_Level_IsIdempotentAndReturnsPrevious перестанет ловить эту поломку. "
                        + "При смене идемпотентности уровня проверь SettlementLevelRoutes.change.");
        JsonNode parsed = MAPPER.readTree(first.body());
        assertEquals(
                68,
                parsed.get("previous").asInt(),
                "Ответ обязан называть прежний уровень: " + first.body()
                        + " Иначе тест put_Level_IsIdempotentAndReturnsPrevious перестанет ловить эту поломку. "
                        + "При смене ответа PUT проверь SettlementLevelJson.changeJson.");
    }

    @Test
    void put_SameRequestIdOtherLevel_IsConflict() {
        String requestId = UUID.randomUUID().toString();
        gateway.result = new SettlementLevelChangeResult(68, 70, true);
        send("PUT", "/v1/settlement/level", body(requestId, 70));

        RestResponse response = send("PUT", "/v1/settlement/level", body(requestId, 71));

        assertEquals(
                409,
                response.status(),
                "Другое тело с тем же request_id обязано давать 409: " + response.body()
                        + " Иначе тест put_SameRequestIdOtherLevel_IsConflict перестанет ловить эту поломку. "
                        + "При смене идемпотентности уровня проверь SettlementLevelRoutes.change.");
    }

    @Test
    void put_WithoutRequestId_IsInvalid() {
        RestResponse response = send("PUT", "/v1/settlement/level", "{\"level\":70}");

        assertEquals(
                400,
                response.status(),
                "Без request_id уровень менять нельзя: " + response.body()
                        + " Иначе тест put_WithoutRequestId_IsInvalid перестанет ловить эту поломку. "
                        + "При смене разбора тела PUT проверь SettlementLevelChange.parse.");
        assertTrue(
                response.body().contains("request_id"),
                "Ошибка обязана называть пропущенное поле request_id: " + response.body()
                        + " Иначе тест put_WithoutRequestId_IsInvalid перестанет ловить эту поломку. "
                        + "При смене сообщения разбора проверь SettlementLevelChange.");
    }

    private static final class FakeLevelGateway implements SettlementLevelGateway {
        private Optional<SettlementLevelView> view = Optional.empty();
        private SettlementLevelPreview preview =
                new SettlementLevelPreview(64, 63, 0, 0, 25, 0, true, 0, null, null, 1, 64, 64, 0, 0);
        private SettlementLevelChangeResult result = new SettlementLevelChangeResult(null, 64, true);
        private int sets;

        @Override
        public Optional<SettlementLevelView> view() {
            return view;
        }

        @Override
        public SettlementLevelPreview preview() {
            return preview;
        }

        @Override
        public SettlementLevelChangeResult set(SettlementLevelChange change) {
            sets++;
            return new SettlementLevelChangeResult(result.previous(), change.level(), result.changed());
        }
    }
}
