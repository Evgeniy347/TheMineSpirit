package com.theminespirit.core.engine;

import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import java.util.List;
import java.util.Map;

class TaskRunnerTestsSupport {
    static final String SCRIPT = "do";
    static final String CHECK = "yes";

    static TaskNode script(String id) {
        return new ScriptCall(id, Map.of());
    }

    static TaskNode sequence(TaskNode... steps) {
        return new SequenceNode(List.of(steps));
    }

    static final class CountingFactory {
        final ScriptFactory delegate;
        int created;

        CountingFactory(ScriptFactory delegate) {
            this.delegate = delegate;
        }

        ScriptFactory asFactory() {
            return params -> {
                created++;
                return delegate.create(params);
            };
        }
    }

    static final class SucceedingInstance implements ScriptInstance {
        @Override
        public ScriptStatus tick() {
            return ScriptStatus.SUCCEEDED;
        }
    }

    static final class FailingInstance implements ScriptInstance {
        @Override
        public ScriptStatus tick() {
            return ScriptStatus.FAILED;
        }
    }
}
