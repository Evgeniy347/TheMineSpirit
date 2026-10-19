package com.theminespirit.core.rest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

public final class Router {
    private final List<Compiled> routes;

    private Router(List<Compiled> routes) {
        this.routes = routes;
    }

    public static Router of(List<Route> routes) {
        List<Compiled> compiled = new ArrayList<>(routes.size());
        Set<String> seen = new HashSet<>();
        for (Route route : routes) {
            if (!seen.add(route.method() + " " + route.pattern())) {
                throw new IllegalArgumentException("Маршрут " + route.method() + " " + route.pattern()
                        + " объявлен дважды: запрос не должен иметь двух обработчиков");
            }
            compiled.add(new Compiled(route, RoutePattern.parse(route.pattern())));
        }
        return new Router(List.copyOf(compiled));
    }

    public RestResponse handle(RestRequest request) {
        if (request.body().length > RestTuning.MAX_BODY_BYTES) {
            return ErrorJson.response(new ApiException(ErrorCode.INVALID_REQUEST,
                    "Тело запроса " + request.body().length + " байт больше лимита " + RestTuning.MAX_BODY_BYTES
                            + " байт: сократите тело запроса",
                    Map.of("limit_bytes", RestTuning.MAX_BODY_BYTES)));
        }
        Optional<List<String>> segments = RoutePattern.split(request.path());
        if (segments.isEmpty()) {
            return notFound(request);
        }
        Set<String> allowed = new TreeSet<>();
        for (Compiled compiled : routes) {
            Optional<PathParams> params = compiled.pattern().match(segments.get());
            if (params.isEmpty()) {
                continue;
            }
            if (compiled.route().method().equals(request.method())) {
                return invoke(compiled.route(), request, params.get());
            }
            allowed.add(compiled.route().method());
        }
        return allowed.isEmpty() ? notFound(request) : methodNotAllowed(request, allowed);
    }

    private static RestResponse invoke(Route route, RestRequest request, PathParams params) {
        try {
            return route.handler().handle(request, params);
        } catch (ApiException e) {
            return ErrorJson.response(e);
        }
    }

    private static RestResponse notFound(RestRequest request) {
        return ErrorJson.response(new ApiException(ErrorCode.NOT_FOUND,
                "Путь '" + request.path() + "' не найден: проверьте адрес по docs/api/README.md, например GET /v1/bots"));
    }

    private static RestResponse methodNotAllowed(RestRequest request, Set<String> allowed) {
        String allow = String.join(", ", allowed);
        RestResponse body = ErrorJson.response(new ApiException(ErrorCode.INVALID_REQUEST,
                "Метод '" + request.method() + "' не поддерживается для пути '" + request.path()
                        + "': допустимо " + allow,
                Map.of("allow", List.copyOf(allowed))));
        return new RestResponse(405, Map.of("Allow", allow), body.body());
    }

    private record Compiled(Route route, RoutePattern pattern) {
    }
}
