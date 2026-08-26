# Runtime validation

TimeLens is code-complete and unit tested, but unit tests cannot prove the thing it actually
promises, which is that a historical view exists only on one player's screen. That needs two
accounts and a real server.

Every defect found in TimeLens so far got past both unit tests and code review, and was caught
only by running the plugin. That includes CoreProtect mutating the action list it is handed, the
view radius being far smaller than real builds, and doors rendering without their upper half.
Treat this checklist as part of the work, not as a formality afterwards.

Run all of it before tagging a release.

## Result table

Fill this in as you go. Everything above the line is the alpha.1 gate.

| Test | Expected | Result |
|---|---|---|
| A enters historical view | A sees past | |
| B stands beside A | **B sees the present** | |
| B modifies unrelated block | B remains normal | |
| B modifies a coordinate A is faking | *observe what A receives* | |
| A exits | current real state restored | |
| ~1k blocks rendered | no meaningful hitch | |
| ~5k blocks rendered | record hitch / MSPT | |
| ~12k blocks rendered | record hitch / MSPT | |
| Door / bed | both halves visually correct | |
| Waterlogged block | block data correct | |
| Piston / redstone | no broken snapshot state | |
| Hard server stop | no persistent player changes | |
| Reconnect | player completely normal | |

For the client figures, precision does not matter. What matters is which of these it looks like:

    1k  -> imperceptible
    5k  -> tiny hitch
    12k -> noticeable but acceptable

versus `12k -> client freezes for two seconds`. If 12k is bad, lower the cap. Do not start an
optimisation project around a number chosen by hand.

**Do the isolation test first.** If A sees the past while B sees the present, the single most
important promise of TimeLens is proven, and everything else is detail.

## Setup

- Paper 1.21.11 and CoreProtect 24.0 or newer. Use the release jar, **not** the Maven artifact,
  which is a development build and refuses to enable.
- Two accounts, A and B
- CoreProtect must already hold history for the period you test

## 1. Two-player isolation

The single most important test. If this fails, nothing else matters.

- [ ] A runs `/timelens 7d`
- [ ] B stands next to A and sees **absolutely no change**, the real, current world
- [ ] B can break and place blocks normally while A is viewing
- [ ] A exits and sees the world as it is now, **including B's changes**

## 2. Live mutation while viewing, the biggest open question

Exit must restore what is really there now, not a cached snapshot from when the view opened.
But there is a second, sharper question hiding in the same scenario.

**The concern.** When B changes a block, the server sends a block update to everyone tracking
that chunk, including A. That update carries the *real* block. If it lands on a coordinate
TimeLens is faking for A, it will overwrite the fake state and A's view will silently develop
holes. Nothing in TimeLens intercepts outgoing packets, so there is no mechanism preventing this.

Expect it to happen. The test is to confirm whether it does, and how visible it is.

- [ ] A opens a view where a coordinate shows **STONE** historically but is **AIR** in reality
- [ ] B places dirt at exactly that coordinate
- [ ] **Does A's historical STONE survive, or does it become DIRT?**
- [ ] Repeat with B *breaking* a block that A sees as present
- [ ] A runs `/timelens exit`, A must see B's **current** state either way

If the fake state is overwritten, do not reach for packet interception, and do not reach for
self-healing either. Re-sending the one coordinate is easy; reliably detecting *every* way the
real world can change is not. Blocks move through player edits, pistons, explosions, fluids,
fire, physics, entity actions, other plugins, commands and world-editing tools. Chasing all of
them means building a second change-tracking system purely to maintain an illusion.

**Preferred policy for alpha.1: end the view and say why.**

    TimeLens › The viewed area changed in the present world.
    TimeLens › Historical view closed to prevent visual inconsistencies.

This is honest, bounded, and impossible to get subtly wrong. Self-healing sessions can come
later, once the update behaviour is properly understood. Implement only after the test shows what
actually happens.

## 2b. Chunk and state refresh

Related, and worth watching for throughout every other test:

- [ ] Does any ordinary server-side refresh erase parts of A's snapshot? Chunk reloads,
      neighbouring block updates, light recalculation and `/reload` are all worth trying.
- [ ] Does walking to the edge of view distance and back restore real blocks?

## 3. Async races

Each of these interrupts a request mid-flight. None may error, and none may render afterwards.

- [ ] `/timelens 30d 96`, then disconnect before it completes
- [ ] `/timelens 30d 96`, then teleport away before it completes
- [ ] `/timelens 30d 96`, then change world before it completes
- [ ] `/timelens 30d 96`, then `/timelens exit` while still loading
- [ ] `/timelens 7d` twice in quick succession. The second must be refused, not queued
- [ ] Console stays clean throughout

## 4. Real structures

Build, break and re-place each of these, then view across the change.

- [ ] Doors, showing both halves rather than a floating bottom
- [ ] Beds, showing both halves
- [ ] Double chests
- [ ] Waterlogged blocks, such as stairs and slabs under water
- [ ] Signs. The text may not survive, so confirm that it fails gracefully
- [ ] Stairs, trapdoors, fences and slabs. These carry a `half` or a shape but occupy one block
      each, so they must **not** gain a phantom partner
- [ ] Tall grass, sunflowers
- [ ] A block replaced several times within one second

## 5. Performance

Watch `/tps` and `/mspt` during both the lookup and the render.

Prepare cost, on the **main thread**, over 40 runs each. One tick is 50 ms.

| Radius | Blocks sent | best | median | p95 | worst |
|---|---|---|---|---|---|
| 16 | 977 | 0.74 ms | 1.35 ms | 2.35 ms | 2.37 ms |
| 48 | 1,336 | 0.49 ms | 0.66 ms | 1.77 ms | 2.62 ms |
| 96 | 2,396 | 1.07 ms | 1.77 ms | 2.61 ms | **2.76 ms** |

Lookup, which runs **off** the main thread: 31–40 ms for 2,500–4,100 events.

### Client and network cost, not yet measured

The figures above are server preparation only. `sendMultiBlockChange` is one call on the server,
but it becomes a packet per chunk section on the wire, and the client has to apply and re-light
every one. The 12,000-block cap bounds server work; it does **not** bound client cost.

Test at increasing sizes and watch **both** clients:

| Blocks sent | Server MSPT | A's client hitch | B's client |
|---|---|---|---|
| ~1,000 | | | |
| ~5,000 | | | |
| ~12,000 (the cap) | | | |

- [ ] A's client does not freeze or stutter noticeably at the cap
- [ ] B's client is completely unaffected at every size
- [ ] Server MSPT stays flat

If A hitches badly at 12,000, the cap is too high. Lower it rather than trying to make the send
cheaper.

Measured on Paper 1.21.11 against a real CoreProtect database. Worst case is what matters, since
the best figure flatters a warmed JIT and Minecraft performance complaints come from spikes. At
roughly 1.2 microseconds per block at the worst observed rate, the 12,000-block render cap works
out to about 14 ms, which is under a third of a tick.

Record median and worst, not just best, whenever you re-run this.

- [ ] A request over `maximum-results` is **refused**, not rendered

### Benchmark invariants, because a run that renders nothing is a failure

Two performance runs during development reported clean passes while measuring nothing at all.
With nobody online Paper unloads chunks immediately, TimeLens correctly skipped every position as
unloaded, and the harness cheerfully reported success. A green result that means "no code ran" is
more dangerous than a red one, because it looks like evidence.

Any benchmark of the render path must assert, and fail loudly if not:

    query events        > 0
    reconstructed positions > 0
    blocks to send      > 0
    a known historical coordinate is present in the output

Without those, a future Paper or CoreProtect change can quietly turn the performance test back
into a no-op and nobody will notice. A harness must also hold plugin chunk tickets, or every
position is skipped as unloaded.

The lookup runs off the main thread, so the risk is concentrated in the render: it reads world
state and sends packets on the server thread. A visible MSPT spike there means
`history.maximum-results` needs lowering.

## 6. Server interruption

- [ ] A opens a view
- [ ] Kill the server process **without** `/timelens exit`
- [ ] Restart, A rejoins
- [ ] A is completely normal: moves freely, sees the real world, no leftover blocks

TimeLens stores nothing about a player outside the session, and holds them in place by rewriting
movement events rather than altering speeds or gamemode, so there should be nothing to clean up.
This test exists to prove that stays true.

## Afterwards

Confirm the world itself was never touched:

- [ ] Walk the tested area, confirming no block differs from what you and B actually built
- [ ] `/co lookup` in the tested area, confirming no TimeLens entries and no rollbacks
