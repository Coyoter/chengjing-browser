"""Regenerate the source-only JVM fallback; Android XML remains the runtime authority."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
source = json.loads((ROOT / "localization/source-zh-TW.json").read_text())
def literal(value):
    return json.dumps(value, ensure_ascii=False).replace("$", "\\$")

text = "package tw.techtarian.browser\n\n/** Generated source templates for JVM validation. Runtime strings come from Android resources. */\ninternal object BrowserTextSource {\n    val values=mapOf(\n"
text += "".join("        R.string." + key + " to " + literal(value) + ",\n" for key, value in source.items())
text += "    )\n}\n"
(ROOT / "app/src/main/java/tw/techtarian/browser/BrowserTextSource.kt").write_text(text)
print("Updated source fallback:", len(source), "templates")
