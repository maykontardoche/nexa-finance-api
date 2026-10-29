#!/usr/bin/env python3
"""Confere os links relativos das docs vivas: todo [texto](caminho) precisa apontar para um
arquivo ou diretório que existe. Links externos (http, mailto) e só-âncora (#secao) são
ignorados; a âncora depois do caminho também é ignorada.

Uso: python scripts/check-doc-links.py    (sai com código 1 se houver link quebrado)
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
DOCS = [ROOT / "README.md", *sorted((ROOT / "docs").rglob("*.md"))]
LINK = re.compile(r"\[[^\]]*\]\(([^)\s]+)\)")
# Blocos de código podem conter colchetes e parênteses que não são link.
FENCE = re.compile(r"```.*?```", re.DOTALL)


def broken_links(doc: pathlib.Path):
    text = FENCE.sub("", doc.read_text(encoding="utf-8"))
    for target in LINK.findall(text):
        if target.startswith(("http://", "https://", "mailto:", "#")) or target.startswith("<"):
            continue
        path = target.split("#", 1)[0]
        if path and not (doc.parent / path).exists():
            yield target


def main() -> int:
    failures = [(doc.relative_to(ROOT), link) for doc in DOCS for link in broken_links(doc)]
    for doc, link in failures:
        print(f"QUEBRADO: {doc} -> {link}")
    print(f"{len(DOCS)} documentos verificados, {len(failures)} link(s) quebrado(s).")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
