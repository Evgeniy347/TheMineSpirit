package com.theminespirit.neoforge.intellect;

import com.theminespirit.core.inventory.Stack;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExpeditionMember {
    UUID id();

    boolean removed();

    String displayName();

    Optional<String> currentScript();

    List<Stack> inventory();

    boolean hasPickaxe();
}
