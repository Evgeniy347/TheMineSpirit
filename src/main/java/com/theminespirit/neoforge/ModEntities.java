package com.theminespirit.neoforge;

import com.theminespirit.core.ModIdentity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> REGISTRY =
            DeferredRegister.create(Registries.ENTITY_TYPE, ModIdentity.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<SpiritEntity>> SPIRIT = REGISTRY.register(
            "spirit",
            () -> EntityType.Builder.of(SpiritEntity::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.4F)
                    .clientTrackingRange(10)
                    .build("spirit"));

    private ModEntities() {}

    public static void register(IEventBus modEventBus) {
        REGISTRY.register(modEventBus);
    }

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(SPIRIT.get(), attributes());
    }

    private static AttributeSupplier attributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.ATTACK_DAMAGE, 1.0)
                .add(Attributes.STEP_HEIGHT, 1.0)
                .build();
    }
}
