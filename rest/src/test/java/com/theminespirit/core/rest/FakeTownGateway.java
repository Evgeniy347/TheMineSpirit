package com.theminespirit.core.rest;

import java.util.ArrayList;
import java.util.List;

final class FakeTownGateway implements TownGateway {
    private final List<String> applied = new ArrayList<>();
    private final List<String> previews = new ArrayList<>();
    private final List<String> drawioFrames = new ArrayList<>();
    private final List<String> removed = new ArrayList<>();
    private String answer = "{\"ok\":true}";
    private String drawio = "<mxfile/>";
    private byte[] image = {1, 2, 3};
    private RuntimeException failure;

    void withAnswer(String json) {
        answer = json;
    }

    void withDrawio(String xml) {
        drawio = xml;
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

    List<String> drawioFrames() {
        return List.copyOf(drawioFrames);
    }

    List<String> removed() {
        return List.copyOf(removed);
    }

    @Override
    public String planDrawio() {
        drawioFrames.add("default");
        return drawio;
    }

    @Override
    public String planDrawio(int x1, int z1, int x2, int z2, int scale) {
        drawioFrames.add(x1 + "," + z1 + "," + x2 + "," + z2 + "," + scale);
        return drawio;
    }

    @Override
    public byte[] terrainPng(int x1, int z1, int x2, int z2, int scale) {
        return image;
    }

    @Override
    public byte[] previewDrawio(String body) {
        previews.add(body);
        if (failure != null) {
            throw failure;
        }
        return image;
    }

    @Override
    public String applyDrawio(String body) {
        applied.add(body);
        if (failure != null) {
            throw failure;
        }
        return answer;
    }

    @Override
    public String removePlot(String id) {
        removed.add(id);
        if (failure != null) {
            throw failure;
        }
        return answer;
    }
}
