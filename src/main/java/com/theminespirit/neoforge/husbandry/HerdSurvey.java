package com.theminespirit.neoforge.husbandry;

import com.theminespirit.core.husbandry.AnimalRef;
import com.theminespirit.core.husbandry.PenBounds;
import com.theminespirit.core.husbandry.Species;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class HerdSurvey {
    private HerdSurvey() {
    }

    public static List<AnimalRef> snapshot(Level level, Vec3 center, double radius, PenBounds pen) {
        Objects.requireNonNull(level, "Уровень не может быть null: без него стадо не опросить");
        Objects.requireNonNull(center, "Центр поиска не может быть null: без него неизвестно, где стадо");
        Objects.requireNonNull(pen, "Загон не может быть null: без него неизвестно, кто отбился");
        AABB area = new AABB(center.x - radius, center.y - radius, center.z - radius, center.x + radius + 1.0,
                center.y + radius + 1.0, center.z + radius + 1.0);
        List<AnimalRef> herd = new ArrayList<>();
        for (Animal animal : level.getEntitiesOfClass(Animal.class, area, candidate -> !candidate.isRemoved())) {
            map(animal, pen).ifPresent(herd::add);
        }
        return List.copyOf(herd);
    }

    static Optional<AnimalRef> map(Animal animal, PenBounds pen) {
        Optional<Species> species = HerdSpecies.of(animal);
        if (species.isEmpty()) {
            return Optional.empty();
        }
        boolean baby = animal.isBaby();
        BlockCoord pos = new BlockCoord(animal.blockPosition().getX(), animal.blockPosition().getY(),
                animal.blockPosition().getZ());
        return Optional.of(new AnimalRef(
                animal.getStringUUID(),
                species.get(),
                baby,
                !baby && animal.getAge() == 0,
                !baby && animal instanceof Sheep sheep && !sheep.isSheared(),
                pos,
                pen.inside(pos)));
    }
}
