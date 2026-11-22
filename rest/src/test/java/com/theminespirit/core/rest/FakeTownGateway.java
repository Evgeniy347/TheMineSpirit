package com.theminespirit.core.rest;

import java.util.ArrayList;
import java.util.List;

final class FakeTownGateway implements TownGateway {
    private final List<String> edits = new ArrayList<>();
    private final List<Boolean> dryRuns = new ArrayList<>();
    private String view = "{\"stage\":1}";
    private String answer = "{\"ok\":true}";
    private RuntimeException failure;

    void withView(String json) {
        view = json;
    }

    void withAnswer(String json) {
        answer = json;
    }

    void withFailure(RuntimeException error) {
        failure = error;
    }

    List<String> edits() {
        return List.copyOf(edits);
    }

    List<Boolean> dryRuns() {
        return List.copyOf(dryRuns);
    }

    @Override
    public String viewJson() {
        return view;
    }

    @Override
    public String editJson(String body, boolean dryRun) {
        edits.add(body);
        dryRuns.add(dryRun);
        if (failure != null) {
            throw failure;
        }
        return answer;
    }
}
