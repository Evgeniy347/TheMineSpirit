package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.intellect.TaskStarter;
import org.jspecify.annotations.Nullable;

final class ActivityLaunch {
    private static final TaskStarter STARTER = new TaskStarter();

    private ActivityLaunch() {}

    static boolean launch(GraphPost post, BoardCase activity, String script, TaskNode task) {
        SpiritEntity spirit = idle(post);
        return spirit != null && start(post, spirit, activity, script, task);
    }

    static boolean launchOn(GraphPost post, SpiritEntity spirit, BoardCase activity, String script, TaskNode task) {
        if (spirit.isRemoved() || busy(post, spirit)) {
            return false;
        }
        return start(post, spirit, activity, script, task);
    }

    static boolean launchNow(GraphPost post, SpiritEntity spirit, BoardCase activity, String script, TaskNode task) {
        return !spirit.isRemoved() && start(post, spirit, activity, script, task);
    }

    private static boolean start(
            GraphPost post, SpiritEntity spirit, BoardCase activity, String script, TaskNode task) {
        return post.service.launch(spirit, activity, script, task, STARTER::start, post.now);
    }

    private static @Nullable SpiritEntity idle(GraphPost post) {
        for (SpiritEntity spirit : post.spirits) {
            if (!spirit.isRemoved() && !busy(post, spirit)) {
                return spirit;
            }
        }
        return null;
    }

    private static boolean busy(GraphPost post, SpiritEntity spirit) {
        if (!post.service.board().assignedTo(spirit.getUUID()).isEmpty()) {
            return true;
        }
        return spirit.currentTaskScript().isPresent() && !spirit.runsBackgroundTask();
    }
}
