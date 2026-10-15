package com.theminespirit.core.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.plan.ScriptCall;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ScriptExecutorTests {
    @Test
    void observesWhileTacticsOwns_FlagFromFactory_InstanceNotCreated() {
        AtomicInteger creations = new AtomicInteger();
        ScriptFactory factory = new ScriptFactory() {
            @Override
            public ScriptInstance create(Map<String, Object> params) {
                creations.incrementAndGet();
                return new ScriptInstance() {
                    @Override
                    public ScriptStatus tick() {
                        return ScriptStatus.SUCCEEDED;
                    }
                };
            }

            @Override
            public boolean observesWhileTacticsOwns() {
                return true;
            }
        };
        ScriptExecutor executor = new ScriptExecutor(new ScriptCall("watch", Map.of()),
                ScriptRegistry.of(Map.of("watch", factory)));

        assertTrue(executor.observesWhileTacticsOwns(),
                "флаг обязан читаться из фабрики: иначе движок не узнает, что скрипт наблюдает, пока тактика владеет");
        assertEquals(0, creations.get(),
                "чтение флага не имеет права конструировать скрипт: фабрика вызвана " + creations.get() + " раз"
                        + " (ревью 30, п. 5)");
    }
}
