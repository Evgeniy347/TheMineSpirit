package com.theminespirit.neoforge.movement;

import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.script.CatalogLimits;
import com.theminespirit.core.script.ComeToPlayerParams;
import com.theminespirit.neoforge.Approach;
import com.theminespirit.neoforge.SpiritEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class ComeToPlayerScript implements ScriptInstance {
    public static final String NEAREST = "nearest";

    private final SpiritEntity spirit;
    private final String playerName;
    private final Approach approach;
    private ServerPlayer player;
    private boolean started;
    private String failureReason;

    public ComeToPlayerScript(SpiritEntity spirit, ComeToPlayerParams params) {
        this.spirit = spirit;
        this.playerName = params.player();
        this.approach = new Approach(spirit);
    }

    @Override
    public ScriptStatus tick() {
        if (spirit.isRemoved()) {
            approach.cancel();
            return fail("дух исчез по дороге к игроку");
        }
        if (!started) {
            started = true;
            player = findPlayer();
            if (player == null) {
                return fail(
                        NEAREST.equals(playerName)
                                ? "на сервере нет игрока, к которому можно подойти"
                                : "игрок '" + playerName + "' не найден на сервере");
            }
            String refusal = TravelCheck.refusal(spirit, cellOf(player));
            if (refusal != null) {
                return fail("игрок вне разрешённой зоны: " + refusal);
            }
            approach.begin(player, CatalogLimits.NAV_ARRIVAL_TOLERANCE);
        }
        if (player.isRemoved()) {
            approach.cancel();
            return fail("игрок '" + player.getName().getString() + "' ушёл с сервера по дороге");
        }
        return switch (approach.tick()) {
            case ARRIVED -> ScriptStatus.SUCCEEDED;
            case MOVING -> ScriptStatus.RUNNING;
            case TIMEOUT ->
                fail("не дошёл до игрока '" + player.getName().getString() + "' за " + approach.timeoutTicks()
                        + " тиков");
            case STUCK ->
                fail("застрял по дороге к игроку '" + player.getName().getString() + "' в (x=" + spirit.getX() + ", y="
                        + spirit.getY() + ", z=" + spirit.getZ() + ")");
        };
    }

    @Override
    public void cancel() {
        approach.cancel();
    }

    @Override
    public void suspend() {
        approach.suspend();
    }

    @Override
    public String failureReason() {
        return failureReason;
    }

    private ScriptStatus fail(String reason) {
        failureReason = reason;
        return ScriptStatus.FAILED;
    }

    private ServerPlayer findPlayer() {
        if (!(spirit.level() instanceof ServerLevel level)) {
            return null;
        }
        spirit.countEntityQuery();
        if (NEAREST.equals(playerName)) {
            return (ServerPlayer) level.getNearestPlayer(spirit, -1.0);
        }
        for (ServerPlayer candidate : level.players()) {
            if (candidate.getName().getString().equals(playerName)) {
                return candidate;
            }
        }
        return null;
    }

    private static BlockCoord cellOf(ServerPlayer target) {
        BlockPos cell = target.blockPosition();
        return new BlockCoord(cell.getX(), cell.getY(), cell.getZ());
    }
}
