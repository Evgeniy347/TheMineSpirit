package com.theminespirit.neoforge.movement;

import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.script.GotoParams;
import com.theminespirit.neoforge.GotoScript;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.board.HomePoint;
import java.util.Map;

public final class ReturnHomeScript implements ScriptInstance {
    private static final String HOME_READ_KEY = "homeRead";

    private final GotoScript walk;

    public ReturnHomeScript(SpiritEntity spirit) {
        BlockCoord home = HomePoint.of(spirit).orElse(null);
        this.walk = home == null ? null : new GotoScript(spirit, new GotoParams(home.x(), home.y(), home.z()));
    }

    @Override
    public ScriptStatus tick() {
        if (walk == null) {
            return ScriptStatus.FAILED;
        }
        return walk.tick();
    }

    @Override
    public void cancel() {
        if (walk != null) {
            walk.cancel();
        }
    }

    @Override
    public void suspend() {
        if (walk != null) {
            walk.suspend();
        }
    }

    @Override
    public String failureReason() {
        return walk == null
                ? "дома нет ни у духа, ни у поселения: поставьте его командой /spirit home <дух> set"
                : walk.failureReason();
    }

    @Override
    public Map<String, Object> saveState() {
        return Map.of(HOME_READ_KEY, walk != null);
    }
}
