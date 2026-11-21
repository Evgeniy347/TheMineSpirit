package com.theminespirit.core.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.route.QuarryCandidate;
import com.theminespirit.core.board.route.QuarrySite;
import com.theminespirit.core.board.route.QuarryStatus;
import com.theminespirit.core.board.route.RouteResource;
import com.theminespirit.core.observation.BlockCoord;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class QuarryRoutesTests {
    @Test
    void search_Defaults_UseSettlementRange() {
        RecordingGateway gateway = new RecordingGateway();
        Router router = Router.of(new QuarryRoutes(gateway).routes());

        RestResponse response = router.handle(
                new RestRequest("GET", QuarryRoutes.SEARCH, Map.of("resource", "dirt"), Map.of(), new byte[0]));

        assertEquals(
                200,
                response.status(),
                "GET /v1/quarry/search обязан отвечать списком лучших областей"
                        + " Иначе тест search_Defaults_UseSettlementRange перестанет ловить эту поломку. При смене search_Defaults_UseSettlementRange проверь это утверждение.");
        assertEquals(
                128,
                gateway.query.min(),
                "По умолчанию поиск начинается со 128 блоков: ближе добыча не нужна"
                        + " Иначе тест search_Defaults_UseSettlementRange перестанет ловить эту поломку. При смене search_Defaults_UseSettlementRange проверь это утверждение.");
        assertEquals(
                512,
                gateway.query.max(),
                "По умолчанию поиск кончается на 512 блоках: дальше тропа не окупается"
                        + " Иначе тест search_Defaults_UseSettlementRange перестанет ловить эту поломку. При смене search_Defaults_UseSettlementRange проверь это утверждение.");
        assertTrue(
                response.body().contains("\"candidates\""),
                "Ответ перечисляет кандидатов: " + response.body()
                        + " Иначе тест search_Defaults_UseSettlementRange перестанет ловить эту поломку. При смене search_Defaults_UseSettlementRange проверь это утверждение.");
    }

    @Test
    void search_PointAndRange_Parsed() {
        RecordingGateway gateway = new RecordingGateway();
        Router router = Router.of(new QuarryRoutes(gateway).routes());

        router.handle(new RestRequest(
                "GET",
                QuarryRoutes.SEARCH,
                Map.of("resource", "sand", "x", "10", "y", "64", "z", "-20", "min", "64", "max", "256"),
                Map.of(),
                new byte[0]));

        assertEquals(
                RouteResource.SAND,
                gateway.query.resource(),
                "Ресурс поиска берётся из запроса"
                        + " Иначе тест search_PointAndRange_Parsed перестанет ловить эту поломку. При смене search_PointAndRange_Parsed проверь это утверждение.");
        assertEquals(
                new BlockCoord(10, 64, -20),
                gateway.query.base().orElseThrow(),
                "Точка отсчёта берётся из запроса"
                        + " Иначе тест search_PointAndRange_Parsed перестанет ловить эту поломку. При смене search_PointAndRange_Parsed проверь это утверждение.");
        assertEquals(
                64,
                gateway.query.min(),
                "Минимум берётся из запроса"
                        + " Иначе тест search_PointAndRange_Parsed перестанет ловить эту поломку. При смене search_PointAndRange_Parsed проверь это утверждение.");
        assertEquals(
                256,
                gateway.query.max(),
                "Максимум берётся из запроса"
                        + " Иначе тест search_PointAndRange_Parsed перестанет ловить эту поломку. При смене search_PointAndRange_Parsed проверь это утверждение.");
    }

    @Test
    void search_BadRange_RejectedWithContract() {
        RecordingGateway gateway = new RecordingGateway();
        Router router = Router.of(new QuarryRoutes(gateway).routes());

        RestResponse response = router.handle(new RestRequest(
                "GET",
                QuarryRoutes.SEARCH,
                Map.of("resource", "dirt", "min", "300", "max", "100"),
                Map.of(),
                new byte[0]));

        assertEquals(
                400,
                response.status(),
                "min больше max — ошибка запроса: поиск не должен молча поменять диапазон; " + response.body()
                        + " Иначе тест search_BadRange_RejectedWithContract перестанет ловить эту поломку. При смене search_BadRange_RejectedWithContract проверь это утверждение.");
    }

    @Test
    void search_BadCoordinate_RejectedInsteadOfZero() {
        RecordingGateway gateway = new RecordingGateway();
        Router router = Router.of(new QuarryRoutes(gateway).routes());

        RestResponse response = router.handle(new RestRequest(
                "GET",
                QuarryRoutes.SEARCH,
                Map.of("resource", "dirt", "x", "восток", "y", "64", "z", "0"),
                Map.of(),
                new byte[0]));

        assertEquals(
                400,
                response.status(),
                "Нечисловая координата — ошибка запроса, а не точка (0,64,0): иначе поиск молча ищет не там"
                        + " При смене search_BadCoordinate_RejectedInsteadOfZero проверь это утверждение.");
    }

    @Test
    void install_StrategistBody_SetsSite() {
        RecordingGateway gateway = new RecordingGateway();
        Router router = Router.of(new QuarryRoutes(gateway).routes());
        String body = "{\"resource\":\"dirt\",\"x\":40,\"y\":64,\"z\":20,\"radius\":5,"
                + "\"biome\":\"minecraft:plains\",\"by\":\"strategist\"}";

        RestResponse response = router.handle(
                new RestRequest("POST", QuarryRoutes.SITE, Map.of(), Map.of(), body.getBytes(StandardCharsets.UTF_8)));

        assertEquals(
                200,
                response.status(),
                "POST /v1/quarry/site ставит место добычи"
                        + " Иначе тест install_StrategistBody_SetsSite перестанет ловить эту поломку. При смене install_StrategistBody_SetsSite проверь это утверждение.");
        QuarrySite site = gateway.installed;
        assertEquals(
                RouteResource.DIRT,
                site.resource(),
                "Ресурс места установлен"
                        + " Иначе тест install_StrategistBody_SetsSite перестанет ловить эту поломку. При смене install_StrategistBody_SetsSite проверь это утверждение.");
        assertEquals(
                new BlockCoord(40, 64, 20),
                site.point(),
                "Точка места установлена"
                        + " Иначе тест install_StrategistBody_SetsSite перестанет ловить эту поломку. При смене install_StrategistBody_SetsSite проверь это утверждение.");
        assertEquals(
                5,
                site.radius(),
                "Радиус области установлен"
                        + " Иначе тест install_StrategistBody_SetsSite перестанет ловить эту поломку. При смене install_StrategistBody_SetsSite проверь это утверждение.");
        assertEquals(
                "strategist",
                gateway.by,
                "Установку делает стратег или игрок, а не граф"
                        + " Иначе тест install_StrategistBody_SetsSite перестанет ловить эту поломку. При смене install_StrategistBody_SetsSite проверь это утверждение.");
    }

    @Test
    void install_GraphDecision_Rejected() {
        RecordingGateway gateway = new RecordingGateway();
        Router router = Router.of(new QuarryRoutes(gateway).routes());
        String body = "{\"resource\":\"dirt\",\"x\":0,\"y\":64,\"z\":0,\"by\":\"graph\"}";

        RestResponse response = router.handle(
                new RestRequest("POST", QuarryRoutes.SITE, Map.of(), Map.of(), body.getBytes(StandardCharsets.UTF_8)));

        assertEquals(
                400,
                response.status(),
                "Граф не выбирает место: 'by':'graph' отвергается контрактом; " + response.body()
                        + " Иначе тест install_GraphDecision_Rejected перестанет ловить эту поломку. При смене install_GraphDecision_Rejected проверь это утверждение.");
    }

    @Test
    void remove_Resource_UnsetsSite() {
        RecordingGateway gateway = new RecordingGateway();
        Router router = Router.of(new QuarryRoutes(gateway).routes());

        RestResponse response = router.handle(
                new RestRequest("DELETE", QuarryRoutes.SITE, Map.of("resource", "sand"), Map.of(), new byte[0]));

        assertEquals(
                200,
                response.status(),
                "DELETE /v1/quarry/site снимает место"
                        + " Иначе тест remove_Resource_UnsetsSite перестанет ловить эту поломку. При смене remove_Resource_UnsetsSite проверь это утверждение.");
        assertEquals(
                RouteResource.SAND,
                gateway.removed,
                "Снятие касается указанного ресурса"
                        + " Иначе тест remove_Resource_UnsetsSite перестанет ловить эту поломку. При смене remove_Resource_UnsetsSite проверь это утверждение.");
        assertTrue(
                response.body().contains("\"state\":\"unset\""),
                "Ответ подтверждает снятие: " + response.body()
                        + " Иначе тест remove_Resource_UnsetsSite перестанет ловить эту поломку. При смене remove_Resource_UnsetsSite проверь это утверждение.");
    }

    @Test
    void statuses_AllResources_Reported() {
        RecordingGateway gateway = new RecordingGateway();
        Router router = Router.of(new QuarryRoutes(gateway).routes());

        RestResponse response =
                router.handle(new RestRequest("GET", QuarryRoutes.STATUS, Map.of(), Map.of(), new byte[0]));

        assertEquals(
                200,
                response.status(),
                "GET /v1/quarry/status отдаёт состояние мест добычи"
                        + " Иначе тест statuses_AllResources_Reported перестанет ловить эту поломку. При смене statuses_AllResources_Reported проверь это утверждение.");
        assertTrue(
                response.body().contains("\"state\":\"unset\""),
                "Не заданное место видно как unset: " + "легенда и стратег не гадают"
                        + " Иначе тест statuses_AllResources_Reported перестанет ловить эту поломку. При смене statuses_AllResources_Reported проверь это утверждение.");
    }

    private static final class RecordingGateway implements QuarryGateway {
        private QuarryQuery query;
        private QuarrySite installed;
        private String by;
        private RouteResource removed;

        @Override
        public List<QuarryStatus> statuses() {
            return List.of(QuarryStatus.unset(RouteResource.DIRT), QuarryStatus.unset(RouteResource.SAND));
        }

        @Override
        public List<QuarryCandidate> search(QuarryQuery query) {
            this.query = query;
            return List.of(
                    new QuarryCandidate(query.resource(), new BlockCoord(200, 64, 0), 4, 120, 200, "minecraft:desert"));
        }

        @Override
        public QuarryStatus install(QuarryInstall install) {
            installed = install.site();
            by = install.by();
            return QuarryStatus.of(
                    new QuarrySite(install.resource(), install.point(), install.radius(), install.biome(), 120),
                    120,
                    false,
                    false);
        }

        @Override
        public QuarryStatus remove(RouteResource resource) {
            removed = resource;
            return QuarryStatus.unset(resource);
        }
    }
}
