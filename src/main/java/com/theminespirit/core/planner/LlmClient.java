package com.theminespirit.core.planner;

@FunctionalInterface
public interface LlmClient {
    String complete(String prompt);
}
