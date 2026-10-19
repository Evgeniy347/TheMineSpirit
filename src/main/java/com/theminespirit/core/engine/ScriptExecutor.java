package com.theminespirit.core.engine;

import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.policy.PolicyExemptions;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

final class ScriptExecutor extends NodeExecutor {
    private final String scriptId;
    private final Map<String, Object> params;
    private final ScriptRegistry registry;
    private @Nullable ScriptInstance instance;
    private boolean failed;
    private Optional<ScriptResult> collectedResult = Optional.empty();

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
                collectedResult = live.result();
                instance = null;
            }
            if (result == ScriptStatus.FAILED) {
                failed = true;
                String scriptReason = live.failureReason();
                fail(
                        scriptReason == null || scriptReason.isBlank()
                                ? "скрипт '" + scriptId + "' завершился с ошибкой"
                                : scriptReason);
            }
            return result;
        } catch (UnknownScriptException error) {
            cancelLive(error);
            failed = true;
            fail(error.getMessage(), error);
            return ScriptStatus.FAILED;
        } catch (RuntimeException error) {
            cancelLive(error);
            failed = true;
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
        ScriptFactory factory = registry.find(scriptId)
                .orElseThrow(() -> new UnknownScriptException(
                        "Неизвестный скрипт '" + scriptId + "': он не зарегистрирован в реестре исполнения"));
        return factory.create(params);
    }

    @Override
    @Nullable
    String currentScript() {
        return instance == null && !failed ? null : scriptId;
    }

    @Override
    boolean observesWhileTacticsOwns() {
        if (failed) {
            return false;
        }
        return registry.find(scriptId)
                .map(ScriptFactory::observesWhileTacticsOwns)
                .orElse(false);
    }

    @Override
    Optional<ScriptInstance.Progress> scriptProgress() {
        return instance == null ? Optional.empty() : instance.progress();
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
    Optional<ScriptResult> result() {
        return collectedResult;
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
