package com.theminespirit.core.route;

@FunctionalInterface
public interface RouteBudget {
    RouteBudget UNLIMITED = () -> false;

    boolean exhausted();
}
