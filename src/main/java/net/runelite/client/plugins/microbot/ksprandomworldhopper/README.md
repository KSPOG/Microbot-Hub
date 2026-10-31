# KSP Random World Hopper v1.0.0

A minimal Microbot plugin that automatically world-hops after a newly randomized delay.

## Behaviour

- `Minutes X`: lower delay bound.
- `Minutes Y`: upper delay bound.
- The plugin picks a random whole-minute delay inclusively between X and Y.
- A fresh random delay is generated after every successful world hop.
- `Use F2P Only`: selects F2P worlds.
- `Use P2P Only`: selects members worlds.
- If both filters are enabled or both are disabled, the plugin follows the account's current membership type.
- If `Minutes X > Minutes Y`, the plugin automatically swaps the effective minimum/maximum.
- Failed world selection/hops retry after 10 seconds without consuming a full random interval.
- The script will not attempt another hop while Microbot reports that a hop is already in progress.

## Source-loader folder

`ksprandomworldhopper/`

Files:
- `KspRandomWorldHopperPlugin.java`
- `KspRandomWorldHopperConfig.java`
- `KspRandomWorldHopperScript.java`
