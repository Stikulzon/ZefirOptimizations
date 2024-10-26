# Zefir's Optimizations

Zefir's Optimizations is a Fabric mod that adds parallel entity movement using Akka actors. It focuses primarily on separating the movement ticking of mobs and armor stands from the main thread. This can gradually improve the performance on the multi-core server.

It works by attaching an actor to each MobEntity and ArmorStand and redirecting movement calls and executing them on diffirent thread. For now, only the basic movement logic from Entity and LivingEntity is implemented.

**You must to have atleast 2 cores to see the diffirence! The more cores, the better.**

<details>
  <summary>How it works</summary>
Each mob and armor stand gets its own actor. When it's time for the entity to tick its movement logic, the Async Tick Manager tells the corresponding Entity Actor to do the work. The Entity Actor receives messages to its mailbox and processes them one by one.

Some actions, like dealing damage or picking up items, must be done on the main thread for safety reasons. When an Entity Actor needs to do one of these actions, it sends a message to the Main Thread Actor that do the work on main thread.

For now, Entity Actor implements tickMovement() and travel() from LivingEntity and MobEntity (armor stand inherits it form LivingEntity).
</details>

[![Static Badge](https://img.shields.io/badge/discord-label?style=for-the-badge&logo=discord&logoColor=%23ffffff&labelColor=%237289da&color=%237289da&link=https%3A%2F%2Fdiscord.gg%2FrgT62cvHNQ)](https://discord.gg/4nZYz3a4KE)

![Very informative diagram](https://cdn.modrinth.com/data/cached_images/f160e41399f4e8bb3256d4af8d1a96678b49ccfe.png)

**It may and will be VERY broken. It can broke your etities, your world and your dog. You have been warned.**

### Rport any issues in our [Discord](https://discord.gg/4nZYz3a4KE)!
