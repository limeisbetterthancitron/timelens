# Changelog

All notable changes to TimeLens are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this
project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.1.0-alpha.1] - 2026-08-25

First public test release. Not yet proven on production servers.

### Added

- Initial TimeLens historical world viewer.
- `/timelens <when> [radius]` renders how the surrounding area looked in the past, using block
  history recorded by CoreProtect, as a private overlay for the requesting player only.
- `/timelens exit` restores the viewer's client to the live world state.
- `/timelens status` reports the open view, and `/timelens help` lists the commands.
- Time given either as an age (`30m`, `2h`, `7d`, `2w`) or as a calendar moment in the server's
  time zone (`2026-08-20`, `2026-08-20 14:30`, `2026-08-20 14:30:45`, or `14:30` for earlier
  today). Moments in the future are refused.
- Optional per-view radius argument, so a view that fell short of a build can be widened without
  editing the configuration.
- Views report the radius they used, because a view smaller than the build is the usual reason
  one looks incomplete.
- Two-block structures — doors, beds, tall plants, pitcher crops and small dripleaf — are kept
  whole. History records only their base block, so the partner half is derived when one is shown
  and cleared when one is taken away, instead of leaving a broken door or floating bed half.
- Tab completion for subcommands and common time values.
- `timelens.use` and `timelens.admin` permissions, both defaulting to op.
- CoreProtect integration behind a `HistoryProvider` seam, validating that the plugin is
  present, enabled, exposing its API, and reporting API v12 or newer.
- Reconstruction engine that reverses recorded changes from newest to oldest, isolated from
  CoreProtect and covered by unit tests.
- History lookups run off the main server thread; world reads and packet sends stay on it.
- Configurable view radius, vertical radius, maximum radius, maximum lookback and maximum
  result count, all validated at startup with safe fallbacks.
- One active view and one in-flight query per player.
- Two independent limits on the cost of a single view: `history.maximum-results` bounds recorded
  changes, and an internal cap bounds the blocks actually rendered, since one is a poor predictor
  of the other.
- Session cleanup on exit, disconnect, world change, teleport and death.
- Interaction restrictions while a view is open, keeping the viewer from acting on blocks the
  server does not believe exist. `view.freeze-movement` additionally holds the viewer where the
  view was taken from until they exit, avoiding the mismatch between what they see and what the
  server still collides with. Looking around stays free either way.

[Unreleased]: https://github.com/limeisbetterthancitron/timelens/compare/v0.1.0-alpha.1...HEAD
[0.1.0-alpha.1]: https://github.com/limeisbetterthancitron/timelens/releases/tag/v0.1.0-alpha.1
