package com.theminespirit.neoforge;

import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.neoforge.board.HomedSpirit;
import java.util.Optional;

public interface SpiritTaskView extends HomedSpirit {
    String displayName();

    void startTask(TaskNode plan);

    boolean runsBackgroundTask();

    void cancelTask();

    Optional<String> currentActivity();

    Optional<String> currentTaskScript();
}
