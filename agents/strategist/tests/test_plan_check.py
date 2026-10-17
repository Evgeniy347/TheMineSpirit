import copy
import json
import random
import unittest
from pathlib import Path

from plan_check import (Catalog, CatalogError, PlanContext, PlanError, PlanLimits, check_plan, context_from_observation,
                        load_policy)
from tests.fake_rest import default_capabilities

APP = Path(__file__).resolve().parent.parent


def script(name, **params):
    return {"type": "script", "script": name, "params": params}


def many(count, node_factory):
    return [node_factory(i) for i in range(count)]


def deep(levels):
    node = script("scan")
    for _ in range(levels):
        node = {"type": "sequence", "steps": [node]}
    return node


VALID = [
    ("goto с координатами", script("goto", x=10, y=64, z=-3)),
    ("scan без params", {"type": "script", "script": "scan"}),
    ("sequence goto + chop_tree", {"type": "sequence", "steps": [script("goto", x=1, y=64, z=1), script("chop_tree", count=5)]}),
    ("fallback из двух", {"type": "fallback", "steps": [script("mine_vein", ore="iron"), script("gather", block="stone", count=8)]}),
    ("repeat 3", {"type": "repeat", "times": 3, "step": script("chop_tree", count=2)}),
    ("repeat границы 1 и 64", {"type": "sequence", "steps": [
        {"type": "repeat", "times": 1, "step": script("scan")}, {"type": "repeat", "times": 64, "step": script("scan")}]}),
    ("parallel all", {"type": "parallel", "mode": "all", "steps": [script("scan"), script("wait", ticks=20)]}),
    ("wait 6000", script("wait", ticks=6000)),
    ("wait 1", script("wait", ticks=1)),
    ("chop_tree с пустыми params", script("chop_tree")),
    ("mine_vein enum", script("mine_vein", ore="diamond", count=2, radius=32)),
    ("harvest_crop replant=false", script("harvest_crop", crop="wheat", replant=False)),
    ("вложенные sequence в fallback", {"type": "fallback", "steps": [
        {"type": "sequence", "steps": [script("goto", x=0, y=70, z=0), script("collect_drops", radius=16)]},
        script("wait", ticks=100)]}),
    ("scan с filter", script("scan", radius=16, filter=["*_log", "*_ore"])),
    ("acquire с нуля", script("acquire", item="wooden_pickaxe", count=1)),
    ("acquire с namespace", script("acquire", item="minecraft:oak_log", count=8)),
    ("craft из имеющегося", script("craft", item="wooden_pickaxe", count=1)),
    ("equip точного предмета", script("equip", item="iron_pickaxe")),
    ("equip шаблона", script("equip", item="*_pickaxe")),
    ("withdraw из ближайшего сундука", script("withdraw", item="iron_pickaxe", count=1)),
    ("withdraw с координатами", script("withdraw", container="8,63,-5", item="minecraft:oak_log", count=8, radius=8)),
    ("глубина ровно 16", deep(15)),
    ("8 шагов в корне", {"type": "sequence", "steps": many(8, lambda i: script("scan"))}),
    ("32 узла ровно", {"type": "sequence", "steps": [
        {"type": "sequence", "steps": many(7, lambda i: script("scan"))},
        {"type": "sequence", "steps": many(7, lambda i: script("scan"))},
        {"type": "sequence", "steps": many(7, lambda i: script("scan"))},
        {"type": "sequence", "steps": many(6, lambda i: script("scan"))}]}),
]

INVALID = [
    ("план — массив", [script("scan")], "план"),
    ("план — null", None, "план"),
    ("нет type", {"script": "scan"}, "type"),
    ("неизвестный type", {"type": "loop", "steps": []}, "loop"),
    ("script без имени", {"type": "script", "params": {}}, "script"),
    ("скрипта нет в каталоге", script("teleport"), "teleport"),
    ("скрипт есть в каталоге, но вне белого списка", script("fill", block="stone", x=1, y=1, z=1), "белого списка"),
    ("say вне белого списка", script("say", text="привет"), "say"),
    ("params не объект", {"type": "script", "script": "scan", "params": [1]}, "params"),
    ("лишний параметр", script("scan", foo=1), "foo"),
    ("нет обязательного z", script("goto", x=1, y=64), "plan.params.z"),
    ("count ниже минимума", script("chop_tree", count=0), "plan.params.count"),
    ("count выше максимума во вложенном шаге", {"type": "sequence", "steps": [script("scan"), script("chop_tree", count=257)]},
     "plan.steps[1].params.count"),
    ("дробное вместо целого", script("chop_tree", count=2.5), "count"),
    ("bool вместо целого", script("chop_tree", count=True), "count"),
    ("строка вместо целого", script("chop_tree", count="5"), "count"),
    ("enum вне перечня", script("mine_vein", ore="adamantium"), "adamantium"),
    ("wait.ticks выше границы", script("wait", ticks=6001), "plan.params.ticks"),
    ("wait.ticks ниже границы", script("wait", ticks=0), "ticks"),
    ("wait без ticks", script("wait"), "ticks"),
    ("repeat times 0", {"type": "repeat", "times": 0, "step": script("scan")}, "times"),
    ("repeat times 65", {"type": "repeat", "times": 65, "step": script("scan")}, "times"),
    ("repeat times bool", {"type": "repeat", "times": True, "step": script("scan")}, "times"),
    ("repeat без step", {"type": "repeat", "times": 2}, "step"),
    ("sequence пустой", {"type": "sequence", "steps": []}, "steps"),
    ("steps не список", {"type": "sequence", "steps": script("scan")}, "steps"),
    ("parallel с неверным mode", {"type": "parallel", "mode": "some", "steps": [script("scan")]}, "mode"),
    ("condition без каталога проверок", {"type": "condition", "check": "has_item", "params": {}, "then": script("scan")},
     "condition"),
    ("больше 32 узлов", {"type": "sequence", "steps": [
        {"type": "sequence", "steps": many(7, lambda i: script("scan"))} for _ in range(5)]}, "узлов"),
    ("9 шагов в корне", {"type": "sequence", "steps": many(9, lambda i: script("scan"))}, "шагов"),
    ("глубина 17", deep(16), "глубина"),
    ("300 узлов", {"type": "sequence", "steps": [
        {"type": "sequence", "steps": many(30, lambda i: script("scan"))} for _ in range(10)]}, "узлов"),
    ("лишнее поле узла", {"type": "script", "script": "scan", "comment": "привет"}, "comment"),
    ("слишком длинная строка", script("chop_tree", species="a" * 201), "species"),
    ("не строка в списке", script("scan", filter=["ok", 3]), "filter"),
    ("строка вместо списка", script("scan", filter="*_log"), "filter"),
    ("NaN вместо числа", script("wait", ticks=float("nan")), "ticks"),
    ("null в параметре", script("chop_tree", count=None), "count"),
    ("список слишком длинный", script("scan", filter=["a"] * 33), "filter"),
    ("acquire без item", script("acquire", count=1), "item"),
    ("acquire без count", script("acquire", item="wooden_pickaxe"), "count"),
    ("acquire count 0", script("acquire", item="wooden_pickaxe", count=0), "count"),
    ("craft count 257", script("craft", item="wooden_pickaxe", count=257), "count"),
    ("withdraw без item", script("withdraw", count=1), "item"),
    ("withdraw count 0", script("withdraw", item="iron_pickaxe", count=0), "count"),
    ("equip без item", script("equip"), "item"),
    ("acquire с пустым id", script("acquire", item="", count=1), "item"),
    ("acquire с пробелом в id", script("acquire", item="wooden pickaxe", count=1), "item"),
    ("craft с заглавными буквами", script("craft", item="Wooden_Pickaxe", count=1), "item"),
    ("withdraw с шаблоном", script("withdraw", item="*_pickaxe", count=1), "item"),
    ("acquire с пустым namespace", script("acquire", item=":oak_log", count=1), "item"),
    ("acquire с пустым path", script("acquire", item="minecraft:", count=1), "item"),
    ("acquire с двумя двоеточиями", script("acquire", item="a:b:c", count=1), "item"),
]


class PlanCheckTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.catalog = Catalog.from_capabilities(default_capabilities())
        cls.policy = load_policy(APP / "policy.json")
        cls.limits = PlanLimits()

    def check(self, plan):
        return check_plan(plan, self.catalog, self.policy, self.limits)

    def test_validPlans_Table_Accepted(self):
        for name, plan in VALID:
            with self.subTest(plan=name):
                try:
                    self.check(plan)
                except PlanError as error:
                    self.fail(f"допустимый план «{name}» отвергнут: {error}. Проверь plan_check против docs/API.md и каталога")

    def test_invalidPlans_Table_RejectedWithRussianTextNamingNode(self):
        self.assertGreaterEqual(len(VALID) + len(INVALID), 30, "в таблице не меньше 30 планов")
        for name, plan, fragment in INVALID:
            with self.subTest(plan=name):
                with self.assertRaises(PlanError, msg=f"недопустимый план «{name}» должен отвергаться до отправки") as ctx:
                    self.check(plan)
                text = str(ctx.exception)
                self.assertIn(fragment.lower(), text.lower(), f"сообщение «{text}» должно называть «{fragment}»")
                self.assertTrue(any("а" <= ch.lower() <= "я" for ch in text), "текст ошибки по-русски")

    def test_error_NestedParam_NamesFullPath(self):
        plan = {"type": "fallback", "steps": [script("scan"), {"type": "sequence", "steps": [
            script("scan"), script("scan"), script("chop_tree", count=999)]}]}
        with self.assertRaises(PlanError) as ctx:
            self.check(plan)
        self.assertIn("plan.steps[1].steps[2].params.count", str(ctx.exception))

    def test_error_SeveralProblems_AllReported(self):
        plan = {"type": "sequence", "steps": [script("chop_tree", count=0), script("teleport"), script("wait")]}
        with self.assertRaises(PlanError) as ctx:
            self.check(plan)
        self.assertGreaterEqual(len(ctx.exception.errors), 3, "модель получает все проблемы плана разом")

    def test_whitelist_ScriptOutsideList_RejectedEvenIfCatalogHasIt(self):
        policy = load_policy(APP / "policy.json")
        self.assertIn("fill", {s["id"] for s in default_capabilities()["scripts"]}, "для теста fill должен быть в каталоге")
        self.assertNotIn("fill", policy.allowed_scripts)
        with self.assertRaises(PlanError):
            check_plan(script("fill", block="stone", x=1, y=1, z=1), self.catalog, policy, self.limits)

    def test_policy_Whitelist_MatchesChecklists(self):
        expected = {"goto", "scan", "collect_drops", "chop_tree", "mine_vein", "gather", "till", "plant_crop",
                    "harvest_crop", "bone_meal", "wait", "acquire", "craft", "equip", "withdraw"}
        self.assertEqual(expected, set(self.policy.allowed_scripts),
                         "белый список — чеклист 22 плюс инструментальные скрипты чеклиста 38Б")

    def test_limits_Configurable(self):
        plan = {"type": "sequence", "steps": many(3, lambda i: script("scan"))}
        check_plan(plan, self.catalog, self.policy, PlanLimits(max_nodes=4, max_root_steps=3))
        with self.assertRaises(PlanError):
            check_plan(plan, self.catalog, self.policy, PlanLimits(max_nodes=3, max_root_steps=3))
        with self.assertRaises(PlanError):
            check_plan(plan, self.catalog, self.policy, PlanLimits(max_nodes=32, max_root_steps=2))

    def test_condition_WithCatalogCheck_Accepted(self):
        capabilities = default_capabilities()
        capabilities["conditions"] = [{"id": "has_item", "params": {"type": "object", "properties": {
            "item": {"type": "string"}}, "required": ["item"]}}]
        catalog = Catalog.from_capabilities(capabilities)
        plan = {"type": "condition", "check": "has_item", "params": {"item": "minecraft:stick"},
                "then": script("scan"), "else": script("wait", ticks=10)}
        check_plan(plan, catalog, self.policy, self.limits)
        plan["params"] = {}
        with self.assertRaises(PlanError):
            check_plan(plan, catalog, self.policy, self.limits)

    def test_numberParam_NonFiniteAndWrongTypes_Rejected(self):
        capabilities = {"scripts": [{"id": "wait", "params": {"type": "object", "properties": {
            "factor": {"type": "number", "minimum": 0, "maximum": 2}}, "required": []}}]}
        catalog = Catalog.from_capabilities(capabilities)
        policy = load_policy(APP / "policy.json")
        for value in (0.5, 1, 2.0):
            check_plan(script("wait", factor=value), catalog, policy, self.limits)
        for value in (float("nan"), float("inf"), 2.5, -0.1, True, "1"):
            with self.subTest(value=value):
                with self.assertRaises(PlanError, msg=f"number-параметр со значением {value!r} должен отвергаться"):
                    check_plan(script("wait", factor=value), catalog, policy, self.limits)

    def test_catalog_Malformed_RaisesCatalogError(self):
        for bad in ({}, {"scripts": "x"}, {"scripts": [{"summary": "нет id"}]}, {"scripts": [{"id": "a", "params": 5}]}):
            with self.subTest(bad=bad):
                with self.assertRaises(CatalogError):
                    Catalog.from_capabilities(bad)

    def test_check_Deterministic(self):
        for name, plan, _ in INVALID:
            with self.subTest(plan=name):
                results = []
                for _ in range(3):
                    try:
                        self.check(plan)
                        results.append(None)
                    except PlanError as error:
                        results.append(tuple(error.errors))
                self.assertEqual(1, len(set(results)), "одна и та же проверка даёт один и тот же ответ")

    def test_check_DoesNotMutatePlan(self):
        for name, plan in VALID:
            before = copy.deepcopy(plan)
            self.check(plan)
            self.assertEqual(before, plan, f"check_plan не должен менять план «{name}»")

    def test_examples_AllPassPlanCheck(self):
        files = sorted((APP / "prompt" / "examples").glob("*.json"))
        self.assertGreaterEqual(len(files), 3, "в prompt/examples нужно 3–4 образца")
        for path in files:
            with self.subTest(example=path.name):
                example = json.loads(path.read_text(encoding="utf-8"))
                self.assertIn("reason", example)
                if example.get("skip"):
                    self.assertNotIn("plan", example)
                    continue
                try:
                    self.check(example["plan"])
                except PlanError as error:
                    self.fail(f"образец {path.name} не проходит plan_check: {error}")


def goto(x, z, y=64):
    return script("goto", x=x, y=y, z=z)


class WorldRulesTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.catalog = Catalog.from_capabilities(default_capabilities())
        cls.policy = load_policy(APP / "policy.json")
        cls.context = PlanContext((100, 64, -50), frozenset({"stone", "oak_log", "grass_block"}))

    def check(self, plan, context="default"):
        return check_plan(plan, self.catalog, self.policy, PlanLimits(), self.context if context == "default" else context)

    def test_policy_LoadsWorldRuleSettings(self):
        self.assertEqual(256, self.policy.max_goto_distance)
        self.assertEqual((("gather", "block"),), self.policy.observed_block_params)

    def test_goto_HorizontalDistanceBoundary256(self):
        self.check(goto(100 + 256, -50))
        self.check(goto(100 - 256, -50))
        self.check(goto(100, -50 + 256, y=300))
        for far in (goto(100 + 257, -50), goto(100, -50 - 257), goto(100 + 190, -50 + 190)):
            with self.assertRaises(PlanError, msg="цель дальше 256 блоков по горизонтали отвергается") as ctx:
                self.check(far)
            self.assertIn("по горизонтали", str(ctx.exception))
        self.check(goto(100 + 180, -50 + 180))

    def test_goto_InsideNestedPlan_NamesFullPath(self):
        plan = {"type": "sequence", "steps": [script("scan"), goto(5000, 5000)]}
        with self.assertRaises(PlanError) as ctx:
            self.check(plan)
        self.assertIn("plan.steps[1].params", str(ctx.exception))

    def test_goto_WithoutContext_NotChecked(self):
        self.check(goto(29_000_000, 0), context=None)

    def test_goto_UnknownSpiritPosition_Rejected(self):
        with self.assertRaises(PlanError) as ctx:
            self.check(goto(1, 1), context=PlanContext(None, frozenset()))
        self.assertIn("позиция духа", str(ctx.exception))

    def test_gather_OnlyBlocksFromObservation(self):
        self.check(script("gather", block="stone", count=5))
        self.check(script("gather", block="minecraft:stone", count=5))
        self.check(script("gather", block="oak_log", count=5))
        for bad in ("diamond_ore", "minecraft:iron_ore", "*_log", "stone,oak_log", ""):
            with self.subTest(block=bad):
                with self.assertRaises(PlanError, msg=f"блок {bad!r} отсутствует в наблюдении") as ctx:
                    self.check(script("gather", block=bad, count=5))
                self.assertIn("plan.params.block", str(ctx.exception))
                self.assertIn("наблюдении", str(ctx.exception))

    def test_gather_ObservedBlocksUnknownOrEmpty(self):
        self.check(script("gather", block="stone", count=1), context=PlanContext((0, 0, 0), None))
        with self.assertRaises(PlanError):
            self.check(script("gather", block="stone", count=1), context=PlanContext((0, 0, 0), frozenset()))

    def test_gather_NotNaturalBlock_RejectedEvenWhenObservationUnknown(self):
        with self.assertRaises(PlanError, msg="«whatever» не природный блок: отказ не зависит от наблюдения") as ctx:
            self.check(script("gather", block="whatever", count=1), context=PlanContext((0, 0, 0), None))
        self.assertIn("natural_blocks", str(ctx.exception))
        self.check(script("gather", block="minecraft:stone", count=1), context=PlanContext((0, 0, 0), None))

    def test_gather_NaturalBlockFromObservation_Accepted(self):
        observed = frozenset({"stone", "iron_ore", "deepslate_iron_ore", "sand", "gravel", "clay", "dirt",
                              "oak_log", "oak_leaves", "cactus"})
        context = PlanContext((0, 64, 0), observed)
        for block in ("stone", "minecraft:iron_ore", "deepslate_iron_ore", "sand", "gravel", "clay", "dirt",
                      "oak_log", "oak_leaves", "cactus"):
            with self.subTest(block=block):
                self.check(script("gather", block=block, count=4), context=context)

    def test_gather_PlayerMadeBlockFromObservation_RejectedNamingNaturalList(self):
        observed = frozenset({"oak_planks", "glass", "bricks", "white_bed", "oak_door", "stone"})
        context = PlanContext((0, 64, 0), observed)
        for block in ("oak_planks", "minecraft:glass", "bricks", "white_bed", "oak_door"):
            with self.subTest(block=block):
                with self.assertRaises(PlanError, msg=f"блок {block!r} сделал игрок: gather.block его не примет") as ctx:
                    self.check(script("gather", block=block, count=4), context=context)
                text = str(ctx.exception)
                self.assertIn("plan.params.block", text)
                self.assertIn("natural_blocks", text)
                self.assertIn(block.lower().replace("minecraft:", ""), text.lower())
                self.assertTrue(any("а" <= ch.lower() <= "я" for ch in text), "текст ошибки по-русски")

    def test_policy_NaturalBlocks_CoverNaturalGroupsAndExcludePlayerMade(self):
        natural = self.policy.natural_blocks
        self.assertGreaterEqual(len(natural), 100,
                                "список природных блоков — данные правила: он должен покрывать ванильный мир 1.21.1, "
                                "а не отдельные примеры")
        for block in ("iron_ore", "deepslate_iron_ore", "diamond_ore", "coal_ore", "stone", "deepslate", "granite",
                      "dirt", "grass_block", "sand", "red_sand", "gravel", "clay", "oak_log", "birch_log",
                      "spruce_log", "oak_leaves", "birch_leaves", "cactus", "ice", "obsidian", "terracotta"):
            with self.subTest(block=block):
                self.assertIn(block, natural, f"природного блока {block!r} нет в natural_blocks: модельный gather его отвергнет")
        for block in ("oak_planks", "spruce_planks", "glass", "glass_pane", "bricks", "cobblestone", "stone_bricks",
                      "oak_door", "oak_fence", "white_bed", "crafting_table", "chest", "furnace", "torch",
                      "white_wool", "oak_slab", "oak_sign", "bookshelf", "white_concrete", "smooth_stone"):
            with self.subTest(block=block):
                self.assertNotIn(block, natural, f"блок {block!r} делает игрок, а не природа: он не должен попадать "
                                                 "в natural_blocks, иначе дух разберёт постройку игрока")

    def test_policy_MalformedNaturalBlocks_RejectedWithClearText(self):
        root = APP.parent / "TestResults" / "policy-tmp"
        root.mkdir(parents=True, exist_ok=True)
        for name, mutate in (("нет ключа", lambda d: d.pop("natural_blocks")),
                             ("пустая группа", lambda d: d.update(natural_blocks={"руды": []})),
                             ("не список", lambda d: d.update(natural_blocks={"руды": "coal_ore"})),
                             ("с префиксом minecraft:", lambda d: d.update(natural_blocks={"руды": ["minecraft:coal_ore"]})),
                             ("пустая строка", lambda d: d.update(natural_blocks={"руды": [""]}))):
            with self.subTest(case=name):
                data = json.loads((APP / "policy.json").read_text(encoding="utf-8"))
                mutate(data)
                path = root / f"policy-{abs(hash(name))}.json"
                path.write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")
                with self.assertRaises(ValueError, msg=f"policy.json без списка природных блоков ({name}) должен "
                                                      "давать ошибку с именем файла, а не молчаливый пустой список") as ctx:
                    load_policy(path)
                self.assertIn("natural_blocks", str(ctx.exception))

    def test_otherScripts_BlockParamNotCheckedAgainstObservation(self):
        self.check(script("mine_vein", ore="diamond", count=1))

    def test_mineVein_WithoutPickaxeInInventory_RejectedNamingRequiredTier(self):
        context = PlanContext((0, 64, 0), frozenset({"stone", "iron_ore"}), frozenset())
        with self.assertRaises(PlanError) as ctx:
            self.check(script("mine_vein", ore="iron"), context=context)
        text = str(ctx.exception)
        self.assertIn("plan", text)
        self.assertIn("кирк", text.lower())
        self.assertIn("каменн", text.lower(), "железная руда требует каменной кирки")

    def test_mineVein_ToolBelowOreTier_Rejected(self):
        context = PlanContext((0, 64, 0), frozenset({"diamond_ore"}), frozenset({"stone_pickaxe"}))
        with self.assertRaises(PlanError) as ctx:
            self.check(script("mine_vein", ore="diamond"), context=context)
        text = str(ctx.exception)
        self.assertIn("железн", text.lower(), "алмазная руда требует железной кирки")
        self.assertIn("stone_pickaxe", text)

    def test_mineVeinAndGatherStone_WithSufficientPickaxe_Accepted(self):
        context = PlanContext((0, 64, 0), frozenset({"stone", "iron_ore", "diamond_ore"}),
                              frozenset({"stone_pickaxe"}))
        for plan in (script("mine_vein", ore="iron"), script("mine_vein", ore="coal"),
                     script("gather", block="stone", count=2)):
            with self.subTest(plan=plan):
                self.check(plan, context=context)

    def test_gather_StoneWithoutPickaxe_Rejected(self):
        context = PlanContext((0, 64, 0), frozenset({"stone"}), frozenset())
        with self.assertRaises(PlanError) as ctx:
            self.check(script("gather", block="stone", count=4), context=context)
        self.assertIn("кирк", str(ctx.exception).lower())

    def test_gather_BlockNotMinedWithPickaxe_NoToolRequirement(self):
        context = PlanContext((0, 64, 0), frozenset({"oak_log", "sand", "dirt"}), frozenset())
        for block in ("oak_log", "sand", "dirt"):
            with self.subTest(block=block):
                self.check(script("gather", block=block, count=4), context=context)

    def test_pickaxeCheck_InventoryUnknown_AcceptedWithWarningNotRejected(self):
        context = PlanContext((0, 64, 0), frozenset({"stone", "iron_ore"}), None)
        warnings = self.check(script("mine_vein", ore="iron"), context=context)
        self.assertEqual(1, len(warnings), "без списка предметов в наблюдении проверка кирки даёт предупреждение, а не отказ")
        self.assertIn("предупрежд", warnings[0].lower())

    def test_contextFromObservation_ReadsInventoryOrLeavesUnknown(self):
        observation = {"pos": [1, 64, 2], "blocks": [], "inventory": [{"item": "minecraft:iron_pickaxe", "count": 1}, "мусор"]}
        self.assertEqual(frozenset({"iron_pickaxe"}), context_from_observation(observation).inventory)
        self.assertIsNone(context_from_observation({"pos": [1, 64, 2]}).inventory,
                          "нет ключа inventory — наличие кирки непроверяемо (повод для предупреждения)")

    def test_contextFromObservation_ReadsHeldItemFromNewField(self):
        observation = {"pos": [1, 64, 2], "blocks": [], "inventory": [],
                       "held": {"item": "minecraft:stone_pickaxe", "durability": 120}}
        self.assertEqual(frozenset({"stone_pickaxe"}), context_from_observation(observation).inventory,
                         "предмет в руке (новое поле held) обязан попадать в инвентарь проверки кирки")

    def test_contextFromObservation_HeldPickaxeWithoutInventoryKey_KnowsInventory(self):
        observation = {"pos": [1, 64, 2], "held": {"item": "minecraft:iron_pickaxe"}}
        self.assertEqual(frozenset({"iron_pickaxe"}), context_from_observation(observation).inventory,
                         "held без inventory обязан снимать неизвестность: кирка в руке видна проверке")

    def test_gather_StoneWithHeldPickaxeEmptyInventory_Accepted(self):
        observation = {"pos": [0, 64, 0], "blocks": [{"item": "minecraft:stone", "count": 4}],
                       "inventory": [], "held": {"item": "minecraft:iron_pickaxe"}}
        self.check(script("gather", block="stone", count=1),
                   context=context_from_observation(observation))

    def test_contextFromObservation_ReadsPositionAndNormalisedBlocks(self):
        observation = {"pos": [10, 64, -3], "blocks": [{"item": "minecraft:stone", "count": 5}, {"item": "dirt", "count": 1}, "мусор"]}
        context = context_from_observation(observation)
        self.assertEqual((10, 64, -3), context.position)
        self.assertEqual(frozenset({"stone", "dirt"}), context.observed_blocks)
        self.assertIsNone(context_from_observation({"pos": "x"}).position)
        self.assertEqual(frozenset(), context_from_observation({}).observed_blocks)

    def test_property_GotoVerdictMatchesHypotAcrossRandomPoints(self):
        rng = random.Random(4)
        for _ in range(500):
            dx, dz = rng.randint(-400, 400), rng.randint(-400, 400)
            plan = goto(100 + dx, -50 + dz, y=rng.randint(-64, 319))
            far = (dx * dx + dz * dz) ** 0.5 > 256
            try:
                self.check(plan)
                accepted = True
            except PlanError:
                accepted = False
            self.assertEqual(not far, accepted, f"dx={dx}, dz={dz}: проверка расстояния расходится с расчётом")


class ItemToolScriptRuleTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.catalog = Catalog.from_capabilities(default_capabilities())
        cls.policy = load_policy(APP / "policy.json")
        cls.limits = PlanLimits()

    def check(self, plan, context=None):
        return check_plan(plan, self.catalog, self.policy, self.limits, context)

    def test_emptyInventory_AcquirePickaxeAccepted_MineVeinRejected(self):
        context = PlanContext((0, 64, 0), frozenset({"stone", "iron_ore"}), frozenset())
        self.check(script("acquire", item="wooden_pickaxe", count=1), context)
        with self.assertRaises(PlanError, msg="пустой инвентарь: mine_vein без кирки обязан отклониться") as ctx:
            self.check(script("mine_vein", ore="iron"), context)
        self.assertIn("кирк", str(ctx.exception).lower())

    def test_pickaxeInInventory_MineVeinAccepted(self):
        context = PlanContext((0, 64, 0), frozenset({"stone", "iron_ore"}), frozenset({"stone_pickaxe"}))
        self.check(script("mine_vein", ore="iron"), context)

    def test_toolInInventoryNotInHand_EquipAccepted(self):
        context = PlanContext((0, 64, 0), frozenset({"stone"}), frozenset({"iron_pickaxe"}))
        self.check(script("equip", item="iron_pickaxe"), context)
        self.check(script("equip", item="*_pickaxe"), context)

    def test_containerNearby_WithdrawAccepted(self):
        self.check(script("withdraw", item="iron_pickaxe", count=1))
        self.check(script("withdraw", container="nearest", item="iron_pickaxe", count=1))

    def test_equipWildcard_OnlyEquipAllowsIt(self):
        for name in ("acquire", "craft", "withdraw"):
            with self.subTest(script=name):
                with self.assertRaises(PlanError, msg=f"{name}.item не может быть шаблоном: нужен конкретный предмет") as ctx:
                    self.check(script(name, item="*_pickaxe", count=1))
                self.assertIn("plan.params.item", str(ctx.exception))

    def test_itemIds_ValidAccepted(self):
        for item in ("wooden_pickaxe", "minecraft:oak_log", "oak_log", "minecraft:iron_sword", "stone_pickaxe"):
            with self.subTest(item=item):
                self.check(script("acquire", item=item, count=1))

    def test_itemIds_InvalidRejectedNamingNode(self):
        for item in ("", " ", "Wooden_Pickaxe", "wooden pickaxe", ":oak_log", "minecraft:", "a:b:c", "кирка", "oak_log@"):
            with self.subTest(item=item):
                with self.assertRaises(PlanError, msg=f"id предмета {item!r} обязан отклониться") as ctx:
                    self.check(script("acquire", item=item, count=1))
                self.assertIn("plan.params.item", str(ctx.exception))
                self.assertTrue(any("а" <= ch.lower() <= "я" for ch in str(ctx.exception)), "текст ошибки по-русски")


def random_json(rng, depth=0):
    kinds = ["str", "int", "float", "bool", "none", "list", "dict"] if depth < 24 else ["str", "int", "none"]
    kind = rng.choice(kinds)
    if kind == "str":
        return rng.choice(["scan", "goto", "script", "sequence", "x", "", "минус", "any", "iron", "*_log"])
    if kind == "int":
        return rng.choice([0, 1, -1, 5, 64, 65, 6000, 6001, 10 ** 12, -10 ** 12])
    if kind == "float":
        return rng.choice([0.5, 1.0, float("nan"), float("inf"), -0.0, 1e300])
    if kind == "bool":
        return rng.choice([True, False])
    if kind == "none":
        return None
    if kind == "list":
        return [random_json(rng, depth + 1) for _ in range(rng.randint(0, 4))]
    keys = ["type", "script", "params", "steps", "times", "step", "mode", "check", "then", "else", "count", "ticks", "x"]
    return {rng.choice(keys): random_json(rng, depth + 1) for _ in range(rng.randint(0, 5))}


def random_planish(rng, depth=0):
    kind = rng.choice(["script", "sequence", "repeat", "fallback", "parallel", "garbage"])
    if depth > 30:
        return script("scan")
    if kind == "script":
        return {"type": "script", "script": rng.choice(["scan", "goto", "wait", "chop_tree", "nope"]),
                "params": {rng.choice(["x", "ticks", "count", "radius", "foo"]): random_json(rng, 20)}}
    if kind in ("sequence", "fallback"):
        return {"type": kind, "steps": [random_planish(rng, depth + 1) for _ in range(rng.randint(0, 3))]}
    if kind == "parallel":
        return {"type": "parallel", "mode": rng.choice(["all", "any", 5]),
                "steps": [random_planish(rng, depth + 1) for _ in range(rng.randint(0, 3))]}
    if kind == "repeat":
        return {"type": "repeat", "times": rng.choice([0, 1, 3, 64, 65, True, "2"]), "step": random_planish(rng, depth + 1)}
    return random_json(rng)


class PlanCheckPropertyTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.catalog = Catalog.from_capabilities(default_capabilities())
        cls.policy = load_policy(APP / "policy.json")
        cls.limits = PlanLimits()

    def test_property_RandomJsonTrees_NeverRaiseAnythingButPlanError(self):
        rng = random.Random(22)
        for i in range(1000):
            plan = random_json(rng) if i % 2 else random_planish(rng)
            try:
                check_plan(plan, self.catalog, self.policy, self.limits)
            except PlanError:
                pass
            except Exception as error:
                self.fail(f"итерация {i}: check_plan бросил {type(error).__name__}: {error} на плане {str(plan)[:200]}. "
                          "Любой мусор должен давать PlanError с текстом для модели")

    def test_property_TypeMutationOfValidPlan_AlwaysRejected(self):
        mutations = [None, True, {"x": 1}, [1, 2], "строка-вместо-значения", 3.5]
        checked = 0
        for name, plan in VALID:
            for path, container, key in param_slots(plan):
                for mutated in mutations:
                    if type_fits(container[key], mutated):
                        continue
                    broken = copy.deepcopy(plan)
                    slot_container = resolve(broken, path)
                    slot_container[key] = mutated
                    checked += 1
                    with self.assertRaises(PlanError, msg=f"«{name}»: параметр {path}.{key} заменён на {mutated!r} — "
                                                          "проверка обязана отвергнуть другой тип"):
                        check_plan(broken, self.catalog, self.policy, self.limits)
        self.assertGreater(checked, 40, "мутаций должно быть достаточно, иначе свойство проверяет пустоту")


def param_slots(node, path=()):
    if not isinstance(node, dict):
        return
    if node.get("type") == "script":
        for key in node.get("params", {}):
            yield path, node["params"], key
    for field in ("steps",):
        for i, child in enumerate(node.get(field, [])):
            yield from param_slots(child, path + ((field, i),))
    if "step" in node:
        yield from param_slots(node["step"], path + (("step", None),))


def resolve(plan, path):
    node = plan
    for field, index in path:
        node = node[field] if index is None else node[field][index]
    return node["params"]


def type_fits(original, mutated):
    if isinstance(original, bool):
        return isinstance(mutated, bool)
    if isinstance(original, int):
        return isinstance(mutated, int) and not isinstance(mutated, bool)
    if isinstance(original, str):
        return isinstance(mutated, str)
    if isinstance(original, list):
        return isinstance(mutated, list) and all(isinstance(i, str) for i in mutated)
    return False


if __name__ == "__main__":
    unittest.main()
