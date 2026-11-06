package com.theminespirit.neoforge;

import com.theminespirit.core.mining.MiningSafety;
import com.theminespirit.core.movement.PersonalSpace;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.reload.DataTables;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

final class PersonalSpaceKeeper {
    private static final double MOVE_SPEED = 1.0;

    private final SpiritEntity spirit;
    private int ticks;

    PersonalSpaceKeeper(SpiritEntity spirit) {
        this.spirit = spirit;
    }

    void tick() {
        PersonalSpace rules = DataTables.INSTANCE.personalSpace().current();
        if (++ticks % rules.checkIntervalTicks() != 0
                || spirit.isSleeping()
                || spirit.isPassenger()
                || !spirit.getNavigation().isDone()) {
            return;
        }
        AABB around = spirit.getBoundingBox().inflate(rules.keepDistance());
        List<PersonalSpace.Position> players = new ArrayList<>();
        for (Player player : spirit.level().getEntitiesOfClass(Player.class, around, Player::isAlive)) {
            if (!player.isSpectator()) {
                players.add(new PersonalSpace.Position(player.getX(), player.getY(), player.getZ()));
            }
        }
        PersonalSpace.Position self = new PersonalSpace.Position(spirit.getX(), spirit.getY(), spirit.getZ());
        if (players.isEmpty() || !rules.crowded(self, players)) {
            return;
        }
        LevelSafetyView safety = new LevelSafetyView(spirit.level());
        BlockCoord feet = new BlockCoord(spirit.getBlockX(), spirit.getBlockY(), spirit.getBlockZ());
        Optional<BlockCoord> step = rules.retreat(feet, players, cell -> MiningSafety.canStand(cell, safety));
        step.ifPresent(cell -> spirit.getNavigation().moveTo(cell.x() + 0.5, cell.y(), cell.z() + 0.5, MOVE_SPEED));
    }
}
