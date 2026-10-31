# KSP Auto Repair Agent v0.2.2 — Codex / ChatGPT backend

This replacement pack adds a first-class **Codex / ChatGPT** backend to the existing KSP Auto Repair Agent v0.2 reliability build.

## What changed

- `Agent backend` now includes **Codex / ChatGPT**.
- Added `Codex executable` (default: `codex`).
- Added optional `Codex model`; leave blank to use your current Codex/ChatGPT default.
- Codex is executed non-interactively with:
  - workspace root set to the managed repair repo
  - `workspace-write` sandbox
  - no approval pauses
  - ephemeral session
  - generated incident prompt supplied over stdin
- Claude Code and Custom command remain available.
- Plugin version bumped to **0.2.1**.
- The existing repair coordinator still owns guardrails, compilation, Git, Source Loader refresh, runtime validation, and rollback.

## One-time Codex setup

In PowerShell:

```powershell
codex --version
```

If that command is missing:

```powershell
npm install -g @openai/codex
```

Authenticate:

```powershell
codex login
```

Sign in with the same ChatGPT account used by the ChatGPT desktop app.

Then verify:

```powershell
codex login status
```

## Microbot configuration

Set:

```text
Agent backend: Codex / ChatGPT
Codex executable: codex
Codex model: [leave blank]
Full auto repair: ON
Automatic rollback: ON
Use loader compiler: ON
Refresh loader after repair: ON
Push validated fixes: ON
```

You do not need to enter a Custom command.

## Build

From the Microbot-Hub root:

```powershell
./gradlew build -PpluginList=KspAutoRepairPlugin
```

## Files in this pack

These files replace their counterparts under:

`src/main/java/net/runelite/client/plugins/microbot/kspautorepair/`

- `KspAgentBackend.java`
- `KspAutoRepairConfig.java`
- `KspAutoRepairPlugin.java`

The large v0.2 reliability coordinator and the other v0.2 support files remain unchanged.


## Important Codex CLI flag ordering

Current Codex CLI builds treat `-a/--ask-for-approval` as a top-level option.
The correct non-interactive form is:

```powershell
codex -a never exec --sandbox workspace-write --ephemeral "Reply with exactly KSP_CODEX_READY"
```

Do not place `-a never` after `exec`.
