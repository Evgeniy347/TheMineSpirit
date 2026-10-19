package com.theminespirit.core.tactics;

public record ActionRule(String id, int priority, Condition when, TacticalAction action) {}
