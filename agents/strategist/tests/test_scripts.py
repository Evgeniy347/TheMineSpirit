import re
import unittest
from pathlib import Path

REPO = Path(__file__).resolve().parents[3]
SCRIPTS = (REPO / "scripts" / "strategist.sh", REPO / "scripts" / "testing" / "check-strategist.sh")
BYTE_CUT = re.compile(r"\bcut\s+-[cb]")


class ScriptsTests(unittest.TestCase):
    def test_scripts_DoNotCutUtf8ByBytesOrColumns(self):
        for path in SCRIPTS:
            text = path.read_text(encoding="utf-8")
            self.assertIsNone(BYTE_CUT.search(text), f"{path.name}: cut -c/-b режет русский текст посреди символа; "
                              "используйте функцию chars (python) или ${var:0:N} в UTF-8 локали")

    def test_byteCutGuard_FindsViolationInSample(self):
        self.assertIsNotNone(BYTE_CUT.search("echo текст | cut -c1-80"))
        self.assertIsNotNone(BYTE_CUT.search("x | cut -b1-5"))
        self.assertIsNone(BYTE_CUT.search("x | cut -d: -f1"))

    def test_checkScript_HasSafeTruncationHelper(self):
        text = SCRIPTS[1].read_text(encoding="utf-8")
        self.assertIn("chars()", text)
        self.assertIn("| chars 80", text, "обрезка подробностей once идёт через chars")


if __name__ == "__main__":
    unittest.main()
