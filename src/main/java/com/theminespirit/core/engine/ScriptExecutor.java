package com.theminespirit.core.engine;

import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.policy.PolicyExemptions;
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
            ScriptInstance live = instance;
            ScriptStatus result = live.tick();
            if (result != ScriptStatus.RUNNING) {
                instance = null;
            }
            if (result == ScriptStatus.FAILED) {
                String scriptReason = live.failureReason();
                fail(scriptReason == null || scriptReason.isBlank()
                        ? "скрипт '" + scriptId + "' завершился с ошибкой"
                        : scriptReason);
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
    TaskProgress buildProgress() {
        return new TaskProgress.ScriptAt(instance == null ? Map.of() : instance.saveState());
    }

    @Override
    void applyRestore(TaskProgress progress) {
        TaskProgress.ScriptAt at = (TaskProgress.ScriptAt) progress;
        instance = createInstance();
        instance.restoreState(at.state());
    }

    @Override
    void suspend() {
        if (instance != null) {
            instance.suspend();
        }
    }

    @Override
    void resume() {
        if (instance != null) {
            instance.resume();
        }
    }

    @Override
    PolicyExemptions exemptions() {
        return instance == null ? PolicyExemptions.empty() : instance.policyExemptions();
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
