package com.theminespirit.neoforge.tactics;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.tactics.CombatDirective;
import com.theminespirit.core.tactics.CombatMode;
import com.theminespirit.core.tactics.DirectiveOwner;
import com.theminespirit.core.tactics.Point;
import com.theminespirit.core.tactics.adapter.OrdersBuilder;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

final class TacticsOrders {
    static final CombatMode DEFAULT = CombatMode.DEFENSIVE;

    private final TacticsService host;
    private final TacticsDirectives directives;
    private CombatMode mode = DEFAULT;
    private Optional<OrdersBuilder.Zone> zone = Optional.empty();
    private Optional<UUID> ward = Optional.empty();

    TacticsOrders(TacticsService host) {
        this.host = host;
        this.directives = new TacticsDirectives(host);
    }

    CombatMode mode() {
        return mode;
    }

    void setMode(CombatMode next) {
        if (next == null) {
            throw new IllegalArgumentException("Режим боя = null: допустимы " + Arrays.toString(CombatMode.values()));
        }
        mode = next;
        host.markForced();
        if (next == CombatMode.PASSIVE) {
            host.spirit.setTarget(null);
        }
    }

    void restoreMode(CombatMode next) {
        if (next == null) {
            throw new IllegalArgumentException("Режим боя = null: укажите CombatMode");
        }
        mode = next;
    }

    Optional<OrdersBuilder.Zone> zone() {
        return zone;
    }

    void zone(BlockCoord center, double radius) {
        zone = Optional.of(new OrdersBuilder.Zone(new Point(center.x() + 0.5, center.y(), center.z() + 0.5), radius));
        host.markForced();
    }

    void zone(Optional<OrdersBuilder.Zone> next) {
        zone = next;
    }

    void clearZone() {
        zone = Optional.empty();
        host.markForced();
    }

    Optional<UUID> ward() {
        return ward;
    }

    void ward(@Nullable UUID id) {
        ward = Optional.ofNullable(id);
        host.markForced();
    }

    boolean hasState() {
        return mode != DEFAULT || zone.isPresent() || ward.isPresent();
    }

    TacticsDirectives directives() {
        return directives;
    }

    void applyDirective(CombatDirective directive, DirectiveOwner owner) {
        directives.apply(directive, owner);
    }

    boolean clearDirective(DirectiveOwner owner) {
        return directives.clear(owner);
    }

    void holdDirective() {
        directives.hold();
    }

    void setForcedTarget(LivingEntity target) {
        directives.setTarget(target);
    }
}
