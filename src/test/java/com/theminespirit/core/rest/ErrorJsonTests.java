package com.theminespirit.core.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ErrorJsonTests {
    private static final String DOC_EXAMPLE = "{\"error\":{\"code\":\"invalid_plan\",\"message\":"
            + "\"У скрипта 'wait' не задан обязательный параметр 'ticks' (INT)\",\"details\":{}}}";

    @Test
    void toJson_InvalidPlanError_IsByteEqualToApiDocExample() throws IOException {
        ApiError error = ApiError.of(ErrorCode.INVALID_PLAN,
                "У скрипта 'wait' не задан обязательный параметр 'ticks' (INT)");

        String json = ErrorJson.toJson(error);

        assertEquals(DOC_EXAMPLE, json,
                "Формат ошибки — контракт docs/API.md: {error:{code,message,details}} без пробелов и с details-объектом");
        String apiDoc = Files.readString(Path.of("docs/API.md"), StandardCharsets.UTF_8);
        assertTrue(apiDoc.contains(json),
                "Пример ошибки в docs/API.md обязан быть побайтно равен тому, что отдаёт мост: клиенты сверяют "
                        + "ответы с документом. Поправь пример в docs/API.md");
    }

    @Test
    void toJson_ErrorWithoutDetails_WritesEmptyObjectNotNull() {
        ApiError error = new ApiError(ErrorCode.NOT_FOUND, "нет", null);

        assertEquals("{\"error\":{\"code\":\"not_found\",\"message\":\"нет\",\"details\":{}}}",
                ErrorJson.toJson(error),
                "details всегда объект: клиент не должен проверять его на null");
    }

    @Test
    void toJson_ErrorWithDetails_KeepsInsertionOrderAndEscapesQuotes() {
        ApiError error = new ApiError(ErrorCode.INVALID_REQUEST, "поле \"radius\"",
                Map.of("field", "radius"));

        assertEquals("{\"error\":{\"code\":\"invalid_request\",\"message\":\"поле \\\"radius\\\"\","
                        + "\"details\":{\"field\":\"radius\"}}}",
                ErrorJson.toJson(error),
                "Кавычки в тексте обязаны экранироваться, иначе клиент получит битый JSON");
    }

    @Test
    void response_NotFoundException_CarriesStatusFromCode() {
        RestResponse response = ErrorJson.response(new ApiException(ErrorCode.NOT_FOUND, "нет бота"));

        assertEquals(404, response.status(),
                "Статус ответа берётся из кода ошибки: not_found обязан быть 404");
        assertEquals("{\"error\":{\"code\":\"not_found\",\"message\":\"нет бота\",\"details\":{}}}", response.body(),
                "Тело ответа — тот же объект ошибки");
    }
}
