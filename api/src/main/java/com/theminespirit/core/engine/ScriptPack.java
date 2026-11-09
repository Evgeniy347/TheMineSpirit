package com.theminespirit.core.engine;

import java.util.Map;
import java.util.Set;

/**
 * lib: набор скриптов одной механики. Регистрация и поиск скрипта — по контракту в базовом модуле:
 * хаб со всеми скриптами держал зависимость механик от :mod. Реализации живут там, где лежат сами
 * скрипты, а {@link ScriptPacks} собирает их в один реестр.
 *
 * @param <T> владелец скрипта (дух): фабрики замыкаются на конкретном владельце
 */
public interface ScriptPack<T> {
    String id();

    Set<String> scripts();

    Map<String, ScriptFactory> factories(T owner);
}
