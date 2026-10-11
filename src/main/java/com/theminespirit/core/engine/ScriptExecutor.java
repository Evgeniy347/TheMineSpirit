package com.theminespirit.core.engine;

import com.theminespirit.core.plan.ScriptCall;
import java.util.Map;

final class ScriptExecutor extends NodeExecutor {
    private final String scriptId;
    private final Map<String, Object> params;
    private final ScriptRegistry registry;
    private ScriptInstance instance;

    ScriptExecutor(ScriptCall call, ScriptRegistry registry) {
        this.scriptId = call.script();
        this.params = call.params();
        this.registry = registry;
    }

    @Override
    ScriptStatus advance() {
        try {
            if (instance == null) {
                instance = createInstance();
            }
            ScriptStatus result = instance.tick();
            if (result != ScriptStatus.RUNNING) {
                instance = null;
            }
            if (result == ScriptStatus.FAILED && failureReason() == null) {
                fail("скрипт '" + scriptId + "' завершился с ошибкой");
            }
            return result;
        } catch (UnknownScriptException error) {
            cancelLive(error);
            fail(error.getMessage(), error);
            return ScriptStatus.FAILED;
        } catch (RuntimeException error) {
            cancelLive(error);
            fail("скрипт '" + scriptId + "' упал: " + NodeExecutor.describe(error), error);
            return ScriptStatus.FAILED;
        }
    }

    private void cancelLive(RuntimeException failure) {
        if (instance == null) {
            return;
        }
        ScriptInstance live = instance;
        instance = null;
        try {
            live.cancel();
        } catch (RuntimeException cancelError) {
            failure.addSuppressed(cancelError);
        }
    }

    private ScriptInstance createInstance() {
        ScriptFactory factory = registry.find(scriptId).orElseThrow(() -> new UnknownScriptException(
                "Неизвестный скрипт '" + scriptId + "': он не зарегистрирован в реестре исполнения"));
        return factory.create(params);
    }

    @Override
    String currentScript() {
        return instance == null ? null : scriptId;
    }

    @Override
    void cancel() {
        if (instance != null) {
            ScriptInstance live = instance;
            instance = null;
            live.cancel();
        }
    }
}
