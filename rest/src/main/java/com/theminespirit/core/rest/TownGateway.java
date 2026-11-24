package com.theminespirit.core.rest;

public interface TownGateway {
    String planJson();

    byte[] terrainPng(int x1, int z1, int x2, int z2, int scale);

    byte[] previewPng(String body);

    String applyJson(String body);
}
