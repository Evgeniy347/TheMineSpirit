package com.theminespirit.neoforge.experience;

import com.theminespirit.neoforge.SpiritEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

public final class SpiritExperienceEvents {
    public SpiritExperienceEvents(IEventBus bus) {
        bus.addListener(this::onDeath);
        bus.addListener(this::onBlockDrops);
        bus.addListener(this::onPlayerLoggedIn);
    }

    private void onDeath(LivingDeathEvent event) {
        LivingEntity killed = event.getEntity();
        if (killed instanceof SpiritEntity || killed.level().isClientSide()) {
            return;
        }
        SpiritEntity spirit = killerSpirit(event.getSource());
        if (spirit == null) {
            return;
        }
        int reward = killed.getExperienceReward((ServerLevel) killed.level(), spirit);
        if (reward <= 0) {
            return;
        }
        MinecraftServer server = killed.level().getServer();
        if (server == null) {
            return;
        }
        killed.skipDropExperience();
        ExperienceAwards.award(server, reward);
    }

    private void onBlockDrops(BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof SpiritEntity)) {
            return;
        }
        int experience = event.getDroppedExperience();
        if (experience <= 0) {
            return;
        }
        event.setDroppedExperience(0);
        ExperienceAwards.award(event.getLevel().getServer(), experience);
    }

    private void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        MinecraftServer server = event.getEntity().getServer();
        if (server != null) {
            ExperienceAwards.flushPool(server);
        }
    }

    private static SpiritEntity killerSpirit(DamageSource source) {
        Entity causing = source.getEntity();
        return causing instanceof SpiritEntity spirit ? spirit : null;
    }
}
