package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.graph.ActivityPlans;
import com.theminespirit.core.board.graph.GraphFacts;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

final class ActivityLaunch {
    private ActivityLaunch() {}

    static boolean launch(GraphPost post, BoardCase activity, String script, TaskNode task) {
        SpiritEntity spirit = idle(post);
        return spirit != null && start(post, spirit, activity, script, task);
    }

    static boolean launchOrQueue(
            GraphPost post, String key, int priority, BoardCase activity, String script, TaskNode task) {
        return GraphActivityPost.request(post, key, priority, activity, script, task);
    }

    static boolean launchOn(GraphPost post, SpiritEntity spirit, BoardCase activity, String script, TaskNode task) {
        if (spirit.isRemoved() || busy(post, spirit) || stuck(post, spirit)) {
            return false;
        }
        return start(post, spirit, activity, script, task);
    }

    static boolean launchNow(GraphPost post, SpiritEntity spirit, BoardCase activity, String script, TaskNode task) {
        return !spirit.isRemoved()
                && GraphActivityPost.launchNow(post, spirit, activity, new ActivityPlans.Plan(script, task));
    }

    private static boolean start(
            GraphPost post, SpiritEntity spirit, BoardCase activity, String script, TaskNode task) {
        return GraphActivityPost.launch(post, spirit, activity, new ActivityPlans.Plan(script, task));
    }

    private static @Nullable SpiritEntity idle(GraphPost post) {
        for (SpiritEntity spirit : post.spirits) {
            if (!spirit.isRemoved() && !busy(post, spirit) && !stuck(post, spirit)) {
                return spirit;
            }
        }
        return null;
    }

    static boolean stuck(GraphPost post, SpiritEntity spirit) {
        return stuck(post.facts, spirit.getUUID());
    }

    static boolean stuck(GraphFacts facts, UUID spirit) {
        var fact = facts.spirits().get(spirit.toString());
        return fact != null && (fact.stuck() || fact.trapped() || fact.trap() != null);
    }

    private static boolean busy(GraphPost post, SpiritEntity spirit) {
        if (!post.service.board().assignedTo(spirit.getUUID()).isEmpty()) {
            return true;
        }
        return spirit.currentTaskScript().isPresent() && !spirit.runsBackgroundTask();
    }
}
