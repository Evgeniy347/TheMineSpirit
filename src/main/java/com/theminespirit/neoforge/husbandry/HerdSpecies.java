package com.theminespirit.neoforge.husbandry;

import com.theminespirit.core.husbandry.Species;
import java.util.Optional;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Sheep;

public final class HerdSpecies {
    private HerdSpecies() {
    }

    public static Optional<Species> of(Entity entity) {
        if (entity instanceof Cow) {
            return Optional.of(Species.COW);
        }
        if (entity instanceof Sheep) {
            return Optional.of(Species.SHEEP);
        }
        if (entity instanceof Pig) {
            return Optional.of(Species.PIG);
        }
        if (entity instanceof Chicken) {
            return Optional.of(Species.CHICKEN);
        }
        return Optional.empty();
    }
}
