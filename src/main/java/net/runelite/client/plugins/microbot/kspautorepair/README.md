# KSP Auto Repair Agent v0.2

KSP Auto Repair Agent is a continuous self-healing development bridge for KSP Microbot plugins. Version 0.2 observes **intent, action and postcondition**, not only player movement or log errors.

## Reliability model

The observer combines four evidence layers:

1. **RuneLite events** — menu actions, widgets, item containers, varbits, stats, animations, chat, NPC/object lifecycle and game-state changes.
2. **Runtime snapshots** — position, animation, interaction target, inventory/equipment hashes, XP, `Microbot.status`, active KSP plugins and reflected state-machine fields.
3. **Action journal** — inferred menu actions plus optional explicit `KspObservation.action(...)` instrumentation, with a baseline, expected postcondition and action-specific deadline.
4. **State-machine history** — reflected fields whose names look like state/phase/step/stage/task/mode, including script objects, with repeated-sequence and no-progress detection.

A low-confidence observation is recorded but does not automatically rewrite source. The default autonomous repair threshold is 85%.

## Failure detection

v0.2 detects and classifies:

- failed action postconditions
- repeated state-machine loops
- state stalls
- global no-progress stalls
- client-thread violations
- null-pointer failures
- Microbot API incompatibilities
- widget/dialogue/banking/pathing failures
- source-loader/compilation failures
- Git authentication failures
- network failures
- coding-agent authentication/executable failures

Infrastructure failures are captured but are not treated as source-code bugs.

## Per-action postconditions

Menu actions automatically receive an expected result and deadline. Examples:

- Walk -> position change
- Bank -> widget change
- Talk-to -> dialogue/widget transition
- Withdraw/Deposit -> inventory change
- Teleport -> position change
- Wield/Wear/Eat/Drink -> inventory/equipment change

Existing KSP plugins require no changes for inferred observation. Plugins can optionally provide stronger evidence:

```java
KspObservation.state("KspBankOrganizer", "OPEN_BANK");
KspObservation.action(
        "KspBankOrganizer",
        "OPEN_BANK",
        "Bank booth",
        KspObservation.Expectation.WIDGET_CHANGE,
        7000,
        java.util.Collections.emptyMap());
KspObservation.progress("KspBankOrganizer", "BANK_OPENED");
KspObservation.waitReason("KspBankOrganizer", "Waiting for movement", 5000,
        java.util.Collections.emptyMap());
```

Explicit postconditions receive higher diagnostic confidence than inferred menu actions.

## Semantic progress and loop detection

Movement by itself is not considered proof that the task is healthy. The observer also considers state transitions, item/equipment changes, XP/varbit/widget changes and completed action postconditions.

State histories are checked for repeating periods, so sequences such as:

`BANK -> TRAVEL -> INTERACT -> BANK -> TRAVEL -> INTERACT -> ...`

can become a deterministic `REPEATING_STATE` incident instead of appearing healthy because the player is moving.

## Incident replay bundle

Each incident is stored under:

`%USERPROFILE%\.runelite\ksp-agent-repair\incidents\<incident-id>\`

The bundle can contain:

- `incident.json`
- `events.jsonl`
- `actions.jsonl`
- `states.jsonl`
- `environment.json`
- `client-tail.log`
- `screenshot.png`
- `source-revision.txt`
- `repair-history-match.json`
- `compile-validation.txt`
- `agent-prompt-<n>.txt`
- `agent-output-<n>.log`
- `changed-files.txt`
- `repair-status.json`
- `git.log`

The environment snapshot includes inventory, equipment, destination/selection information, visible widgets and nearby cached NPC/game-object evidence.

## Repair memory

Outcomes are appended to:

`%USERPROFILE%\.runelite\ksp-agent-repair\repair-history.jsonl`

The next incident receives similar prior repairs and their outcomes. Successful repairs can be reused as evidence; failed or rolled-back approaches are explicitly shown to the coding agent so it does not blindly repeat them.

## Source Loader integration

KSP Source Loader is a separately installed runtime plugin, so v0.2 does not hard-link against its classes.

`KspSourceLoaderBridge` discovers loader capabilities at runtime:

- lifecycle/listener callbacks when the installed loader exposes them
- current revision getters/fields
- immediate refresh methods
- candidate validation methods
- `InMemoryJavaCompiler` when it is safely visible

When direct hooks are unavailable, the observer retains the revision/client-log fallback. This makes v0.2 compatible with the existing Source Loader while allowing newer loader builds to expose stronger direct APIs later.

## Candidate staging and validation

The coding agent does not commit or push directly.

1. The bridge resets a managed candidate worktree to the configured source branch.
2. The coding agent may edit only the failing plugin's `.java` directory.
3. Renames, non-Java changes and protected-path changes are rejected.
4. `git diff --check` must pass.
5. The exact Source Loader validation/compiler path is used when discoverable.
6. Otherwise the system Java compiler is used as a fallback preflight.
7. Only a passing candidate may be committed and pushed.
8. Source Loader is asked to refresh immediately when supported.
9. The repaired revision must be observed by the loader.
10. Runtime validation requires semantic progress during the configured validation window.
11. Loader rejection, recurrence of the failure, or insufficient post-fix progress can automatically revert and push a rollback.

## Repair watchdog

The repair engine has explicit phases:

`IDLE -> CAPTURING_INCIDENT -> SYNCING_SOURCE -> RUNNING_AGENT -> VALIDATING_CHANGES -> COMMITTING -> PUSHING -> WAITING_FOR_LOADER -> VALIDATING_RUNTIME`

Rollback uses `ROLLING_BACK`.

Each phase has a bounded deadline. Agent processes are forcibly terminated on timeout. A Source Loader wait first requests a refresh and then rolls back when the revision still cannot be validated and automatic rollback is enabled.

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

## Default source repository

`https://github.com/KSPOG/ksppluginsrelease.git`

Managed worktree:

`%USERPROFILE%\.runelite\ksp-agent-repair\worktree\ksppluginsrelease`

## Agent backends

### Claude Code

The built-in backend invokes the configured executable non-interactively and only grants source reading/editing tools. Git remains controlled by the repair bridge.

### Custom command

The custom command supports:

- `{repo}` — managed candidate repository path
- `{prompt}` — generated incident prompt file

The process edits source and exits. The bridge performs guardrails, compilation, source control, loader refresh, runtime validation and rollback.

## Build

```powershell
./gradlew build -PpluginList=KspAutoRepairPlugin
```

## Runtime prerequisites

Full autonomous repair requires:

- Git on `PATH` with push access to the configured KSP source repository.
- A coding-agent executable that can run without interactive prompts.
- KSP Source Loader enabled and watching the same repository/branch.

If one of those prerequisites is missing, the observer still records replayable incidents; infrastructure failures are not converted into speculative source patches.
