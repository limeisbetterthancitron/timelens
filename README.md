# TimeLens

**Explore your Minecraft world's past.**

TimeLens reconstructs how an area of your world looked at an earlier point in time and shows it
to a single player, using the block history CoreProtect has already recorded. The world itself is
never altered. The past is drawn only on one player's screen.

```
/timelens 7d
```

![Status](https://img.shields.io/badge/status-alpha-orange)
![Minecraft](https://img.shields.io/badge/Minecraft-1.21.11-brightgreen)
![Paper](https://img.shields.io/badge/server-Paper-blue)
![License](https://img.shields.io/badge/license-MIT-lightgrey)

> **This is an alpha release.** The core promise, that a historical view is visible only to the
> player who requested it, has not yet been confirmed with two clients on a live server. Please
> do not run TimeLens on a production server until it has.

## Contents

- [What TimeLens does](#what-timelens-does)
- [Features](#features)
- [Requirements](#requirements)
- [Installation](#installation)
- [Usage](#usage)
- [Commands](#commands)
- [Permissions](#permissions)
- [Configuration](#configuration)
- [How it works](#how-it-works)
- [Current limitations](#current-limitations)
- [Building from source](#building-from-source)
- [Contributing](#contributing)
- [License](#license)

## What TimeLens does

TimeLens is a visualisation layer. It is not a rollback tool.

When a player asks to see seven days ago, TimeLens reads the block changes CoreProtect recorded
since then, works backwards to determine what each affected position held at that moment, and
sends those blocks to that player's client as a private overlay. The server continues to hold,
simulate and serve the real world exactly as before.

That distinction is the whole point of the project:

- the world on disk is never written to
- CoreProtect's database is only ever read
- other players online at the same time see the present, unchanged
- the viewer's own client is returned to the present when they exit

## Features

- View any area as it looked at a point in the past, given either as an age (`7d`) or as a
  calendar date and time (`2026-08-20 14:30`).
- Historical blocks are sent to one player only.
- History lookups run off the main server thread, so the server keeps ticking.
- Two independent limits bound the cost of a single view, and a request that exceeds either one
  is refused with an explanation rather than rendered slowly.
- Views are cleaned up automatically on exit, disconnect, world change, teleport and death.
- Nothing about a player is modified, so no TimeLens state can survive a crash.

## Requirements

| | |
|---|---|
| Server | Paper 1.21.11, or a fork of it |
| Java | 21 or newer |
| Dependency | [CoreProtect](https://modrinth.com/plugin/coreprotect) 24.0 or newer, API v12 |

TimeLens uses Paper-only APIs and will not load on Spigot or CraftBukkit.

CoreProtect is a hard dependency, since it is the only source of history in this release. Its API
must also be enabled, which means `api-enabled: true` in `plugins/CoreProtect/config.yml`.
TimeLens reports this clearly in the console if it is switched off.

## Installation

1. Install CoreProtect 24.0 or newer and let it run long enough to record some history.
2. Place `TimeLens-<version>.jar` in your `plugins` directory.
3. Restart the server.
4. Grant `timelens.use` to whoever should be able to look at the past.

TimeLens can only show history that CoreProtect has already recorded. A freshly installed
CoreProtect has no past to show, so `/timelens 7d` on a new server correctly reports that it
found nothing.

## Usage

Stand where you want to look, then ask for a point in time. This can be an age counted back from
now, or a calendar date:

```
/timelens 30m
/timelens 7d
/timelens 2026-08-20
/timelens 2026-08-20 14:30
```

TimeLens replies while it works, then reports what it found:

```
TimeLens › Loading world history from 7 days ago...
TimeLens › Viewing the world from 7 days ago.
TimeLens › 1,284 blocks restored within 48 blocks of you.
TimeLens › Use /timelens exit to return to the present.
```

If part of a build is missing, the view was smaller than the build. Pass a radius to widen it:

```
/timelens 7d 96
```

While a view is open you are held where you stood, and you cannot break, place or interact with
blocks. Looking around is unrestricted, and normal movement returns as soon as you exit:

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

Months and years are not supported as ages, because their length is ambiguous and cannot be
resolved to an exact instant without inventing a convention. Use a date instead.

A **calendar moment**, read in the server's own time zone:

| Form | Meaning |
|---|---|
| `2026-08-20` | the start of that day |
| `2026-08-20 14:30` | that day at 14:30 |
| `2026-08-20 14:30:45` | to the second |
| `14:30` | earlier today |

`2026-08-20T14:30` is accepted as well. A moment that has not happened yet is refused.

> **Times are read in the server's time zone, not yours.** A host running in UTC while its
> players are in Europe will resolve `/timelens 14:30` to 14:30 UTC. Check the server's clock
> before reporting a wrong-time bug.

## Commands

| Command | Description |
|---|---|
| `/timelens <when> [radius]` | View the area as it looked at that moment |
| `/timelens exit` | Return to the present |
| `/timelens status` | Show the view you currently have open |
| `/timelens help` | List the available commands |

`<when>` is an age or a date. `[radius]` is optional, and overrides `view.radius` for that single
view up to `view.maximum-radius`.

Subcommands are case-insensitive, and all of them are tab-completed.

## Permissions

| Permission | Default | Grants |
|---|---|---|
| `timelens.use` | `op` | All four subcommands |
| `timelens.admin` | `op` | Reserved for future administrative features |

`timelens.admin` grants nothing in this release. It exists so that server owners can set up their
permission groups once, before later versions add administrative commands.

Both permissions are declared in `plugin.yml` with `op` defaults, and can be granted or revoked
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

Values are validated at startup. Anything unusable is reported in the console and replaced with
the shipped default, so a typo cannot stop the plugin from enabling.

### Sizing a view

`view.radius` is the setting that matters most. A view only reconstructs blocks inside it, so a
build that extends further will look half-restored. TimeLens reports the radius it used with
every view, which makes the cause visible.

Radius alone is a poor measure of cost, because what matters is how much recorded history sits
inside the volume. A radius of 96 over quiet forest is cheap, while the same radius over a busy
town centre is not.

Two independent limits protect tick time:

1. `history.maximum-results` bounds how many recorded changes a view may be built from.
2. An internal cap of 12,000 blocks bounds how many are actually rendered.

The second exists because the first is a poor predictor of it. Thousands of recorded changes can
collapse onto a handful of coordinates, or spread across as many distinct positions. Main-thread
cost tracks rendered blocks rather than recorded changes, at roughly 0.5 to 1.2 microseconds per
block on Paper 1.21.11. Measured figures are in [TESTING.md](TESTING.md).

## How it works

```
CoreProtect
     │  historical records
     ▼
HistoryProvider          off the server thread
     ▼
HistoricalReconstructor
     ▼
HistoricalSnapshot       immutable, handed back to the server thread
     ▼
HistoricalRenderer
     │  player-only fake block data
     ▼
Player's client
```

The real server world stays **unchanged**.

### Reconstruction

For every position that changed inside the view, TimeLens walks that position's recorded changes
from newest to oldest and applies the inverse of each one:

- undoing a **placement** empties the position, because whatever was placed was not there
  beforehand
- undoing a **removal** puts back exactly the block recorded as removed

Both inverses overwrite the position outright, so the value left after the walk is the one
contributed by the oldest change. That is precisely what the position held just before it, at the
requested time.

A replaced block appears in the history as a removal followed by a placement. Undoing the
placement clears it, and undoing the older removal restores what was replaced.

Positions that did not change are never touched, because the present world is already the correct
answer for them. Reconstructed states that turn out to match the live block are dropped as well,
so nothing redundant reaches the client.

### Threading

Database work never runs on the server thread, and world reads and packet sends never run off it.
The only thing that crosses between them is an immutable snapshot.

### Rendering

Blocks are delivered with Paper's `Player#sendMultiBlockChange`, which fakes one packet per chunk
section and does not change the world. Only the requesting player receives them.

## Current limitations

Please read this section before reporting behaviour as a bug.

**The world is never modified.** That is a design guarantee rather than a limitation, but it is
the reason for most of what follows.

- **Views are player-specific.** Only the requesting player sees the past. This is intended.
- **CoreProtect is required**, and it must already contain history covering the period you ask
  for. TimeLens cannot show what was never recorded.
- **Only block state is reconstructed.** Container transactions, chat, commands, sessions and
  interactions are read past and ignored.
- **Entities are not reconstructed.** Mobs, pets, villagers, boats, minecarts, item frames,
  armour stands, paintings, dropped items and players all appear as they are now. This gap cannot
  be closed against CoreProtect, which records only that an entity died, with a time and a place,
  and never where anything stood or moved. Its entity table has no coordinate columns at all.
  Replaying entities would require TimeLens to record its own entity history and to send
  client-side fake entities through a packet library, neither of which this release does.
- **Inventories and container contents are not reconstructed.** A historical chest is shown as a
  chest, and opening it shows its present contents.
- **Block entity and NBT data may be incomplete.** Sign text, banner patterns, skull owners and
  similar detail are not fully restored, because CoreProtect's block history does not carry all
  of it.
- **A block added since the viewed moment simply disappears.** Nothing was there, so nothing is
  shown. That is the truthful view, but it does mean an empty area and an area TimeLens did not
  reach look the same. Check the radius it reports if a view seems short.
- **The view is bounded by `view.radius`.** Anything further away is not reconstructed, which is
  the usual reason a view looks like it is missing part of a build.
- **The view does not follow you.** It is rendered once, around where you stood.
- **Two-block structures are reconstructed from their base block.** History records a door, bed
  or tall plant only once, at its lower or foot block, so TimeLens derives the other half. That
  keeps doors and beds whole, but the derived half is inferred rather than recorded.
- **Collision still uses the real world.** The server does not know about your historical view,
  so a block built recently is invisible to you but still solid, and a block removed recently is
  visible to you but not there. This cannot be fixed from a plugin, because only your client was
  told about the past. `view.freeze-movement` is enabled by default because holding you still
  avoids the mismatch entirely.
- **Breaking, placing, bucket use and block interaction are blocked** while viewing, and
  explained in the action bar. Acting on a block the server does not believe exists is the
  fastest way to desynchronise a client. `/timelens exit` always works.
- **Rolled-back history is skipped.** If CoreProtect marks an entry as rolled back, that change
  is no longer reflected in the current world, so reversing it would move the position away from
  the truth. CoreProtect records the rollback as a flag on the original row rather than as a new
  event, so there is no record of when the rollback ran. A view spanning a rollback shows the
  area as though the rolled-back changes never happened, which is accurate relative to the
  present but cannot depict the period during which those changes were live.
- **Unrecorded removals are dropped rather than guessed.** If CoreProtect recorded that something
  was removed but not what it was, that position keeps showing the present.
- **Reconstruction is approximate at second boundaries.** History timestamps have one-second
  resolution, so a view is accurate to roughly a second either side of the requested moment.
- **Unloaded chunks are skipped** rather than force-loaded, so a view will not stall the server to
  fetch blocks the player cannot see anyway.

Deliberately not implemented in this release: timeline playback, graphical interfaces, block
inspection, contributor visualisation, compare mode, rollback, Folia support and metrics.

## Building from source

Requires JDK 25 and Maven.

```bash
mvn clean verify
```

The plugin jar is written to `target/TimeLens-<version>.jar`, and the tests run as part of
`verify`.

The compiler targets Java 21, so the jar runs on any Java 21 or newer server. Paper and
CoreProtect are `provided` dependencies and are never shaded into the jar.

`tools/benchmark` contains a development harness for measuring the render path without two
clients. It is not part of the build and is not shipped.

### Testing

[TESTING.md](TESTING.md) is the runtime validation checklist and the release gate. Unit tests
cannot prove that a view is player-specific, which needs two accounts and a live server. Every
defect found in this project so far was caught by running the plugin rather than by reading it.

## Contributing

Contributions are welcome. [CONTRIBUTING.md](CONTRIBUTING.md) covers the ground rules,
architecture notes and the one invariant that must never be broken.

## License

Released under the [MIT License](LICENSE). Copyright (c) 2026 Lime.
