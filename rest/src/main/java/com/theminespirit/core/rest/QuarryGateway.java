package com.theminespirit.core.rest;

import com.theminespirit.core.board.route.QuarryCandidate;
import com.theminespirit.core.board.route.QuarryStatus;
import com.theminespirit.core.board.route.RouteResource;
import java.util.List;

public interface QuarryGateway {
    List<QuarryStatus> statuses();

    List<QuarryCandidate> search(QuarryQuery query);

    QuarryStatus install(QuarryInstall install);

    QuarryStatus remove(RouteResource resource);
}
