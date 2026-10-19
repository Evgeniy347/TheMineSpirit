package com.theminespirit.core.planner;

import java.util.function.Supplier;

final class StubLlmClient implements LlmClient {
    private final Supplier<String> behavior;
    private String lastPrompt;

    private StubLlmClient(Supplier<String> behavior) {
        this.behavior = behavior;
    }

    static StubLlmClient responding(String response) {
        return new StubLlmClient(() -> response);
    }

    static StubLlmClient throwing(RuntimeException error) {
        return new StubLlmClient(() -> {
            throw error;
        });
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
