# TweaksKompass

## Introduction
**TweaksKompass** is a comprehensive, all-in-one gameplay overhaul plugin built specifically for **Project: Kompass** (running on PaperMC 26.3, with automatic Purpur integration and optional PlaceholderAPI support).

Designed to modernize and expand nearly every pillar of vanilla survival without needing dozens of separate plugins, TweaksKompass refines combat, mob artificial intelligence, world navigation, crafting progression, pet management, and survival quality-of-life. Every module can be individually toggled and fine-tuned to fit your server's exact balance.

---

## Permissions
TweaksKompass is designed to be plug-and-play—regular players do not require any permission nodes to unlock or use progression-based features and player commands.

| Permission | Description | Default |
| :--- | :--- | :--- |
| `tweakskompass.admin` | Allows reloading the plugin configuration and bypassing progression locks on commands. | Operators (`OP`) |

---

## Commands

| Command | Description | Access |
| :--- | :--- | :--- |
| `/tweakskompass` | Displays the plugin version and a list of available subcommands. | Everyone |
| `/tweakskompass reload` | Reloads all module settings, custom foods, multipliers, and configurations live. | `tweakskompass.admin` / OP |
| `/tweakskompass surfacespawn [toggle\|on\|off]` | Toggles personal surface hostile mob suppression around the player. Choices persist per player. | Players who have slain the Ender Dragon (or OP) |

---

## Features

### ⚔️ Combat & Equipment
* **Shield Combat Overhaul:** Eliminates the vanilla 0.25-second shield raise delay for instant blocking across a 180° frontal arc, restores proper shield-stun behavior, and features configurable axe disable durations.
* **Upward Knockback:** Reworks combat knockback to launch targets upward rather than purely pushing them away—scaling cleanly with sprinting, critical hits, and Knockback/Punch enchantments while still respecting frontal shield blocks.
* **Trident Combat & Smart Retrieval:**
  * Extends Trident melee reach (4.5 blocks) and balances attack speed (1.3).
  * **All-Weather Riptide:** Allows using Riptide on dry land with a 5-second cooldown (0-second cooldown in water or rain).
  * **Melee Channeling:** Summons lightning on melee hits with any Channeling item in any weather, while protecting killed mob drops from lightning fire.
  * **Smart Retrieval & Void Recovery:** Returned tridents go straight back into the exact hand (main or off-hand) they were thrown from, Loyalty tridents recover automatically from the void, portals will not swallow thrown tridents, and Sharpness, Smite, Bane of Arthropods, and Looting can be applied in an anvil.
* **Spear Combat:** Synchronizes Spear attack speed and cooldowns with the Lunge enchantment animation.
* **Wind Charge & Wind Burst Momentum:** Neutralizes Blast Protection's explosion knockback dampening so players wearing full Blast Protection Netherite armor retain full vanilla launch height from Wind Charges and Mace Wind Burst smashes.
* **Anvil Overhaul:** Removes the vanilla "Too Expensive!" 40-level cap, allowing raw material repairs, high-penalty renaming, and high-level enchantment merging while still respecting vanilla enchantment conflict rules.
* **Armor Trim Overhaul:**
  * Gold-trimmed armor pacifies Piglins just like wearing gold gear.
  * Wearing 4 differently trimmed pieces grants a Mixed Set bonus (Absorption II).
  * Wearing a full matching set of any of the **18 armor trim patterns** grants pattern-specific status effects and thematic ambient particles (with any pattern configurable to grant no effect if desired).
* **Custom Armor Durability:** Customizable multiplier and flat damage modifier to scale armor wear (e.g., making armor last 5× longer), with reduced wear from Thorns.
* **Copper Armor Hazards:** Wearing Copper Armor during thunderstorms carries a storm-strike risk per piece worn when exposed to the sky, accompanied by ambient electrical spark particles.

### 🧠 Mob AI, Factions & Spawning
* **Mythic-Style Threat Tables:** Mobs track damage-based threat per attacker rather than erratically switching targets. Attackers must exceed the current target's threat by a configurable threshold (default 110%) to pull aggro, with combat timeout decay and a configurable mob blacklist (such as the Ender Dragon and Warden).
* **Dragon Surface Mob Suppression:** Defeating the Ender Dragon unlocks a personal, toggleable perk that stops hostile mobs (including open-water Drowned) from spawning on the Overworld surface around you at night. Underground caves, dark solid-roofed mob farms, Raider patrols, Skeleton Horse traps, and structure spawns (Ocean Monuments, Pillager Outposts, Woodland Mansions, Witch Huts) continue operating normally, while the Nether and The End remain untouched.
* **Hostile End Phantoms:** Stops Phantoms from spawning in the Overworld and moves natural Phantom spawning to The End (capped at 8 per chunk). End Phantoms are automatically hostile to nearby players and feature smart movement logic to avoid getting stuck in Chorus Fruit trees.
* **End City Shulker Respawning:** Endermen naturally spawning on Purpur inside End Cities have a configurable chance to spawn as a new Shulker if no other Shulkers are nearby.
* **Mob Equipment, Stats & Combat Tweaks:**
  * **Evokers:** Spawn holding a Totem of Undying in their offhand and actively pop it to survive a lethal hit in combat (without double-dropping totems on death).
  * **Skeleton Variants:** Skeletons, Strays, Bogged, and Parched spawn with Shields in their offhand and can actively deflect frontal attacks—while hitting them with an Axe breaks and temporarily disables their guard.
  * **Stat Balancing:** Silverfish and Vexes have 2.0 HP (1 heart); Creepers move at 0.22 speed; standard Iron Golems are buffed to 150.0 HP and 0.30 speed (automatically sparing boss golems such as 800 HP Dreadnought Golems).
  * **Illusioner Nerf:** Replaces the 20-second Blindness spell with 3 seconds of Blindness followed by 10 seconds of Darkness.
* **Faction AI & Friendly Fire:**
  * **Unified Golem Faction:** Iron Golems, Snow Golems, and Copper Golems form a shared faction and never target, damage, or hit each other with snowballs. Iron Golems prioritize Zombies and Raiders before standard threats (including Creepers) while ignoring Zombie Villagers; Snow Golems prioritize Creepers.
  * **Mob Rivalries & Fears:** Raiders avoid Giants and Wardens and battle Zombies and Piglins; Zombies hunt Raiders and Witches; Witches invert potions to throw Splash Strong Healing at Zombies; Giants gain functional melee AI; Creepers flee from Iron Golems.
  * **Friendly Fire Prevention:** Skeleton variants never target or damage other skeletons; Illagers and Witches never target or damage fellow raiders.
* **Environmental & World Mob Rules:**
  * Mobs drop natural XP when killed by Wither Roses, Magma blocks, entity cramming, lava, fall damage, pointed dripstone/stalagmites, stonecutters, or Iron Golems.
  * Undead mobs and Phantoms do not burn in daylight (and skeletons never flee into shade or water).
  * Piglins and Hoglins do not zombify in the Overworld.
  * Armor stands naturally spawn with arms.
  * Farmland cannot be trampled into dirt, and Turtle Eggs cannot be broken by walking entities (while mobs still pathfind to them).
  * Axolotls ignore passive aquatic mobs (Fish, Squid, Glow Squid, Tadpoles) and can be bred using raw Tropical Fish items.

### 🧭 World & Traversal
* **The End Void Teleportation:** Falling into The End's void rescues the player (and any ridden mount/vehicle), dropping them from the Overworld sky at `Y = 512` at their exact `X` and `Z` coordinates with a custom message, sound effect, one-time landing fall-damage immunity, and optional Slow Falling.
* **Better Elytras:** Adds a configurable flight speed cap, live color-coded action-bar speedometer, mid-air Wind Charge boosts, ground-charged Wind Charge launches, and aerial TNT bombing with inherited flight inertia.
* **Better Boats:** Spacebar boat jumping on water or ground, Frost Walker water freezing underneath boats, and smooth buoyancy control that allows climbing upstream and water rapids.
* **Minecart Speed & Track Fixes:** Configurable maximum minecart speed (automatically detects Purpur servers and syncs Purpur's configuration alongside live carts), plus corner speed regulation and slope lookahead to stop fast minecarts from getting stuck on sharp turns or bouncing backward on inclines.
* **Better Ladders:** Right-click ladders with an empty hand to quick-climb to the top or bottom, sneak right-click with ladders to chain-place downward, and keep floating ladders intact without support blocks.

### 🛠️ Crafting, Tools & Blocks
* **Crafting & Smelting Expansion:**
  * Smelt Raw Iron, Copper, and Gold Blocks directly into metal blocks in Furnaces and Blast Furnaces.
  * Blast Sand and Red Sand into Glass at 2× speed in Blast Furnaces.
  * Re-dye already-colored Concrete, Concrete Powder, and Stained Terracotta (8 surrounding any dye).
  * Shapeless 1:1 crafting conversion from Cobblestone to Gravel, and Gravel to Sand.
* **Tool & Silk Touch Tweaks:**
  * Harvest Budding Amethyst with Silk Touch Pickaxes, and harvest Reinforced Deepslate exclusively with Silk Touch Netherite Pickaxes.
  * Pickaxes mine Glass, Panes, Light Sources (Glowstone, Sea Lanterns, Redstone Lamps, Froglights, Lanterns, Shroomlights, Campfires), and Cacti effectively.
  * Hoes mine Cacti and Froglights effectively; Axes mine mob and player Skulls effectively.
* **Shears Looting:** Enchant Shears with Looting I–III in an anvil for bonus wool and mushroom drops when shearing Sheep and Mooshrooms.
* **Fast Leaf Decay:** Chopping tree logs triggers rapid, cascading leaf decay across canopies with particles, sounds, and natural sapling/apple/stick drops.
* **Chainmail Sieve:** Wearing a Chainmail Helmet breaks falling gravity blocks (Sand, Gravel, Suspicious Sand/Gravel, and Snow) on head impact, sifting drops with particles and sounds.
* **Copper Oxidation Bottles:** Right-click copper blocks with Water Bottles—or throw Splash Water Bottles—to advance oxidation stages in an area while keeping doors and block states synchronized.
* **Snow Physics & Snowballs:** Unsupported snow layers fall and stack on impact; thrown snowballs add layers to blocks and convert 8 layers into full Snow Blocks.
* **Painting Tweaks:** Unlocks the rare 2×2 elemental paintings (*Earth*, *Wind*, *Fire*, *Water*) into survival placement pools and allows sneak right-clicking existing paintings with a Painting item to cycle variants of the same size.
* **Shulker Dyeing:** Bedrock Edition parity allowing players and dispensers to dye Shulker mobs in all 16 colors, or wash them back to default using a Water Bottle.

### 🍎 Survival, Food & Storage
* **Custom Food System:** Configure nutrition, saturation, cooldowns (Global, Personal, or Disabled), instant-eat mode, potion effects, and commands (with PlaceholderAPI support) for any vanilla or non-vanilla item—including placed Cakes and Candle Cakes per slice.
* **Always-Eat:** Allows eating any food item—and placed Cake slices—even at a full 20/20 hunger bar without altering item stacking.
* **Better Invisibility:** Hides armor (and optionally held items) from other players while preserving third-person view for yourself, strips potion swirl particles, and shrinks hostile mob detection range to close quarters.
* **Custom Stack Sizes & Bundles:** Stack Potions (8), Soups/Stews (16), Cakes (16), and Totems of Undying (4), and configure multi-stack Bundle capacities.
* **Inventory Totem of Undying:** Activates Totems of Undying from any direct slot in the player's inventory without needing to hold them in a hand.
* **Magical Campfire:** Campfires and Soul Campfires passively heal nearby players within range with configurable amounts and action-bar feedback.

### 🐾 Pets, Leashes & Villagers
* **Consolidated Pet Tweaks:**
  * **Enchantable Pet Armor:** Apply Protection, Fire/Blast/Projectile Protection, Thorns, Unbreaking, and Mending onto Wolf Armor, Horse Armor, and Nautilus Armor in an anvil.
  * **Companion Teleportation:** Automatically rescues and teleports following companion pets (Wolves, Cats, and Parrots—leaving mountable horses, ghasts, and nautiluses where parked) across distances, unloading chunks, and dimensions.
  * **Low-Health Survival Retreat:** Wounded companion pets below a configurable health threshold drop combat, refuse to attack, whimper, alert their owner, and retreat behind their owner until healed.
* **Lead Any Mob:** Attach leads to any living mob, bind multiple leashed mobs to a single fence post, right-click to untie, and travel through portals, teleports, and Elytra boosts without snapping or duplicating leads.
* **Villager Instant Restock:** Trades restock instantly upon opening the trade menu and mid-trading session (including Wandering Traders), with optional demand price inflation clearing.
* **Turtle Scute Brushing:** Brush adult turtles with a Brush to collect Turtle Scutes with durability wear, particles, and sound effects.
