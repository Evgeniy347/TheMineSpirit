package com.theminespirit.core.building;

public record ChestPair(boolean chest, boolean free, boolean supported, boolean reachable, boolean stale) {
    public enum Decision {
        PLACE,
        REFUSE
    }

    public Decision decision() {
        if (stale) {
            return Decision.REFUSE;
        }
        if (chest) {
            return Decision.PLACE;
        }
        return free && supported && reachable ? Decision.PLACE : Decision.REFUSE;
    }

    public String refusal() {
        if (stale) {
            return "во второй клетке остался BlockEntity без блока: сначала почините контейнер";
        }
        if (!free) {
            return "вторая половина занята: сначала освободите клетку";
        }
        if (!supported) {
            return "под второй половиной нет опоры: двойной сундук повиснет";
        }
        if (!reachable) {
            return "вторая половина вне досягаемости духа: сначала подойти";
        }
        return "вторая половина недоступна";
    }
}
