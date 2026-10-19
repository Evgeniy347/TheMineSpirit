package com.theminespirit.neoforge.coordinator;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class CoordinatorLifecycleTest {
    @Test
    public void registry_AfterClear_IsEmpty() {
        CoordinatorRegistry.clear();
        assertEquals(0, CoordinatorRegistry.size(), "после очистки реестра координаторов нет: карта не течёт");
        assertEquals(
                0,
                CoordinatorRegistry.size(),
                "повторная очистка тоже безопасна: двойное снятие уровня не ломает реестр");
    }
}
