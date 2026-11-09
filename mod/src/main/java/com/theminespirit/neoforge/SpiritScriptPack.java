package com.theminespirit.neoforge;

import com.theminespirit.core.engine.ScriptFactory;
import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptPack;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * lib: общий каркас набора скриптов духа: механика объявляет пары id → фабрика, а реестр собирает
 * ScriptPacks. Так каждая механика регистрирует свои скрипты сама, без общего хаба на все реализации.
 */
abstract class SpiritScriptPack implements ScriptPack<SpiritEntity> {
    private Map<String, Function<SpiritEntity, ScriptFactory>> builders;

    protected abstract void register();

    protected final void put(String id, Function<SpiritEntity, ScriptFactory> builder) {
        if (builders().putIfAbsent(id, builder) != null) {
            throw new IllegalStateException("Скрипт '" + id + "' набора '" + id() + "' объявлен дважды");
        }
    }

    @Override
    public final Set<String> scripts() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(builders().keySet()));
    }

    @Override
    public final Map<String, ScriptFactory> factories(SpiritEntity owner) {
        Map<String, ScriptFactory> factories = new LinkedHashMap<>();
        builders().forEach((id, builder) -> factories.put(id, builder.apply(owner)));
        return factories;
    }

    protected static ScriptFactory observing(ScriptFactory delegate) {
        return new ScriptFactory() {
            @Override
            public ScriptInstance create(Map<String, Object> params) {
                return delegate.create(params);
            }

            @Override
            public boolean observesWhileTacticsOwns() {
                return true;
            }
        };
    }

    private Map<String, Function<SpiritEntity, ScriptFactory>> builders() {
        Map<String, Function<SpiritEntity, ScriptFactory>> current = builders;
        if (current == null) {
            current = new LinkedHashMap<>();
            builders = current;
            register();
        }
        return current;
    }
}
