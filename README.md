# RedstoneOptimizer

A Paper 1.21.11 plugin for busy farms. It keeps the server simulation intact and trims what clients are told to redraw.

## Why this exists

Big redstone farms send a lot of block updates that nobody standing at the farm actually needs to see. Item piles also burn entity tracking and client frame time. This plugin keeps those things quieter without changing redstone logic or deleting drops.

## Redstone

The plugin watches block update and block event packets on the way out to each player. By default every redstone part change is held back, no matter how close the player is. The server still ticks the redstone exactly as before. That stops the client from rebuilding chunk sections for every wire and piston toggle, which is what usually tanks FPS at a large farm.

Every held position is remembered. Within `redstone.repair-chunks` the plugin sends the current block states in one batched section update every `redstone.refresh-interval-ticks`, so the client still catches up without seeing every intermediate step. Piston events are filtered too.

If you want nearby redstone to animate normally, set `redstone.quiet-all` to false and choose a `view-chunks` range. That brings the FPS cost back for anything inside the range.

This is a visual and network optimisation. It is not a redstone tick accelerator.

## Items

Item entity tracking is set to 2 chunks by default. Items outside that range are not sent to clients, but they still exist on the server, still tick, still move in water, still despawn and can still be picked up.

The range is set on each item entity, so item frames and paintings keep their normal range. On builds where per-item tracking cannot be reached, the plugin falls back to Spigot's misc tracking range.

## Far blocks

Hoppers and redstone parts can be removed from the client when they are far away. The server never changes the real blocks, so farms, hoppers, water and storage still work. The first pass scans loaded chunks for hopper and redstone positions, then the client is sent air for those positions in batched section updates. Walking back inside `far-blocks.restore-chunks` restores the real block states.

By default the hide range follows the player's sent view distance, so blocks are hidden almost to the render edge. Restoration happens 8 blocks inside the hide edge, and the check runs every 10 ticks. Hoppers are found from chunk block entities every pass, so they do not wait for the slower redstone palette scan.

## Item piles

Nearby stationary stacks of the same item and owner are combined on a timer. The default radius is small, so sorters and water streams are left alone. The merge pass has a budget and backs off if it cannot keep up.

## Hoppers

Loaded chunks around each player are counted. The default cap is 4000 hoppers inside an 8 chunk radius. Once that region is full, another hopper cannot be placed. Counts are kept per chunk and adjusted on place, break and explosion events.

If TPS drops, the plugin widens Spigot's hopper transfer and check intervals. The original values are put back when TPS recovers or the plugin stops. That part can be turned off if exact hopper timing matters more than the lag response.

## Under load

The TPS watch runs separately from the work modules. When TPS falls below the low mark, item piles merge over a wider radius and hoppers get the slower intervals. When TPS recovers, both go back to normal. The redstone packet filter stays on.

## Install

1. Put the jar in `plugins`.
2. Start Paper 1.21.11.
3. Change `plugins/RedstoneOptimizer/config.yml` if the defaults do not fit the server.
4. Run `/redstoneoptimizer reload`.

The built jar is `build/libs/RedstoneOptimizer-1.0.0.jar`.

## Build

Java 21 is required. The checked-in wrapper downloads Gradle.

```
gradlew.bat build
```

## Commands

`/redstoneoptimizer status` shows ranges, held packet counts, merge count, hopper count near the sender and TPS state.

`/redstoneoptimizer reload` reloads the config file and restarts the background work.

`/redstoneoptimizer reset` replaces the config with the packaged defaults and restarts the work. This overwrites the current config file.

Aliases are `/ropt` and `/ro`.

## Permissions

`redstoneoptimizer.admin` allows status and reload. Default is op.

`redstoneoptimizer.bypass.redstone` keeps full redstone updates for that player. Default is false.

`redstoneoptimizer.bypass.hopper` skips the area hopper cap for that player. Default is false. Operators do not skip the cap unless they have this permission.

`redstoneoptimizer.bypass.farblocks` keeps far hoppers and redstone parts visible for that player. Default is false.

## Settings worth knowing

`items.tracking-chunks` is the item range. Default is 2.

`items.merge.radius` and `items.merge.low-tps-radius` control pile merging in normal and loaded conditions.

`redstone.quiet-all` is the main FPS switch. It is true by default and holds back every redstone update. If you set it to false, `redstone.view-chunks` controls how far redstone updates still go through.

`redstone.view-chunks` is the redstone range when `quiet-all` is false. Default is 2.

`redstone.repair-chunks` is how close a held redstone block must be before it is refreshed in a batch. Default is 2.

`redstone.refresh-interval-ticks` is the batch refresh timer. Default is 20 ticks.

`redstone.materials` lists the blocks treated as redstone parts. Buttons, pressure plates and copper bulbs are also matched by name.

`far-blocks.hide-to-view` follows the player's sent view distance and hides blocks almost to the render edge. Set it to false if you want the fixed `hide-chunks` value instead.

`far-blocks.hide-chunks` is the fixed hide radius used when `hide-to-view` is false. `restore-chunks` is the fixed restore radius for that mode.

`far-blocks.scan-budget` is how many chunks are inspected per tick while building the redstone hide list. Hoppers do not depend on this scan. Lower it if chunk scanning causes a tick spike.

`hoppers.region-chunks` is the radius used for the hopper cap. With 8, the plugin checks 17 by 17 loaded chunks.

`hoppers.max-per-region` is the cap. Default is 4000.

`hoppers.lag-tuning` controls the temporary hopper interval change.

## Tradeoffs

Redstone can look stale for up to one refresh interval, and nearby redstone does not animate by default. That is the point of the filter, but it can look odd after a teleport or a large schematic paste. Turn off `redstone.quiet-all` if you need normal animation nearby.

The plugin does not make redstone calculate faster on the server. It reduces client chunk rebuilds, network traffic and item entity load.

Paper's own redstone, hopper and entity settings still matter. This plugin sits alongside them.
