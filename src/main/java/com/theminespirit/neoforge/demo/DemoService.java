package com.theminespirit.neoforge.demo;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.building.BuildRotation;
import com.theminespirit.core.building.DemoBuildings;
import com.theminespirit.core.building.GroundAnchor;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.slf4j.Logger;

/** Демо-стройка одного уровня: список активных сессий, подготовка площадки и уборка духов. */
public final class DemoService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final double PICK_REACH = 32.0;

    private final ServerLevel level;
    private final List<DemoSession> sessions = new ArrayList<>();

    private DemoService(ServerLevel level) {
        this.level = level;
    }

    static DemoService create(ServerLevel level) {
        return new DemoService(level);
    }

    public int start(CommandSourceStack source, DemoBuildings.Building building, int levelRequested,
            BlockPos explicit, BuildRotation rotation, int upgradeFrom, int upgradeTo) {
        ServerPlayer player = source.getPlayer();
        if (player != null) {
            stopOwnedBy(player.getUUID());
        }
        Optional<BlockPos> resolved = anchor(source, explicit);
        if (resolved.isEmpty()) {
            source.sendFailure(Component.literal("Не нашёл, где строить: встаньте и посмотрите на площадку "
                    + "или укажите координаты x y z"));
            return 0;
        }
        BlockPos anchor = resolved.orElseThrow();
        int lookDx = player == null ? 0 : player.getDirection().getStepX();
        int lookDz = player == null ? 1 : player.getDirection().getStepZ();
        List<DemoPlan> plans;
        try {
            plans = plans(new PlanRequest(building, levelRequested, anchor, lookDx, lookDz, rotation,
                    upgradeFrom, upgradeTo));
        } catch (IllegalArgumentException refused) {
            source.sendFailure(Component.literal("Демо-стройка не начата: " + refused.getMessage()));
            return 0;
        }
        SpiritEntity spirit = ModEntities.SPIRIT.get().create(level);
        if (spirit == null) {
            source.sendFailure(Component.literal("Дух не создан: тип сущности " + ModEntities.SPIRIT.getId()
                    + " не зарегистрирован"));
            return 0;
        }
        DemoSession session = new DemoSession(level, spirit, plans, player == null ? null : player.getUUID());
        spirit.setDemo(true);
        spirit.setInvulnerable(true);
        BlockCoord spawn = plans.get(0).placement().spawn();
        spirit.moveTo(spawn.x() + 0.5, spawn.y(), spawn.z() + 0.5);
        level.addFreshEntity(spirit);
        try {
            session.begin();
        } catch (IllegalStateException failure) {
            spirit.discard();
            source.sendFailure(Component.literal("Демо-стройка не начата: " + failure.getMessage()));
            return 0;
        }
        sessions.add(session);
        LOGGER.info("Демо-стройка '{}' начата в {}: {}", building.id(), level.dimension().location(),
                format(plans));
        source.sendSuccess(() -> Component.literal("Демо-стройка '" + building.name() + "' начата: "
                + format(plans) + ". Дух пишет ход в чат; отменить — /spirit demo stop"), true);
        return 1;
    }

    public void tick() {
        if (sessions.isEmpty()) {
            return;
        }
        sessions.removeIf(session -> !session.tick());
    }

    public int stop() {
        int removed = 0;
        for (DemoSession session : sessions) {
            session.abort();
            session.spirit().discard();
            removed++;
        }
        sessions.clear();
        return removed;
    }

    private void stopOwnedBy(UUID owner) {
        for (DemoSession session : new ArrayList<>(sessions)) {
            if (owner.equals(session.owner())) {
                session.abort();
                session.spirit().discard();
                sessions.remove(session);
            }
        }
    }

    private static List<DemoPlan> plans(PlanRequest request) {
        DemoBuildings.Building building = request.building();
        int levelRequested = request.levelRequested();
        BlockPos anchor = request.anchor();
        int lookDx = request.lookDx();
        int lookDz = request.lookDz();
        BuildRotation rotation = request.rotation();
        int upgradeFrom = request.upgradeFrom();
        int upgradeTo = request.upgradeTo();
        if (upgradeFrom > 0 || upgradeTo > 0) {
            if (!building.leveled()) {
                throw new IllegalArgumentException("У постройки '" + building.id()
                        + "' нет уровней: улучшать нечего");
            }
            if (upgradeFrom < 1 || upgradeTo <= upgradeFrom) {
                throw new IllegalArgumentException("Улучшение задано 'с " + upgradeFrom + " на " + upgradeTo
                        + "': нужен старший уровень выше младшего (например, 1 2)");
            }
            return List.of(DemoPlanner.plan(building, upgradeFrom, anchor, lookDx, lookDz, rotation),
                    DemoPlanner.plan(building, upgradeTo, anchor, lookDx, lookDz, rotation));
        }
        return List.of(DemoPlanner.plan(building, levelRequested, anchor, lookDx, lookDz, rotation));
    }

    private Optional<BlockPos> anchor(CommandSourceStack source, BlockPos explicit) {
        if (explicit != null) {
            return Optional.of(explicit);
        }
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return Optional.empty();
        }
        HitResult hit = player.pick(PICK_REACH, 1.0F, false);
        if (hit.getType() != HitResult.Type.BLOCK || !(hit instanceof BlockHitResult blockHit)) {
            return Optional.empty();
        }
        BlockPos hitPos = blockHit.getBlockPos();
        int groundY;
        try {
            groundY = GroundAnchor.groundY(hitPos.getY(), level.getMinBuildHeight(),
                    y -> isGround(new BlockPos(hitPos.getX(), y, hitPos.getZ())));
        } catch (IllegalArgumentException noGround) {
            return Optional.empty();
        }
        return Optional.of(new BlockPos(hitPos.getX(), groundY + 1, hitPos.getZ()));
    }

    private boolean isGround(BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isCollisionShapeFullBlock(level, pos) && !state.is(BlockTags.LEAVES);
    }

    private static String format(List<DemoPlan> plans) {
        if (plans.size() == 1) {
            return plans.get(0).name() + " (" + plans.get(0).scheme() + ")";
        }
        return plans.get(0).name() + " (" + plans.get(0).scheme() + " → " + plans.get(1).scheme() + ")";
    }

    private record PlanRequest(DemoBuildings.Building building, int levelRequested, BlockPos anchor, int lookDx,
            int lookDz, BuildRotation rotation, int upgradeFrom, int upgradeTo) {
    }
}
