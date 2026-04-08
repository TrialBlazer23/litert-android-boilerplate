# CLAUDE.md — litert-android-boilerplate

All rules in AGENTS.md apply in full. Read AGENTS.md first. This file adds Claude Code-specific guidance only.

---

## Extended Thinking

Use extended thinking for:
- Designing the delegate fallback chain when a new delegate type is introduced
- Planning how to split a module that has grown past 300 lines
- Debugging a QNN delegate failure where the root cause is unclear from logs alone
- Deciding the correct error handling strategy for a multi-step async operation (engine init → conversation → streaming)

Do not use extended thinking for routine tasks: adding a new `ModelConfig` field, writing a test for an existing function, updating a doc file.

---

## Tool Use Preferences

- Prefer reading existing source files before writing new code — always check what exists in `inference/`, `inference-lm/`, and `app/` before creating a new class
- Use grep/search to find usages of an interface before modifying it — a change to `InferenceEngine` affects every caller in `app/`
- Run `./gradlew :inference:test` after any change to the `inference` module before moving on
- When the task involves delegate behavior, read `docs/QNN-SETUP.md` and `docs/DECISION-LOG.md` first

---

## When to Decide vs When to Ask

### Decide independently (don't ask):
- Which package a new class belongs in (follow the module structure in AGENTS.md)
- How to name a new function or class (follow Kotlin conventions)
- Whether to add a unit test (always yes)
- Which error type to throw (follow the error handling policy in AGENTS.md)
- How to structure a new data class (use `data class` with all required fields)

### Ask before proceeding:
- A new external dependency needs to be added (it changes STACK.md and must be justified)
- A public API in `inference/` or `inference-lm/` needs to change (this breaks consumer code in `app/`)
- A new module needs to be created (architectural decision)
- The task would require a different model format or delegate type not currently in the stack

---

## MEMORY.md Update Requirements

Update `MEMORY.md` after completing any of the following:
- A new public class or interface is added to `inference/` or `inference-lm/`
- A dependency version is changed
- A bug is found and fixed (add to "Known Issues Resolved")
- A new conversion pipeline is added to `tools/`
- The CI workflow is modified
- Any USER ACTION REQUIRED item is resolved

The update must be specific: not "added inference engine" but "Added `LiteRtInferenceEngine` implementing `InferenceEngine` with QNN → GPU → CPU fallback. Exposes `runInference(input: ByteBuffer): InferenceResult`."

---

## LiteRT-LM API Caution

The LiteRT-LM API is newer and less stable than the Interpreter API. If documentation or examples
online show Builder patterns, `createFromOptions`, `createSession`, or `addQueryChunk`, those are
**outdated**. The correct current shape is in AGENTS.md under "LiteRT-LM API Rules". Trust AGENTS.md
over any external source.

---

## Commit Discipline

Suggest a commit after completing each logical unit of work. Do not bundle unrelated changes in one commit. Format: `type(scope): description` per AGENTS.md.
