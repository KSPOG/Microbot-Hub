# KSP Auto Repair Agent v0.2.2

KSP Auto Repair Agent is a continuous self-healing development bridge for KSP Microbot plugins. Version 0.2 observes **intent, action and postcondition**, not only player movement or log errors.

## Codex / ChatGPT backend

`Agent backend` now includes **Codex / ChatGPT** as a first-class option. No Custom command is required.

Default Codex settings:

- Codex executable: `codex`
- Codex model: blank, which preserves the configured/default Codex model
- Approval policy: `never`
- Sandbox: `workspace-write`
- Session: ephemeral
- Generated repair prompt: supplied through stdin

Current Codex CLI builds treat `-a/--ask-for-approval` as a top-level option, so the generated invocation is ordered like:

`codex -a never exec -C <repo> --sandbox workspace-write --skip-git-repo-check --ephemeral --color never -`

The repair bridge still owns Git commits/pushes, compiler validation, Source Loader refresh, runtime validation and rollback.

## Reliability model

The observer combines four evidence layers:

1. **RuneLite events** — menu actions, widgets, item containers, varbits, stats, animations, chat, NPC/object lifecycle and game-state changes.
2. **Runtime snapshots** — position, animation, interaction target, inventory/equipment hashes, XP, `Microbot.status`, active KSP plugins and reflected state-machine fields.
3. **Action journal** — inferred menu actions plus optional explicit `KspObservation.action(...)` instrumentation, with a baseline, expected postcondition and action-specific deadline.
4. **State-machine history** — reflected fields whose names look like state/phase/step/stage/task/mode, including script objects, with repeated-sequence and no-progress detection.

A low-confidence observation is recorded but does not automatically rewrite source. The default autonomous repair threshold is 85%.

## Failure detection

v0.2 detects and classifies failed action postconditions, repeated state-machine loops, state stalls, global no-progress stalls, client-thread violations, null-pointer failures, API incompatibilities, widget/dialogue/banking/pathing failures, source-loader/compilation failures, Git authentication failures, network failures, and coding-agent authentication/executable failures.

Infrastructure failures are captured but are not treated as source-code bugs.

## Incident replay bundle

Each incident is stored under:

`%USERPROFILE%\.runelite\ksp-agent-repair\incidents\<incident-id>\`

The bundle can contain `incident.json`, `events.jsonl`, `actions.jsonl`, `states.jsonl`, `environment.json`, `client-tail.log`, `screenshot.png`, `source-revision.txt`, `repair-history-match.json`, `compile-validation.txt`, agent prompts/outputs, changed-files data, repair status, and Git logs.

## Source Loader integration

KSP Source Loader is a separately installed runtime plugin, so v0.2 does not hard-link against its classes. `KspSourceLoaderBridge` discovers lifecycle/listener callbacks, revision getters/fields, immediate refresh methods, candidate validation methods, and `InMemoryJavaCompiler` when safely visible. When direct hooks are unavailable, the observer retains the revision/client-log fallback.

## Candidate staging and validation

The coding agent does not commit or push directly. The bridge resets a managed candidate worktree, constrains changes to the failing plugin's Java directory, rejects protected paths and non-Java edits, performs `git diff --check`, preflights compilation, commits/pushes only passing candidates, requests Source Loader refresh, observes the loaded revision, and automatically rolls back failed runtime validation when configured.

## Default settings

- Full auto repair: **on**
- Global stall threshold: **120 s**
- State stall threshold: **45 s**
- Loop repetitions: **3**
- Incident cooldown: **180 s**
- Auto-repair confidence: **85%**
- Post-fix validation: **60 s**
- Required post-fix semantic progress events: **2**
- Agent attempts: **3**
- Scene radius: **20 tiles**
- Timeline capacity: **1200 events**
- Prefer Source Loader compiler: **on**
- Request loader refresh: **on**
- Automatic rollback: **on**
- Capture screenshots: **on**
- Push validated fixes: **on**

## Build

```powershell
./gradlew build -PpluginList=KspAutoRepairPlugin
```
