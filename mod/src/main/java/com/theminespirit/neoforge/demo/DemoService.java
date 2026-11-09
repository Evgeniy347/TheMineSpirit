package com.theminespirit.neoforge.demo;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.building.BuildRotation;
import com.theminespirit.core.building.DemoBuildings;
import com.theminespirit.core.building.DemoCity;
import com.theminespirit.core.building.DemoPlacement;
import com.theminespirit.core.building.GroundAnchor;
import com.theminespirit.core.building.Preset;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.reload.DataTables;
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
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

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

    public int start(
            CommandSourceStack source,
            DemoBuildings.Building building,
            int levelRequested,
            BlockPos explicit,
            BuildRotation rotation,
            int upgradeFrom,
            int upgradeTo) {
        ServerPlayer player = source.getPlayer();
        if (player != null) {
            stopOwnedBy(player.getUUID());
        }
        Optional<BlockPos> resolved = anchor(source, explicit);
        if (resolved.isEmpty()) {
            source.sendFailure(Component.literal(
                    "Не нашёл, где строить: встаньте и посмотрите на площадку " + "или укажите координаты x y z"));
            return 0;
        }
        BlockPos anchor = resolved.orElseThrow();
        int lookDx = player == null ? 0 : player.getDirection().getStepX();
        int lookDz = player == null ? 1 : player.getDirection().getStepZ();
        List<DemoPlan> plans;
        try {
            plans = plans(new PlanRequest(
                    building, levelRequested, anchor, lookDx, lookDz, rotation, upgradeFrom, upgradeTo));
        } catch (IllegalArgumentException refused) {
            source.sendFailure(Component.literal("Демо-стройка не начата: " + refused.getMessage()));
            return 0;
        }
        String refused = launch(plans, null, player == null ? null : player.getUUID());
        if (refused != null) {
            source.sendFailure(Component.literal(refused));
            return 0;
        }
        LOGGER.info(
                "Демо-стройка '{}' начата в {}: {}",
                building.id(),
                level.dimension().location(),
                format(plans));
        source.sendSuccess(
                () -> Component.literal("Демо-стройка '" + building.name() + "' начата: " + format(plans)
                        + ". Дух пишет ход в чат; отменить — /spirit demo stop"),
                true);
        return 1;
    }

    public int startCity(CommandSourceStack source, int cityLevel, BlockPos explicit) {
        ServerPlayer player = source.getPlayer();
        if (player != null) {
            stopOwnedBy(player.getUUID());
        }
        Optional<BlockPos> resolved = anchor(source, explicit);
        if (resolved.isEmpty()) {
            source.sendFailure(Component.literal(
                    "Не нашёл, где строить улицу: посмотрите на площадку или укажите координаты x y z"));
            return 0;
        }
        BlockPos start = resolved.orElseThrow();
        int lookDx = player == null ? 0 : player.getDirection().getStepX();
        int lookDz = player == null ? 1 : player.getDirection().getStepZ();
        List<DemoPlan> plans = new ArrayList<>();
        try {
            List<DemoCity.Pick> picks = DemoCity.pick(DemoBuildings.all(), DemoService::stageOf, cityLevel);
            List<DemoCity.Plot> plots = new ArrayList<>();
            for (DemoCity.Pick pick : picks) {
                Preset preset = DemoPlanner.preset(pick.level());
                plots.add(new DemoCity.Plot(
                        DemoPlacement.Door.of(preset),
                        new DemoPlacement.Size(preset.sizeX(), preset.sizeY(), preset.sizeZ())));
            }
            List<DemoPlacement> placements = DemoCity.layout(
                    new DemoPlacement.Anchor(start.getX(), start.getY(), start.getZ()),
                    new DemoPlacement.Look(lookDx, lookDz),
                    plots);
            for (int i = 0; i < picks.size(); i++) {
                plans.add(DemoPlanner.plan(picks.get(i).level(), placements.get(i)));
            }
        } catch (IllegalArgumentException refused) {
            source.sendFailure(Component.literal("Витрина не начата: " + refused.getMessage()));
            return 0;
        }
        if (plans.isEmpty()) {
            source.sendFailure(Component.literal(
                    "Витрина " + cityLevel + " пуста: в каталоге нет построек этапа " + (cityLevel - 1)));
            return 0;
        }
        int started = 0;
        for (DemoPlan plan : plans) {
            String refused = launch(List.of(plan), null, player == null ? null : player.getUUID());
            if (refused != null) {
                source.sendFailure(Component.literal(plan.name() + ": " + refused));
            } else {
                started++;
            }
        }
        int total = started;
        LOGGER.info(
                "Витрина {} начата в {}: {} построек из {}",
                cityLevel,
                level.dimension().location(),
                total,
                plans.size());
        source.sendSuccess(
                () -> Component.literal("Витрина этапа " + (cityLevel - 1) + ": строится " + total + " построек — "
                        + String.join(", ", plans.stream().map(DemoPlan::name).toList())
                        + ". Отменить — /spirit demo stop"),
                true);
        return total;
    }

    public int startWall(CommandSourceStack source, BlockPos explicit) {
        return DemoWall.start(this, level, source, explicit);
    }

    private static int stageOf(String scheme) {
        return DataTables.INSTANCE
                .presets()
                .current()
                .byId(scheme)
                .map(Preset::stage)
                .orElse(Integer.MAX_VALUE);
    }

    String launch(List<DemoPlan> plans, @Nullable DemoPlan site, UUID owner) {
        SpiritEntity spirit = ModEntities.SPIRIT.get().create(level);
        if (spirit == null) {
            return "Дух не создан: тип сущности " + ModEntities.SPIRIT.getId() + " не зарегистрирован";
        }
        DemoSession session = site == null
                ? new DemoSession(level, spirit, plans, owner)
                : new DemoSession(level, spirit, plans, owner, site);
        spirit.setDemo(true);
        spirit.setInvulnerable(true);
        BlockCoord spawn = (site == null ? plans.get(0) : site).placement().spawn();
        spirit.moveTo(spawn.x() + 0.5, spawn.y(), spawn.z() + 0.5);
        level.addFreshEntity(spirit);
        try {
            session.begin();
        } catch (IllegalStateException failure) {
            spirit.discard();
            return "Демо-стройка не начата: " + failure.getMessage();
        }
        sessions.add(session);
        return null;
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

    void stopOwnedBy(UUID owner) {
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
                throw new IllegalArgumentException("У постройки '" + building.id() + "' нет уровней: улучшать нечего");
            }
            if (upgradeFrom < 1 || upgradeTo <= upgradeFrom) {
                throw new IllegalArgumentException("Улучшение задано 'с " + upgradeFrom + " на " + upgradeTo
                        + "': нужен старший уровень выше младшего (например, 1 2)");
            }
            List<DemoPlan> steps = new ArrayList<>();
            steps.add(DemoPlanner.plan(building, upgradeFrom, anchor, lookDx, lookDz, rotation));
            for (DemoBuildings.Level step : building.levels()) {
                if (step.level() > upgradeFrom && step.level() < upgradeTo) {
                    steps.add(DemoPlanner.plan(building, step.level(), anchor, lookDx, lookDz, rotation));
                }
            }
            steps.add(DemoPlanner.plan(building, upgradeTo, anchor, lookDx, lookDz, rotation));
            return steps;
        }
        return List.of(DemoPlanner.plan(building, levelRequested, anchor, lookDx, lookDz, rotation));
    }

    Optional<BlockPos> anchor(CommandSourceStack source, BlockPos explicit) {
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
            groundY = GroundAnchor.groundY(
                    hitPos.getY(),
                    level.getMinBuildHeight(),
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
        return plans.get(0).name() + " ("
                + String.join(" → ", plans.stream().map(DemoPlan::scheme).toList()) + ")";
    }

    private record PlanRequest(
            DemoBuildings.Building building,
            int levelRequested,
            BlockPos anchor,
            int lookDx,
            int lookDz,
            BuildRotation rotation,
            int upgradeFrom,
            int upgradeTo) {}
}
