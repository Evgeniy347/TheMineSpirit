package com.theminespirit.core.tactics.adapter;

import com.theminespirit.core.tactics.EnvFacts;
import com.theminespirit.core.tactics.Point;
import com.theminespirit.core.tactics.TimeOfDay;
import java.util.Optional;

public final class EnvFactsBuilder {
    private EnvFactsBuilder() {
    }

    public static EnvFacts build(EnvReading reading, Point self, int light, TimeOfDay time, Optional<Point> home) {
        double homeDistance = home.map(self::distanceTo).map(distance -> Math.min(distance, EnvFacts.FAR_DISTANCE))
                .orElse(EnvFacts.FAR_DISTANCE);
        return new EnvFacts(reading.lavaDistance(), reading.dropHeight(), light, time, homeDistance, home.isPresent(),
                reading.coverDistance(), reading.waterDistance());
    }
}
