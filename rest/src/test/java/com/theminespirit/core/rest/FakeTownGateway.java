package com.theminespirit.core.rest;

import java.util.ArrayList;
import java.util.List;

final class FakeTownGateway implements TownGateway {
    private final List<String> applied = new ArrayList<>();
    private final List<String> previews = new ArrayList<>();
    private String plan = "{\"anchor\":{\"x\":0,\"z\":0},\"plots\":[],\"streets\":[],\"wall\":[]}";
    private String answer = "{\"ok\":true}";
    private byte[] image = {1, 2, 3};
    private RuntimeException failure;

    void withPlan(String json) {
        plan = json;
    }

    void withAnswer(String json) {
        answer = json;
    }

    void withImage(byte[] png) {
        image = png;
    }

    void withFailure(RuntimeException error) {
        failure = error;
    }

    List<String> applied() {
        return List.copyOf(applied);
    }

    List<String> previews() {
        return List.copyOf(previews);
    }

    @Override
    public String planJson() {
        return plan;
    }

    @Override
    public byte[] terrainPng(int x1, int z1, int x2, int z2, int scale) {
        return image;
    }

    @Override
    public byte[] previewPng(String body) {
        previews.add(body);
        if (failure != null) {
            throw failure;
        }
        return image;
    }

    @Override
    public String applyJson(String body) {
        applied.add(body);
        if (failure != null) {
            throw failure;
        }
        return answer;
    }
}
