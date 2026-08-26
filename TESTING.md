# Runtime validation

TimeLens is code-complete and unit tested, but unit tests cannot prove the thing it actually
promises: that a historical view exists only on one player's screen. That needs two accounts and
a real server.

Every bug found in TimeLens so far — CoreProtect mutating the action list it is handed, the view
radius being far smaller than real builds, doors rendering without their upper half — got past
both unit tests and code review, and was caught only by running the plugin. Treat this checklist
as part of the work, not as a formality afterwards.

Run all of it before tagging a release.

## Setup

- Paper 1.21.11, CoreProtect 24.0+ (the release jar, **not** the Maven artifact — that one is a
  dev build and refuses to enable)
- Two accounts, A and B
- CoreProtect must already hold history for the period you test

## 1. Two-player isolation

The single most important test. If this fails, nothing else matters.

- [ ] A runs `/timelens 7d`
- [ ] B stands next to A and sees **absolutely no change** — the real, current world
- [ ] B can break and place blocks normally while A is viewing
- [ ] A exits and sees the world as it is now, **including B's changes**

## 2. Live mutation while viewing — the biggest open question

Exit must restore what is really there now, not a cached snapshot from when the view opened.
But there is a second, sharper question hiding in the same scenario.

**The concern.** When B changes a block, the server sends a block update to everyone tracking
that chunk — including A. That update carries the *real* block. If it lands on a coordinate
TimeLens is faking for A, it will overwrite the fake state and A's view silently develops holes.
Nothing in TimeLens intercepts outgoing packets, so there is no mechanism preventing this.

Expect it to happen. The test is to confirm whether it does, and how visible it is.

- [ ] A opens a view where a coordinate shows **STONE** historically but is **AIR** in reality
- [ ] B places dirt at exactly that coordinate
- [ ] **Does A's historical STONE survive, or does it become DIRT?**
- [ ] Repeat with B *breaking* a block that A sees as present
- [ ] A runs `/timelens exit` — A must see B's **current** state either way

If the fake state is overwritten, do not reach for packet interception. The cheaper options,
roughly in order of preference for an alpha:

1. Re-send the historical block for that one coordinate when a real change lands inside a live
   session — one packet, self-healing, no packet library.
2. End the view with an explanation when the viewed area materially changes.
3. Document it and leave it.

Decide only after seeing the behaviour.

## 2b. Chunk and state refresh

Related, and worth watching for throughout every other test:

- [ ] Does any ordinary server-side refresh — chunk reload, a neighbouring block update, light
      recalculation, a `/reload` — erase parts of A's snapshot?
- [ ] Does walking to the edge of view distance and back restore real blocks?

## 3. Async races

Each of these interrupts a request mid-flight. None may error, and none may render afterwards.

- [ ] `/timelens 30d 96`, then disconnect before it completes
- [ ] `/timelens 30d 96`, then teleport away before it completes
- [ ] `/timelens 30d 96`, then change world before it completes
- [ ] `/timelens 30d 96`, then `/timelens exit` while still loading
- [ ] `/timelens 7d` twice in quick succession — the second must be refused, not queued
- [ ] Console stays clean throughout

## 4. Real structures

Build, break and re-place each of these, then view across the change.

- [ ] Doors — both halves, not a floating bottom
- [ ] Beds — both halves
- [ ] Double chests
- [ ] Waterlogged blocks — stairs and slabs under water
- [ ] Signs — text may not survive; confirm it fails gracefully
- [ ] Stairs, trapdoors, fences, slabs — these carry a `half` or shape but are single blocks and
      must **not** gain a phantom partner
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

### Client and network cost — NOT yet measured

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

If A hitches badly at 12,000, the cap is too high — lower it, do not try to make the send
cheaper.

Measured on Paper 1.21.11 against a real CoreProtect database. Worst case is what matters — the
best figure flatters a warmed JIT, and Minecraft performance complaints come from spikes. At
roughly 1.2 microseconds per block at the worst observed rate, the 12,000-block render cap works
out to about 14 ms, under a third of a tick.

Record median and worst, not just best, whenever you re-run this.

- [ ] A request over `maximum-results` is **refused**, not rendered

### Benchmark invariants — a run that renders nothing is a FAILURE

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

- [ ] Walk the tested area — no block differs from what you and B actually built
- [ ] `/co lookup` in the tested area — no TimeLens entries, no rollbacks
