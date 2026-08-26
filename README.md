# TimeLens

Explore your Minecraft world's past.

TimeLens is a Paper plugin that reconstructs how an area of your world looked at an earlier
point in time and shows it to a single player, using block history already recorded by
CoreProtect. Nothing in the world is altered — the past is drawn only on one player's screen.

```
/timelens 7d
```

## What is TimeLens?

TimeLens is a **visualisation layer, not a rollback tool**.

When a player asks to see seven days ago, TimeLens reads the block changes CoreProtect recorded
since then, works backwards to determine what each affected position held at that moment, and
sends those blocks to that player's client as a private overlay. The server continues to hold,
simulate and serve the real world exactly as before.

That distinction is the whole point of the project:

- the world on disk is never written to;
- CoreProtect's database is only ever read;
- other players online at the same time see the present, unchanged;
- the viewer's own client is returned to the present on exit.

## Features

- View any area as it looked at a point in the past — an age (`7d`) or a date (`2026-08-20 14:30`).
- Historical blocks are visible **only** to the player who asked for them.
- History queries run off the main server thread, so the server keeps ticking.
- Bounded by radius, lookback and result-count limits that a server owner controls, with a
  per-view radius a player can widen on demand.
- One active view and one in-flight query per player, so the database cannot be spammed.
- Views are cleaned up automatically on exit, disconnect, world change, teleport and death.

## Requirements

| | |
|---|---|
| Server | Paper 1.21.11 (or a fork of it) |
| Java | 21 or newer |
| Dependency | [CoreProtect](https://modrinth.com/plugin/coreprotect) 24.0+ with API v12 |

CoreProtect is a **hard dependency**. TimeLens will not enable without it, because CoreProtect
is the only source of history in v0.1.0.

CoreProtect must also have its API enabled — `api-enabled: true` in
`plugins/CoreProtect/config.yml`. TimeLens reports this clearly in the console if it is off.

## Installation

1. Install CoreProtect 24.0 or newer and let it run long enough to record history.
2. Drop `TimeLens-<version>.jar` into your `plugins/` directory.
3. Restart the server.
4. Grant `timelens.use` to whoever should be able to look at the past.

TimeLens can only show history that CoreProtect **already recorded**. A freshly installed
CoreProtect has no past to show, so `/timelens 7d` on a new server correctly reports that it
found nothing.

## Usage

Stand where you want to look, and ask for a point in time — either an age or a calendar date:

```
/timelens 30m
/timelens 7d
/timelens 2026-08-20
/timelens 2026-08-20 14:30
```

If part of a build is missing, the view was smaller than the build. Pass a radius to widen it:

```
/timelens 7d 96
```

TimeLens replies while it works, then reports what it found:

```
TimeLens › Loading world history from 7 days ago...
TimeLens › Viewing the world from 7 days ago.
TimeLens › 1,284 blocks restored within 48 blocks of you.
TimeLens › Missing part of a build? Widen the view with /timelens <when> <radius> up to 128.
TimeLens › Use /timelens exit to return to the present.
```

While a view is open you are held where you stood and cannot break, place or interact with
blocks — see [Current limitations](#current-limitations). Looking around is unrestricted, and
normal movement returns the moment you exit:

```
/timelens exit
```

### Time formats

An **age**, counted back from now:

| Suffix | Unit | Example |
|---|---|---|
| `s` | seconds | `45s` |
| `m` | minutes | `30m` |
| `h` | hours | `2h` |
| `d` | days | `7d` |
| `w` | weeks | `2w` |

Months and years are deliberately unsupported as ages: their length is ambiguous, so they cannot
be resolved to an exact instant without inventing a convention. Use a date instead.

Or a **calendar moment**, in the server's own time zone:

| Form | Means |
|---|---|
| `2026-08-20` | the start of that day |
| `2026-08-20 14:30` | that day at 14:30 |
| `2026-08-20 14:30:45` | to the second |
| `14:30` | earlier today |

`2026-08-20T14:30` works too. A moment that has not happened yet is refused.

> **Times are read in the server's timezone, not yours.** A host running in UTC while its players
> are in Europe will resolve `/timelens 14:30` to 14:30 **UTC**. Check the server's clock before
> reporting a wrong-time bug.

## Commands

| Command | Description |
|---|---|
| `/timelens <when> [radius]` | View the area as it looked at that moment |
| `/timelens exit` | Return to the present |
| `/timelens status` | Show the view you currently have open |
| `/timelens help` | List the available commands |

`<when>` is an age or a date. `[radius]` is optional and overrides `view.radius` for that one
view, up to `view.maximum-radius`.

Subcommands are case-insensitive, and all four are tab-completed.

## Permissions

| Permission | Default | Grants |
|---|---|---|
| `timelens.use` | `op` | All four `/timelens` subcommands |
| `timelens.admin` | `op` | Reserved for future administrative features |

`timelens.admin` grants nothing in v0.1.0. It exists so server owners can set up their
permission groups once, before later versions add administrative commands.

Both permissions are declared in `plugin.yml` with `op` defaults and can be granted or revoked
with any permission plugin.

## Configuration

`plugins/TimeLens/config.yml`:

```yaml
view:
  radius: 48
  vertical-radius: 48
  maximum-radius: 96
  freeze-movement: true
  block-interactions: true

history:
  maximum-lookback: 30d
  maximum-results: 25000

messages:
  prefix: "<green>TimeLens <dark_gray>›</dark_gray> "
```

| Key | Meaning |
|---|---|
| `view.radius` | How far the view reaches along X and Z, in blocks |
| `view.vertical-radius` | How far the view reaches along Y, in blocks |
| `view.maximum-radius` | Ceiling for both radii and for the `[radius]` argument |
| `view.freeze-movement` | Hold the viewer where the view was taken from until they exit |
| `view.block-interactions` | Stop the viewer breaking, placing and interacting while viewing |
| `history.maximum-lookback` | The furthest back a player may ask to see |
| `history.maximum-results` | Refuse to build a view from more recorded changes than this |
| `messages.prefix` | MiniMessage markup placed before every TimeLens message |

**`view.radius` is the setting that matters most.** A view only reconstructs blocks inside it, so
a build that extends further will look half-restored. TimeLens reports the radius it used with
every view so the cause is visible.

Radius alone is a poor measure of cost, though — what matters is how much recorded history sits
inside the volume. A radius of 96 over quiet forest is cheap; the same radius over a busy town
centre is not. `history.maximum-results` is the real safety brake, and it refuses a request
outright rather than rendering it slowly.

Values are validated at startup. Anything unusable is reported in the console and replaced with
the shipped default, so a typo cannot stop the plugin from enabling.

If a request matches more changes than `maximum-results`, TimeLens refuses it rather than
rendering slowly, tells the player to narrow the time range, and logs the details.

`maximum-results` is the first of two brakes on tick time. The database lookup runs off the main
thread, but deciding which blocks to send does not — it reads live world state, at roughly
0.5–1.2 microseconds per block on Paper 1.21.11.

Because a recorded change count is a poor predictor of how many distinct blocks it resolves to,
TimeLens applies a second, internal cap of **12,000 rendered blocks** per view. A request over
either limit is refused with an explanation rather than rendered slowly. See
[TESTING.md](TESTING.md) for measured figures.

## How it works

```
CoreProtect
     │  historical records
     ▼
HistoryProvider        ── off the server thread
     ▼
HistoricalReconstructor
     ▼
HistoricalSnapshot     ── immutable, handed back to the server thread
     ▼
HistoricalRenderer
     │  player-only fake block data
     ▼
Player's client
```

The real server world: **unchanged**.

### Reconstruction

For every position that changed inside the view, TimeLens walks that position's recorded
changes from newest to oldest and applies the inverse of each:

- undoing a **placement** empties the position, because whatever was placed was not there
  beforehand;
- undoing a **removal** puts back exactly the block recorded as removed.

Both inverses overwrite the position outright, so the value left after the walk is the one
contributed by the oldest change — which is what the position held just before it, at the
requested time. A replaced block appears in the history as a removal followed by a placement:
undoing the placement clears it, and undoing the older removal restores what was replaced.

Positions that did not change are never touched, because the present world is already the
correct answer for them. Reconstructed states that turn out to match the live block are dropped
too, so nothing redundant reaches the client.

### Threading

Database work never runs on the server thread, and world reads and packet sends never run off
it. The only thing that crosses between them is an immutable snapshot.

### Rendering

Blocks are delivered with Paper's `Player#sendMultiBlockChange`, which fakes one packet per
chunk section and does not change the world. Only the requesting player receives them.

## Current limitations

TimeLens v0.1.0 is a deliberately narrow first release. Please read this section before
reporting behaviour as a bug.

**The world is never modified.** This is a design guarantee, not a limitation — but it is the
reason for most of the limitations below.

- **Views are player-specific.** Only the requesting player sees the past. This is intended.
- **CoreProtect is required**, and it must already contain history covering the period you ask
  for. TimeLens cannot show what was never recorded.
- **Only block state is reconstructed.** Container transactions, chat, commands, sessions and
  interactions are read past and ignored.
- **Entities are not reconstructed.** Mobs, pets, villagers, boats, minecarts, item frames,
  armour stands, paintings, dropped items and players all appear as they are now. This is not a
  gap that can be closed against CoreProtect: it records only that an entity *died*, with a time
  and a place, and never where anything stood or moved. Its entity table has no coordinate
  columns at all. Replaying entities would need TimeLens to record its own entity history and to
  send client-side fake entities through a packet library — two things v0.1.0 deliberately does
  not do. It is future work, not a bug.
- **Inventories and container contents are not reconstructed.** A historical chest is shown as a
  chest; opening it shows its present contents.
- **Block entity and NBT data may be incomplete.** Sign text, banner patterns, skull owners and
  similar detail are not fully restored, because CoreProtect's block history does not carry all
  of it.
- **A block added since the viewed moment simply disappears.** Nothing was there, so nothing is
  shown. That is the truthful view, but it does mean an empty area and an area TimeLens did not
  reach look the same — check the radius it reports if a view seems short.
- **Two-block structures are reconstructed from their base block.** History records a door, bed
  or tall plant only once, at its lower or foot block, so TimeLens derives the other half. That
  keeps doors and beds whole, but the derived half is inferred rather than recorded.
- **The view is bounded by `view.radius`.** Anything further from you than that is not
  reconstructed, which is the usual reason a view looks like it is missing part of a build. Pass
  a larger radius, or raise the default.
- **The view does not follow you.** It is rendered once, around where you stood. Walking a long
  way and back may show real blocks again as the client reloads those chunks. A view that
  re-renders as you move is planned for a later version.
- **Collision still uses the real world.** The server does not know about your historical view,
  so a block built recently is invisible to you but still solid, and a block removed recently is
  visible to you but not there. This cannot be fixed from a plugin: only your client was told
  about the past. `view.freeze-movement` is on by default because holding you still avoids the
  mismatch entirely; set it to `false` to move freely and accept the oddity.
- **Breaking, placing, bucket use and block interaction are blocked** while viewing, and
  explained in the action bar. Acting on a block the server does not believe exists is the
  fastest way to desynchronise a client. `/timelens exit` always works.
- **Rolled-back history is skipped.** If CoreProtect marks an entry as rolled back, that change
  is no longer reflected in the current world, so reversing it would move the position away from
  the truth. TimeLens therefore ignores those entries. CoreProtect records the rollback as a flag
  on the original row rather than as a new event, so there is no record of *when* the rollback
  ran. A view spanning a rollback shows the area as though the rolled-back changes never
  happened — accurate relative to the present, but unable to depict the period during which
  those changes were live.
- **Unrecorded removals are dropped, not guessed.** If CoreProtect recorded that something was
  removed but not what it was, that position is left showing the present rather than a guess.
- **Reconstruction is approximate at second boundaries.** History timestamps have one-second
  resolution, so a view is accurate to roughly a second either side of the requested moment.
- **Unloaded chunks are skipped** rather than force-loaded, so a view will not stall the server
  to fetch blocks the player cannot see anyway.

Not implemented in v0.1.0, and intentionally so: GUIs, timeline sliders, playback, timelapses,
contributor mode, block-owner inspection, compare mode, rollback, Folia support, and metrics.

## Building from source

Requires JDK 25 and Maven.

```bash
mvn clean verify
```

The plugin jar is written to `target/TimeLens-<version>.jar`. Tests run as part of `verify`.

The compiler targets Java 21, so the jar runs on any Java 21+ server. Paper and CoreProtect are
`provided` dependencies and are never shaded into the jar.

### Manual testing

1. Start a Paper 1.21.11 server with CoreProtect installed.
2. Join and place several blocks.
3. Break or replace some of them.
4. Run `/timelens <when>` covering the period before your edits.
5. Confirm the old blocks appear for you, and that a second player still sees the present.
6. Run `/timelens exit` and confirm the real world returns.
7. Confirm no blocks in the world and no CoreProtect data were changed.

## Contributing

Contributions are welcome — see [CONTRIBUTING.md](CONTRIBUTING.md) for the ground rules,
architecture notes and the one invariant that must never be broken.

[TESTING.md](TESTING.md) is the runtime validation checklist. Unit tests cannot prove that a view
is player-specific; that needs two accounts and a real server, and every bug found so far was
caught by running the plugin rather than by reading it.

## License

Released under the [MIT License](LICENSE). Copyright (c) 2026 Lime.
