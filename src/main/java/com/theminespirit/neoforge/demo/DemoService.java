package com.theminespirit.neoforge.demo;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.building.BuildRotation;
import com.theminespirit.core.building.DemoBuildings;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.slf4j.Logger;

/** Демо-стройка одного уровня: список активных сессий, подготовка площадки и уборка духов. */
public final class DemoService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final double PICK_REACH = 32.0;
    private static final int SPAWN_STEP_BACK = 2;

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
            plans = plans(building, levelRequested, anchor, lookDx, lookDz, rotation, upgradeFrom, upgradeTo);
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
        DemoSession session = new DemoSession(level, spirit, plans);
        spirit.moveTo(anchor.getX() + 0.5 - lookDx * SPAWN_STEP_BACK, anchor.getY(),
                anchor.getZ() + 0.5 - lookDz * SPAWN_STEP_BACK);
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
            SpiritEntity spirit = session.spirit();
            spirit.cancelTask();
            spirit.discard();
            removed++;
        }
        sessions.clear();
        return removed;
    }

    private static List<DemoPlan> plans(DemoBuildings.Building building, int levelRequested, BlockPos anchor,
            int lookDx, int lookDz, BuildRotation rotation, int upgradeFrom, int upgradeTo) {
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

    private static Optional<BlockPos> anchor(CommandSourceStack source, BlockPos explicit) {
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
        return Optional.of(blockHit.getBlockPos().above());
    }

    private static String format(List<DemoPlan> plans) {
        if (plans.size() == 1) {
            return plans.get(0).name() + " (" + plans.get(0).scheme() + ")";
        }
        return plans.get(0).name() + " (" + plans.get(0).scheme() + " → " + plans.get(1).scheme() + ")";
    }
}
