package com.theminespirit.core.rest;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ErrorJson {
    private ErrorJson() {}

    public static String toJson(ApiError error) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", error.code().wire());
        body.put("message", error.message());
        body.put("details", error.details());
        return RestJson.write(Map.of("error", body));
    }

    public static RestResponse response(ApiError error) {
        return RestResponse.json(error.code().status(), toJson(error));
    }

    public static RestResponse response(ApiException exception) {
        return RestResponse.json(exception.status(), toJson(exception.error()));
    }
}
