# KSP Auto Repair v0.2.3 hotfix

This package fixes the failures observed in the 2026-08-15 live Bank Organizer test.

## Fixed

1. **Incident capture crashed off the RuneLite Client thread**
   - `captureEnvironment()` reached `client.getItemContainer(...)` from `ksp-auto-repair-v2-worker`.
   - v0.2.3 supplies the coordinator with a thread-aware `Client` proxy.
   - Background Client API calls are synchronously marshalled through `ClientThread.invoke(...)`.
   - Git, Codex, compilation and other expensive work remain on the repair worker.

2. **Unrelated external plugin errors could be attributed to the active KSP plugin**
   - The observed `NpcAggroAreaPlugin` NullPointerException was incorrectly followed by an Auto Repair incident targeting KSP Bank Organizer.
   - The included coordinator patch now requires the current/recent log context to actually identify the target KSP plugin before a log-derived incident can auto-repair it.
   - Action/state-derived incidents are not affected.

3. **Runtime log label**
   - Hard-coded `KSP Auto Repair v0.2` coordinator messages are changed to `v0.2.3` by the patcher.

## Install

From the extracted package:

```powershell
.\install_v0.2.3_hotfix.ps1 -RepoRoot C:\Users\Proje\Documents\GitHub\Microbot-Hub
```

Then:

```powershell
cd C:\Users\Proje\Documents\GitHub\Microbot-Hub
./gradlew build -PpluginList=KspAutoRepairPlugin
```

## Codex settings

Use:

```text
Agent backend: Codex / ChatGPT
Codex executable: C:\Users\Proje\AppData\Roaming\npm\codex.cmd
Codex model: [blank]
Full auto repair: ON
Use loader compiler: ON
Refresh loader after repair: ON
Automatic rollback: ON
Push validated fixes: ON
```

## Expected next live test

Reproduce the Bank Organizer failures:

- `KSP Bank Organizer stopped: Could not open the nearest bank.`
- `KSP Bank Organizer stopped: Iron axe: Move to tab 4 was not verified.`

With v0.2.3 the observer should create an incident bundle and proceed into Codex instead of crashing in `captureEnvironment()`.
