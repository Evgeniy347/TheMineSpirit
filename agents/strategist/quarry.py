"""Правило места добычи: стратег узнаёт «мало/иссякло/не задано» из REST и ставит лучшую выгодность.

Модель здесь не нужна: правило детерминировано (решение владельца 06.11). Мод место не выбирает.
"""

from __future__ import annotations

KEEPER_STATES = ("low", "empty", "unset")
RESOURCES = ("dirt", "sand")
MIN_RANGE = 128
MAX_RANGE = 512


def weak_sites(view) -> list[dict]:
    """Места, которым нужно решение: мало, иссякло или не задано; только земля и песок."""
    if not isinstance(view, dict):
        return []
    sites = view.get("sites")
    if not isinstance(sites, list):
        return []
    out = []
    for entry in sites:
        if not isinstance(entry, dict):
            continue
        resource = entry.get("resource")
        state = entry.get("state")
        if resource in RESOURCES and state in KEEPER_STATES:
            out.append(entry)
    return out


def best_candidate(candidates) -> dict | None:
    """Лучшая выгодность: запас на блок пути; при равенстве — короткий путь и устойчивые координаты."""
    if not isinstance(candidates, list):
        return None
    valid = []
    for candidate in candidates:
        if not isinstance(candidate, dict):
            continue
        point = candidate.get("point")
        reserve = candidate.get("reserve")
        path = candidate.get("path")
        if not isinstance(point, dict) or not isinstance(reserve, int) or isinstance(reserve, bool) or reserve <= 0:
            continue
        if not isinstance(path, int) or isinstance(path, bool) or path < 0:
            continue
        if not all(isinstance(point.get(axis), int) and not isinstance(point.get(axis), bool)
                   for axis in ("x", "y", "z")):
            continue
        valid.append(candidate)
    if not valid:
        return None
    return sorted(valid, key=lambda item: (
        -(item["reserve"] * 1000 // max(1, item["path"])),
        item["path"],
        item["point"]["x"],
        item["point"]["z"],
        str(item.get("biome", "")),
    ))[0]


def install_body(resource: str, candidate: dict) -> dict:
    point = candidate["point"]
    body = {
        "resource": resource,
        "x": point["x"],
        "y": point["y"],
        "z": point["z"],
        "radius": candidate.get("radius", 4),
        "biome": candidate.get("biome", ""),
        "by": "strategist",
    }
    return body


def search_path(resource: str) -> str:
    return f"/v1/quarry/search?resource={resource}&min={MIN_RANGE}&max={MAX_RANGE}"
