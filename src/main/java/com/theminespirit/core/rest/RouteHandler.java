package com.theminespirit.core.rest;

@FunctionalInterface
public interface RouteHandler {
    RestResponse handle(RestRequest request, PathParams params);
}
