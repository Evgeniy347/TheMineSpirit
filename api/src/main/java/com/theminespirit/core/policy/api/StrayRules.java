package com.theminespirit.core.policy.api;

public interface StrayRules {
    boolean natural(String blockId);

    boolean stray(String blockId);
}
