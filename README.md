# HereBlingy

A premium [Paper](https://papermc.io) Minecraft plugin for **automated branch mining** — systematically strip-mine, excavate ores, dynamically swap tools (pickaxes/shovels), plug fluid leaks, repair Mending tools via shared XP, and sort item drops into Treasures and Debris chests!

---

## Features

- **Dual Automation Modes**:
  - **Strip Mining (Infinite branch mining)**:
    - A completely **selection-free, infinite** branch-mining automation. 
    - progression digs a central **2x2 main tunnel** straight ahead in the direction the player is facing (North, South, East, or West) at their current Y level forever until manually stopped.
    - Off-shoots **2x1 perpendicular side branches** every 3 blocks (spacing of 3 solid blocks) extending left and right to a configurable width (default: 16 blocks, e.g., `/hb start [width]`).
  - **Terraforming (Bounded land leveling)**:
    - Bounded **3D land-clearing** mode guided by Point A & Point B selections.
    - Excavates the entire cuboid layer-by-layer (2-block-high horizontal slices) safely from the top down.
    - **Ignores Trees & Leaves**: Automatically bypasses log, wood, leaves, stems, or root blocks ("hubris") entirely so that natural forests are left intact while standard stone, dirt, sand, mud, and gravel blocks are leveled to the ground.

- **Dynamic Dual-Tool Swapping**:
  - Classifies blocks into **Pickaxe-Worthy** (stone, deepslate, ores, netherrack, tuff, calcite, diorite, andesite, granite, obsidian) and **Shovel-Worthy** (dirt, sand, gravel, clay, soul soil, snow, mud).
  - Automatically swaps active hotbar quickslots to the appropriate tool before swing execution.

- **VeinMiner Integration & Drop Attraction**:
  - Automatically scans a **6-block radius** around broken ores to vacuum all dropped items (perfectly supporting VeinMiner chain breaks).
  - Drops are instantly collected and routed to Keep (Treasures) or Trash (Debris) chests.

- **Active Fluid Leak Plugging**:
  - Scans adjacent blocks for fluid source blocks (Water/Lava) that could flood the player.
  - Places a plug block at the source location using whichever allowed material (`COBBLESTONE`, `COBBLED_DEEPSLATE`, `TUFF`, `NETHERRACK`) the player **currently has the largest quantity of in their inventory**.

- **Cobweb Avoidance & Navigation**:
  - Automatically detects `Material.COBWEB` coordinates along the path before entering them.
  - Instantly bypasses and skips these coordinates to prevent the player from getting trapped or slowed down, allowing seamless movement around cobweb zones without wasting tool durability.

- **Shared Mending XP Repair**:
  - Intercepts block-breaking experience orbs in a 6-block radius.
  - If either or both of the hotbar tools (Pickaxe/Shovel) have the **Mending** enchantment, directly uses the XP to repair their durability, prioritizing the more damaged tool first. Excess XP is given to the player.

- **Smart Tool Supply Refueling Station**:
  - Guides player via setup wizard to configure a **Tool Supply Chest**.
  - When either tool drops below **10 durability**, the player will head to the chest, deposit the worn tool, withdraw a fresh pickaxe/shovel, and return to mining automatically!

- **Configurable Treasures vs. Debris deposit**:
  - **Treasures**: Redstone, Lapis, Copper (raw/ingot), Iron (raw/ingot), Gold (raw/ingot), Diamonds, Emeralds, Quartz, Coal, Ancient Debris, Flint.
  - **Debris**: Sand, gravel, dirt, cobblestone, stone, deepslate, netherrack, tuff, diorite, granite, andesite, calcite, clay, mud.
  - Automatically dumps inventory items into assigned deposit chests when full.

---

## Commands

All commands can be run with `/hb` instead of `/hereblingy`.

| Command | Description | Permission |
|---------|-------------|------------|
| `/hereblingy start [width]` (or `/hb start [width]`) | Start auto-mining (Strip Mining mode starts selection-free infinite tunnel with side branch width; Terraforming mode levels selected 3D bounds) | `hereblingy.use` |
| `/hereblingy stop` (or `/hb stop`) | Stop active mining run and display stats | `hereblingy.use` |
| `/hereblingy restart` (or `/hb restart`) | Resume mining after chest/tool paused stops | `hereblingy.use` |
| `/hereblingy config` (or `/hb config`) | Open interactive chest mining configuration GUI | `hereblingy.config` |
| `/hereblingy setup` (or `/hb setup`) | Run chest deposit & tool supply setup wizard | `hereblingy.setup` |
| `/hereblingy clear` (or `/hb clear`) | Clear coordinate selectors | `hereblingy.use` |

---

## Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `hereblingy.use` | Allows use of `/hereblingy` commands | `true` |
| `hereblingy.setup` | Allows use of the setup wizard | `true` |
| `hereblingy.config` | Allows use of the mining config GUI | `true` |

---

## How to Use

### Toggling Job Modes
1. Type `/hb config` and click the **Active Job** icon at the bottom center (slot 22) to toggle between:
   * **Strip Mining**: Infinite 2x2 branch-mining straight ahead.
   * **Terraforming**: Ground-clearing of a selected 3D cuboid area (ignoring trees).

### 1. Strip Mining Setup (Infinite)
1. Ensure your active job is set to **Strip Mining** in `/hb config`.
2. Stand facing a solid wall in the direction you want to mine (North, South, East, or West).
3. Ensure you have a Pickaxe and Shovel in your hotbar quickslots.
4. Run `/hb start [width]` (e.g., `/hb start 16` to dig branches extending 16 blocks left and right).
5. The plugin will mine a central **2x2 main tunnel** straight ahead infinitely, off-shooting **2x1 branches** every 3 blocks, swapping tools and plugging fluid leaks as it goes.

### 2. Terraforming Setup (Bounded)
1. Ensure your active job is set to **Terraforming** in `/hb config`.
2. Hold a Pickaxe in your main hand.
3. Shift-right-click a block to set **Point A**.
4. Shift-right-click another block to set **Point B** (forms the boundaries of the land/hill to level).
5. Stand near the area and run `/hb start`.
6. The plugin will slice the selected cuboid layer-by-layer from the top down, clearing stone/soils/dirt and ignoring all tree trunks and leaves ("hubris").

### Advanced Chest Deposit & Tool Supply Setup
1. Run `/hb setup` to start the wizard.
2. Shift-right-click a chest where you want to deposit **Debris** (stone, sand, gravel).
3. Shift-right-click a chest where you want to deposit **Treasures** (ores, raw ingots, diamonds).
4. Shift-right-click a chest where you store **Spare Tools** (pickaxes/shovels), or type `skip` in chat.
5. Type a durability threshold in chat (e.g. `12`) or type `skip` (defaults to `10`).
6. Setup is complete! Your configurations are saved in `setup-configs/{playerId}.yml`.

### Mining Configuration GUI
1. Run `/hb config` to open the main category selector.
2. Click **Treasures**, **Standard Blocks**, or **Debris & Soils** to view the item inventory.
3. Hover over any block to see its status:
   - **Left-Click** to toggle breaking (`Enabled` / `Disabled`).
   - **Right-Click** to cycle its destination (`Treasures (Keep Chest)` ➔ `Debris (Trash Chest)` ➔ `Inventory (Keep on Person)`).
4. Configs are stored in `player-configs/{playerId}.yml`.

---

## Building from Source

Build using Gradle:
```bash
./gradlew build
```
The packaged JAR will be located at `build/libs/HereBlingy-1.0.0.jar`.

---

## License

This project is licensed under the [GNU General Public License v3.0](LICENSE).
