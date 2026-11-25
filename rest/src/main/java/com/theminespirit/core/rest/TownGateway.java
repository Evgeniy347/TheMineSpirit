package com.theminespirit.core.rest;

public interface TownGateway {
    String planDrawio();

    String planDrawio(int x1, int z1, int x2, int z2, int scale);

    byte[] terrainPng(int x1, int z1, int x2, int z2, int scale);

    byte[] previewDrawio(String body);

    String applyDrawio(String body);
}
