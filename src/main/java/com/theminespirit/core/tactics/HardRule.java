package com.theminespirit.core.tactics;

public record HardRule(String id, int priority, Condition when, HardEffect effect) {
}
