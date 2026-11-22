package com.theminespirit.core.rest;

public interface TownGateway {
    String viewJson();

    String editJson(String body, boolean dryRun);
}
