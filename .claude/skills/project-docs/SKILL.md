---
name: project-docs
description: Navigate this repo's design documentation in docs/ — where the authoritative spec lives and how to search docs.
  Use it whenever you need to know what the product is supposed to do, why something is designed the way it is, what a mode or term means,
  what roadmap stage something belongs to, or before designing anything non-trivial in :core or :app.
  Also use it when adding or editing a doc under docs/.
---

# Project docs

`docs/` holds the design documentation. It is written **in Russian**. That matters more than it
sounds: an English `grep` over these files returns zero hits and reads like "the doc doesn't cover
that," when it usually does. Translate your search term before grepping.

Codegraph indexes code symbols, not prose, so `codegraph_*` tools find nothing in `docs/`. Use
`Grep` and `Read` here.

## The important docs

- **`Terms of Reference.md`** — the authoritative spec. Requirements, trust model, architecture
  overview, staged roadmap.
  When code and this doc disagree about intent, the doc wins: the code is early-stage and mostly
  unwritten.
  It's short enough to read end to end, and worth doing once if you're new to the project.

## Searching docs

Docs will be added over time and won't be named here. Approach any doc the same way:

1. **List what exists** — `ls docs/`. Filenames are descriptive; use them to judge relevance before
   opening anything.
2. **Get its shape** — `grep -n '^#\{1,4\} ' docs/<file>.md` returns a heading map with line
   numbers. Cheaper than reading the file, and it tells you where to `Read` with `offset`/`limit`.
3. **Grep Russian stems, not whole words.** Russian inflects heavily, so a full word matches only
   one of its forms: `grep -rn 'шифров' docs/` catches шифрование, шифруются, зашифрован,
   расшифровать, while `шифрование` alone misses most of them. Cut the ending off your search term.
4. **Read the surrounding section, not just the hit line.** These docs qualify their statements
   heavily — a requirement on one line often has an exception two lines below it. A grep hit tells
   you where to read, not what the doc says.

## Adding or editing docs

New design docs go in `docs/`. Match what's there: Russian, Markdown, numbered sections, a version
marker near the top.
