# Benchmark harness

A throwaway Paper plugin that drives TimeLens's pipeline directly, so the render path can be
measured without two Minecraft clients. Not part of the Maven build and not shipped.

## Why it exists

`HistoricalRenderer.prepare()` is where the main-thread cost lives, and it needs no `Player`.
Only the packet send does. This harness calls it with real CoreProtect data and reports the
distribution of its cost.

## Invariants

The harness **fails loudly** if a scenario meant to exercise rendering did no work:

    events > 0, reconstructed positions > 0, blocks to send > 0

This is not defensive padding. Two runs during development reported clean passes while measuring
nothing, because with nobody online Paper unloads chunks instantly and every position was
correctly skipped as unloaded. Without these assertions a future Paper or CoreProtect change can
turn the benchmark back into a no-op silently.

Two things a run must get right:

- **Hold plugin chunk tickets.** `world.getChunkAt` alone is not enough with no players online.
- **Pick an area where the past actually differs from the present.** The densest cluster of
  recorded changes is often water flow, which nets out to nothing. Target coordinates whose
  oldest change after the target time is a *removal*.

## Building

Compile against `paper-api`, the CoreProtect release jar, the built TimeLens jar and
`adventure-api`, then jar it with `res/plugin.yml`. Drop it in `plugins/`, start the server, read
the `HARNESS` lines, then delete it.

Edit `CX`/`CY`/`CZ` to point at a dense area of your own world first.
