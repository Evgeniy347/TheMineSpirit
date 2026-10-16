package com.theminespirit.core.rest;

import java.util.List;

/**
 * Платформенная граница доски для REST: очередь заданий поселения, постановка и отмена.
 * Ядро REST не знает про уровни и мир — адаптер находит доску уровня, назначает id задания
 * и кладёт снимок запасов в {@code missing}.
 */
public interface BoardGateway {

    List<BoardTask> tasks();

    BoardTask submit(BoardTaskRequest request);

    BoardTask cancel(String ticketId);
}
