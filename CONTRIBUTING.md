# Contributing to TimeLens

Thanks for taking an interest in TimeLens.

## The one rule

**TimeLens never modifies the world.**

TimeLens is a visualisation layer. It reads history and draws it on one player's client. If a
change would write blocks to the world, mutate CoreProtect's data, or make other players see
something that is not really there, it does not belong in this project, no matter how useful it
seems. Rollback tools already exist, and TimeLens is not one of them.

Everything else in this document is negotiable. That rule is not.

## Getting set up

Requires JDK 25 and Maven.

```bash
mvn clean verify
```

The jar lands in `target/`. To try it on a real server you need Paper 1.21.11 and CoreProtect
24.0+, and CoreProtect must already hold history for the period you want to view.

## Architecture

The pipeline is deliberately split so each stage can be replaced or tested on its own:

```
HistoryProvider  →  HistoricalReconstructor  →  HistoricalSnapshot  →  HistoricalRenderer
```

| Package | Responsibility |
|---|---|
| `history` | Where history comes from. The only place CoreProtect is imported. |
| `reconstruction` | Pure logic: turn recorded changes into a past state. No Bukkit types. |
| `render` | Send block states to one client. Server thread only. |
| `session` | Who is viewing what, and who has a query in flight. |
| `view` | Coordinates the request across threads. |
| `command`, `listener`, `config`, `message`, `util` | Edges and support. |

Two boundaries matter most:

- **Keep CoreProtect inside `history`.** Later versions may add another backend. If CoreProtect
  types start appearing elsewhere, that becomes impossible.
- **Keep `reconstruction` free of Bukkit.** It is pure so it can be unit tested without a
  server, which is why the reconstruction tests are fast and meaningful.

## Threading

Get this wrong and you will corrupt a live server, so it is worth stating plainly:

- History lookups run **off** the server thread. They hit a database.
- Reading world state and sending packets run **on** the server thread. Always.
- Only immutable values cross between the two.

If you add a stage, say in its Javadoc which thread owns it.

## Code style

- Java 21 language level, four-space indentation, 120-column soft limit.
- No Lombok, no Kotlin, no new dependencies without a clear reason.
- Small classes with one responsibility. No thousand-line managers.
- Records for immutable domain data.
- No wildcard imports, no dead code and no commented-out code. Use the plugin logger rather
  than `System.out.println`.
- Comments explain **why**, not what. If the code needs a comment to say what it does, rename
  something instead.
- Validate anything nullable that comes from an external API.

`.editorconfig` covers the mechanical parts.

## Tests

The reconstruction algorithm and the duration parser are unit tested, and both must stay that
way. If you change how history is reversed, add the scenario to
`HistoricalReconstructorTest` first, because the scenarios there are the specification.

Run `mvn clean verify` before opening a pull request. CI runs the same command.

## Pull requests

- One logical change per pull request.
- Explain what a reviewer should look at, and what you tested on a real server.
- Update `CHANGELOG.md` under `[Unreleased]`.
- Update the README if you changed behaviour a server owner would notice. Do not document
  features that are not implemented.

## Scope

TimeLens v0.1.0 is intentionally small. Timeline navigation, playback, block inspection,
contributor visualisation and compare mode are all planned, but each is its own release. A pull
request that adds one of them to v0.1.0 will be asked to wait, not because the idea is bad but
because the foundation should prove itself first.

Bug reports, correctness fixes and documentation improvements are always welcome.

## License

By contributing you agree that your contributions are licensed under the
[MIT License](LICENSE).
