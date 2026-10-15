package com.theminespirit.neoforge.social;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.script.CatalogLimits;
import com.theminespirit.core.script.SayParams;
import com.theminespirit.core.social.SaySpacing;
import com.theminespirit.core.social.SayText;
import com.theminespirit.neoforge.SpiritEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

public final class SayScript implements ScriptInstance {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final SpiritEntity spirit;
    private final String rawText;
    private String failureReason;

    public SayScript(SpiritEntity spirit, SayParams params) {
        this.spirit = spirit;
        this.rawText = params.text();
    }

    @Override
    public ScriptStatus tick() {
        if (spirit.isRemoved()) {
            return fail("дух исчез, пока говорил");
        }
        String text;
        try {
            text = SayText.normalize(rawText, CatalogLimits.SAY_LENGTH_MAX);
        } catch (IllegalArgumentException empty) {
            return fail(empty.getMessage());
        }
        long now = spirit.tickCount;
        if (!SaySpacing.due(spirit.lastSayTick(), now, CatalogLimits.SAY_MIN_INTERVAL_TICKS)) {
            return fail("спам: с прошлой речи прошло " + (now - spirit.lastSayTick())
                    + " тиков, минимум " + CatalogLimits.SAY_MIN_INTERVAL_TICKS);
        }
        deliver(text);
        spirit.setLastSayTick(now);
        return ScriptStatus.SUCCEEDED;
    }

    @Override
    public void cancel() {
    }

    @Override
    public void suspend() {
    }

    @Override
    public String failureReason() {
        return failureReason;
    }

    private void deliver(String text) {
        if (!(spirit.level() instanceof ServerLevel level)) {
            return;
        }
        String line = "<" + spirit.getDisplayName().getString() + "> " + text;
        Component message = Component.literal(line);
        int heard = 0;
        for (ServerPlayer player : level.players()) {
            if (player.distanceTo(spirit) > CatalogLimits.SAY_HEARING_RADIUS) {
                continue;
            }
            player.sendSystemMessage(message);
            heard++;
        }
        LOGGER.info("Дух {} произнёс '{}' в радиусе {}: слышат {} игроков, дух в {}",
                spirit.getDisplayName().getString(), text, CatalogLimits.SAY_HEARING_RADIUS, heard,
                spirit.blockPosition());
    }

    private ScriptStatus fail(String reason) {
        failureReason = reason;
        return ScriptStatus.FAILED;
    }
}
