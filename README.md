# Zephyrium - Parallel Entity Processing

Zephyrium is a server-side Fabric mod for Minecraft 1.21.1 and 26.2 that ticks entities in parallel with mod compatibility in mind.

**You need at least 2 cores to see a difference. The more cores, the better.**

<details>
  <summary>How it works</summary>

In vanilla, one thread ticks every mob, item and minecart one after another, so a big farm slows the whole server down. With this mod each entity gets its own actor-style process on a Java virtual thread, and the server spreads those processes over CPU cores. Players, blocks and chunks stay on the server thread, as in vanilla.

An entity can only be changed by its own process. When another entity, the server or a mod wants to change it, for example when one mob hits another, the request is sent to that entity's process as a message and handled in turn. Simple lookups, like where an entity is or how much health it has, are answered from a snapshot the entity publishes between messages, so nobody has to wait for them. This is enforced by a bytecode transformer, so mods can do whatever trickery with mixins and it will not break (aka good mod compatibility).

Changes to the shared world and unsafe operations, such as placing a block, are sent to the server thread, which applies them in order at safe points.

When there are more entities than your CPU can handle, the ones that fall behind skip ticks instead of lagging the whole server, so TPS stays stable and only those entities slow down.

The mod follows the "let it die" philosophy: a process that crashes or hangs is killed and its entity is restarted from a checkpoint a few seconds old, so one broken entity cannot crash or freeze the server.
</details>

## Configuration

Configs are in `config/zephyrium.json`.
Set `entityProcesses` to `false` to tick entities as vanilla does.

## Commands

All commands need permission level 3.

- `/zo reload` reloads the config.
- `/zo profile start [seconds]` profiles entity processes.
- `/zo profile stop`, `/zo profile status` and `/zo profile entity <target>` stop a session, show the live state, and describe one entity's process.

**It may and will be VERY broken. It can break your entities, your world and your dog. You have been warned.**

### Report any issues on issue tracker or in [Discord](https://discord.gg/4nZYz3a4KE)!
