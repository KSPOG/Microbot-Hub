# KSP Pet Names v0.1.0

Microbot/RuneLite plugin that lets the player assign a custom nickname to the **active account's follower/pet** from a sidebar and renders that nickname above the pet model.

## Important behavior

- Uses `Client#getFollower()` so it targets the local player's actual follower rather than a nearby NPC guess.
- Pet nicknames are stored with `ConfigManager#setRSProfileConfiguration(...)`.
- Loading uses `ConfigManager#getRSProfileConfiguration(...)`.
- Clearing uses `ConfigManager#unsetRSProfileConfiguration(...)` when the account has no saved pet names left.
- Therefore names are **RuneScape-profile/account bound**.
- Switching RuneScape profiles triggers a sidebar refresh through `RuneScapeProfileChanged`.
- A different account can assign a different nickname to the same OSRS pet.
- The overlay only renders for the local account's follower.

## Usage

1. Enable **KSP Pet Names**.
2. Open the KSP Pet Names sidebar.
3. Summon the pet you want to name.
4. Enter a custom name.
5. Press **Save Name**.
6. The custom name is drawn above the pet while it is your active follower.

The sidebar also lists saved pet names for the currently active RuneScape profile and allows deleting them.

## Source-loader / Microbot-Hub layout

Keep the `ksppetnames` directory as one plugin source folder:

```text
ksppetnames/
  KspPetNamesPlugin.java
  KspPetNamesService.java
  KspPetNamesOverlay.java
  KspPetNamesPanel.java
```

Package:

```text
net.runelite.client.plugins.microbot.ksppetnames
```
