import json
import random
import string
import unittest
from pathlib import Path

from parse import MAX_REASON, Decision, ReplyError, parse_reply

APP = Path(__file__).resolve().parent.parent
PLAN = {"type": "script", "script": "chop_tree", "params": {"count": 3}}
GOOD = json.dumps({"reason": "Рядом лес.", "goal": "срубить деревья", "plan": PLAN}, ensure_ascii=False)
SKIP = json.dumps({"reason": "Нечего делать.", "skip": True}, ensure_ascii=False)


def reply(**fields):
    base = {"reason": "причина", "goal": "цель", "plan": PLAN}
    base.update(fields)
    return json.dumps({k: v for k, v in base.items() if v is not ...}, ensure_ascii=False)


ACCEPTED = [
    ("чистый JSON", GOOD, ("Рядом лес.", "срубить деревья", PLAN, False)),
    ("ограждение ```json", f"```json\n{GOOD}\n```", ("Рядом лес.", "срубить деревья", PLAN, False)),
    ("ограждение без языка", f"```\n{GOOD}\n```", ("Рядом лес.", "срубить деревья", PLAN, False)),
    ("проза до и после", f"Вот мой ответ:\n{GOOD}\nНадеюсь, это поможет.", ("Рядом лес.", "срубить деревья", PLAN, False)),
    ("два объекта — первый", f"{GOOD}\n{SKIP}", ("Рядом лес.", "срубить деревья", PLAN, False)),
    ("skip", SKIP, ("Нечего делать.", None, None, True)),
    ("skip с прозой", f"Подумав, решил так: {SKIP} Конец.", ("Нечего делать.", None, None, True)),
    ("юникод и эмодзи", reply(reason="Тут есть 🌲 и «ёлки»", goal="рубка"), ("Тут есть 🌲 и «ёлки»", "рубка", PLAN, False)),
    ("юникод-экранирование", '{"reason": "\\u043f\\u0440\\u0438\\u0432\\u0435\\u0442", "goal": "г", "plan": ' + json.dumps(PLAN) + '}',
     ("привет", "г", PLAN, False)),
    ("скобки внутри строки", reply(reason="закрывающая } и открывающая { внутри"),
     ("закрывающая } и открывающая { внутри", "цель", PLAN, False)),
    ("не-JSON фигурные скобки до ответа", "Заметка {это не json}. " + GOOD, ("Рядом лес.", "срубить деревья", PLAN, False)),
    ("reason ровно предел", reply(reason="р" * MAX_REASON), ("р" * MAX_REASON, "цель", PLAN, False)),
    ("goal ровно 200", reply(goal="ц" * 200), ("причина", "ц" * 200, PLAN, False)),
    ("лишнее поле игнорируется", reply(confidence=0.9), ("причина", "цель", PLAN, False)),
    ("skip с goal — goal отбрасывается", json.dumps({"reason": "р", "skip": True, "goal": "x"}), ("р", None, None, True)),
]

REJECTED = [
    ("пустой ответ", "", "пуст"),
    ("только пробелы", "  \n\t ", "пуст"),
    ("проза без JSON", "Я думаю, надо срубить дерево.", "JSON"),
    ("JSON-массив", json.dumps([PLAN]), "JSON"),
    ("обрезанный JSON", GOOD[:-15], "обрезан"),
    ("нет plan и skip", reply(plan=...), "plan"),
    ("и plan, и skip", reply(skip=True), "одновременно"),
    ("нет reason", reply(reason=...), "reason"),
    ("reason не строка", reply(reason=5), "reason"),
    ("reason пустой", reply(reason=""), "reason"),
    ("reason на символ больше предела", reply(reason="р" * (MAX_REASON + 1)), str(MAX_REASON)),
    ("нет goal при plan", reply(goal=...), "goal"),
    ("goal 201 символ", reply(goal="ц" * 201), "200"),
    ("plan не объект", reply(plan=[1, 2]), "plan"),
    ("skip false без plan", reply(plan=..., skip=False), "skip"),
    ("skip строкой", reply(plan=..., skip="true"), "skip"),
    ("NaN в JSON", '{"reason": "р", "goal": "г", "plan": {"type": "script", "script": "wait", "params": {"ticks": NaN}}}', "JSON"),
    ("висячая запятая", '{"reason": "р", "goal": "г", "plan": {"type": "script", "script": "scan",},}', "JSON"),
    ("только число", "42", "JSON"),
    ("ограждение без закрытия и обрыв", "```json\n" + GOOD[:20], "обрезан"),
]


class ParseTests(unittest.TestCase):
    def test_reasonLimit_RaisedTo500ForReasonableAnswers(self):
        self.assertEqual(500, MAX_REASON,
                         "лимит reason поднят до 500: прежние 300 символов рубили разумные ответы модели (журнал dry 17.10)")

    def test_accepted_Table(self):
        self.assertGreaterEqual(len(ACCEPTED) + len(REJECTED), 20, "в таблице не меньше 20 ответов")
        for name, text, (reason, goal, plan, skip) in ACCEPTED:
            with self.subTest(reply=name):
                decision = parse_reply(text)
                self.assertIsInstance(decision, Decision)
                self.assertEqual((reason, goal, plan, skip), (decision.reason, decision.goal, decision.plan, decision.skip),
                                 f"ответ «{name}» разобран неверно")

    def test_rejected_Table_RaisesReplyErrorWithTextForModel(self):
        for name, text, fragment in REJECTED:
            with self.subTest(reply=name):
                with self.assertRaises(ReplyError, msg=f"ответ «{name}» должен отвергаться") as ctx:
                    parse_reply(text)
                message = str(ctx.exception)
                self.assertIn(fragment.lower(), message.lower(), f"сообщение «{message}» должно называть «{fragment}»")
                self.assertTrue(any("а" <= ch.lower() <= "я" for ch in message), "текст для модели по-русски")
                self.assertIn("JSON", message, "в тексте повтора напомнен формат ответа")

    def test_parse_NonString_RaisesReplyError(self):
        for value in (None, 5, b"{}"):
            with self.subTest(value=value):
                with self.assertRaises(ReplyError):
                    parse_reply(value)

    def test_parse_HugeReply_RejectedWithoutHang(self):
        with self.assertRaises(ReplyError):
            parse_reply("{" * 200_000)
        with self.assertRaises(ReplyError, msg="даже валидный ответ длиннее предела отвергается: так ограничена память") as ctx:
            parse_reply(GOOD + " " * 70_000)
        self.assertIn("длинный", str(ctx.exception))

    def test_parse_DeeplyNestedJson_RaisesReplyErrorNotRecursionError(self):
        with self.assertRaises(ReplyError, msg="глубокий JSON не должен доходить до json.dumps журнала и стража") as ctx:
            parse_reply('{"reason": "р", "goal": "г", "plan": ' + '{"a": ' * 9000 + "1" + "}" * 9000 + "}")
        self.assertIn("глубже", str(ctx.exception))
        for levels in (5000, 12000):
            with self.assertRaises(ReplyError):
                parse_reply('{"a": ' * levels)

    def test_parse_NestingAtLimit_Accepted(self):
        plan = {"type": "script", "script": "wait", "params": {"ticks": 1}}
        for _ in range(15):
            plan = {"type": "sequence", "steps": [plan]}
        self.assertTrue(parse_reply(json.dumps({"reason": "р", "goal": "г", "plan": plan})).plan)

    def test_examples_AllParseAsDecisions(self):
        for path in sorted((APP / "prompt" / "examples").glob("*.json")):
            with self.subTest(example=path.name):
                decision = parse_reply(path.read_text(encoding="utf-8"))
                self.assertTrue(decision.skip or decision.plan is not None)

    def test_property_AnyText_GivesDecisionOrReplyError(self):
        rng = random.Random(5)
        alphabet = '{}[]":,\\ \n`' + string.ascii_letters + "0123456789" + "рсюя🌲"
        fragments = ['"reason"', '"goal"', '"plan"', '"skip"', "true", "null", "{", "}", "[", "]", ":", ",", "```json"]
        for i in range(1000):
            if i % 3 == 0:
                text = "".join(rng.choice(alphabet) for _ in range(rng.randint(0, 200)))
            elif i % 3 == 1:
                text = " ".join(rng.choice(fragments) for _ in range(rng.randint(0, 30)))
            else:
                text = GOOD[:rng.randint(0, len(GOOD))] + rng.choice(["", "}", "]}", "\n```"])
            try:
                result = parse_reply(text)
            except ReplyError:
                continue
            except Exception as error:
                self.fail(f"итерация {i}: parse_reply бросил {type(error).__name__} на {text[:80]!r}: "
                          "любой ответ обязан дать Decision или ReplyError")
            self.assertIsInstance(result, Decision, f"итерация {i}: результат не Decision")
            self.assertTrue(result.skip or isinstance(result.plan, dict), "Decision всегда несёт plan или skip")


if __name__ == "__main__":
    unittest.main()
