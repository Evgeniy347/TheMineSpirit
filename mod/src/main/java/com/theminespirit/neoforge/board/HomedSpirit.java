package com.theminespirit.neoforge.board;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.SpiritView;
import java.util.Optional;

public interface HomedSpirit extends SpiritView {
    Optional<BlockCoord> home();
}
