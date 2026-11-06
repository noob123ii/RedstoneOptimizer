# RedstoneOptimizer

A Paper 1.21.11 plugin for busy farms. It keeps the server simulation intact and trims what clients are told to redraw.

## Why this exists

Big redstone farms send a lot of block updates that nobody standing at the farm actually needs to see. Item piles also burn entity tracking and client frame time. This plugin keeps those things quieter without changing redstone logic or deleting drops.

## Redstone

Redstone inside `far-blocks.distance` updates normally, including piston animations, observers and redstone wire changes. The server ticks the redstone exactly as before. Updates beyond that distance are dropped because those blocks are also hidden client-side, so they cannot pop back in.

The filter checks distance before it looks at the block material, and whole sections that are already close are skipped. That keeps the normal farm speed and animation without adding much packet work.

This is a visual and network optimisation. It is not a redstone tick accelerator.

## Items

Item entity tracking is set to 2 chunks by default. Items outside that range are not sent to clients, but they still exist on the server, still tick, still move in water, still despawn and can still be picked up.

The range is set on each item entity, so item frames and paintings keep their normal range. On builds where per-item tracking cannot be reached, the plugin falls back to Spigot's misc tracking range.

## Far blocks

Hoppers and redstone parts are removed from the client beyond `far-blocks.distance` blocks. This is block distance, not chunk distance. The server never changes the real blocks, so farms, hoppers, water and storage still work. The first pass scans loaded chunks for hopper and redstone positions, then the client is sent air for those positions in batched section updates. Walking back inside the distance restores the real block states.

Hoppers are found from chunk block entities every pass, so they do not wait for the slower redstone palette scan. The check runs every 10 ticks.

## Item piles

Nearby stationary stacks of the same item and owner are combined on a timer. The default radius is small, so sorters and water streams are left alone. The merge pass has a budget and backs off if it cannot keep up.

## Hoppers

Loaded chunks around each player are counted. The default cap is 4000 hoppers inside an 8 chunk radius. Once that region is full, another hopper cannot be placed. Counts are kept per chunk and adjusted on place, break and explosion events.

If TPS drops, the plugin widens Spigot's hopper transfer and check intervals. The original values are put back when TPS recovers or the plugin stops. That part can be turned off if exact hopper timing matters more than the lag response.

## Under load

The TPS watch runs separately from the work modules. When TPS falls below the low mark, item piles merge over a wider radius. Hopper transfer speeds are never changed.

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

`redstone.materials` lists the blocks treated as redstone parts. Buttons, pressure plates and copper bulbs are also matched by name.

`far-blocks.distance` is the block distance used for both hiding and redstone update filtering. Default is 40 blocks. Values are kept between 30 and 40.

`far-blocks.scan-budget` is how many chunks are inspected per tick while building the redstone hide list. Hoppers do not depend on this scan. Lower it if chunk scanning causes a tick spike.

`hoppers.region-chunks` is the radius used for the hopper cap. With 8, the plugin checks 17 by 17 loaded chunks.

`hoppers.max-per-region` is the cap. Default is 4000.

## Tradeoffs

Redstone only stops rendering beyond the configured block distance. Inside that distance it updates and animates normally. A large schematic paste or a chunk reload can leave far blocks visible until the next hide pass catches them.

The plugin does not make redstone calculate faster on the server. It reduces client chunk rebuilds, network traffic and item entity load.

Paper's own redstone, hopper and entity settings still matter. This plugin sits alongside them.
