package com.theminespirit.core.rest;

public interface WarehouseGateway {
    WarehouseView view();

    WarehouseChange decide(WarehouseDecision decision);
}
