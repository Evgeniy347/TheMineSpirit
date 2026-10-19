package com.theminespirit.core.planner;

import java.util.function.Supplier;

final class StubLlmClient implements LlmClient {
    private final Supplier<String> behavior;
    private String lastPrompt;

    static StubLlmClient responding(String response) {
        return new StubLlmClient(() -> response);
    }

    static StubLlmClient throwing(RuntimeException error) {
        return new StubLlmClient(() -> {
            throw error;
        });
    }

    private StubLlmClient(Supplier<String> behavior) {
        this.behavior = behavior;
    }

    @Override
    public String complete(String prompt) {
        lastPrompt = prompt;
        return behavior.get();
    }

    String lastPrompt() {
        return lastPrompt;
    }
}
