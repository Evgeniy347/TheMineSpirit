// lib: Из Baritone (github.com/cabaletta/baritone, pathing/calc/openset/IOpenSet.java), лицензия LGPL-3.0.
package com.theminespirit.core.navigation;

public interface NavOpenSet {
    void insert(NavNode node);

    boolean isEmpty();

    NavNode removeLowest();

    void update(NavNode node);
}
