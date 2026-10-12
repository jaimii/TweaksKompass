package project.kompass.tweakskompass.listener

import project.kompass.tweakskompass.TweaksKompass
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.data.Bisected
import org.bukkit.block.data.type.Door
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.PotionSplashEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.PotionMeta
import org.bukkit.potion.PotionType
import java.util.EnumMap
import java.util.HashSet

class CopperOxidationListener(private val plugin: TweaksKompass) : Listener {

    companion object {
        // High-performance O(1) lookup table for the next oxidation transition
        private val NEXT_OXIDATION_STAGE: MutableMap<Material, Material> = EnumMap(Material::class.java)

        init {
            fun registerChain(stage0: Material, stage1: Material, stage2: Material, stage3: Material) {
                NEXT_OXIDATION_STAGE[stage0] = stage1
                NEXT_OXIDATION_STAGE[stage1] = stage2
                NEXT_OXIDATION_STAGE[stage2] = stage3
            }

            // Standard Copper & Cut Copper
            registerChain(Material.COPPER_BLOCK, Material.EXPOSED_COPPER, Material.WEATHERED_COPPER, Material.OXIDIZED_COPPER)
            registerChain(Material.CUT_COPPER, Material.EXPOSED_CUT_COPPER, Material.WEATHERED_CUT_COPPER, Material.OXIDIZED_CUT_COPPER)
            registerChain(Material.CUT_COPPER_STAIRS, Material.EXPOSED_CUT_COPPER_STAIRS, Material.WEATHERED_CUT_COPPER_STAIRS, Material.OXIDIZED_CUT_COPPER_STAIRS)
            registerChain(Material.CUT_COPPER_SLAB, Material.EXPOSED_CUT_COPPER_SLAB, Material.WEATHERED_CUT_COPPER_SLAB, Material.OXIDIZED_CUT_COPPER_SLAB)

            // Chiseled, Grates, Bulbs, Doors & Trapdoors
            registerChain(Material.CHISELED_COPPER, Material.EXPOSED_CHISELED_COPPER, Material.WEATHERED_CHISELED_COPPER, Material.OXIDIZED_CHISELED_COPPER)
            registerChain(Material.COPPER_GRATE, Material.EXPOSED_COPPER_GRATE, Material.WEATHERED_COPPER_GRATE, Material.OXIDIZED_COPPER_GRATE)
            registerChain(Material.COPPER_BULB, Material.EXPOSED_COPPER_BULB, Material.WEATHERED_COPPER_BULB, Material.OXIDIZED_COPPER_BULB)
            registerChain(Material.COPPER_DOOR, Material.EXPOSED_COPPER_DOOR, Material.WEATHERED_COPPER_DOOR, Material.OXIDIZED_COPPER_DOOR)
            registerChain(Material.COPPER_TRAPDOOR, Material.EXPOSED_COPPER_TRAPDOOR, Material.WEATHERED_COPPER_TRAPDOOR, Material.OXIDIZED_COPPER_TRAPDOOR)
        }
    }

    private fun isWaterBottle(item: ItemStack?, expectedType: Material): Boolean {
        if (item == null || item.type != expectedType) return false
        val meta = item.itemMeta as? PotionMeta ?: return false
        return meta.basePotionType == PotionType.WATER
    }

    // Advances oxidation stage while preserving all block properties
    private fun advanceOxidation(block: Block, visitedBlocks: MutableSet<Block>? = null): Boolean {
        val currentType = block.type

        // Fast O(1) table lookup automatically returns null for waxed blocks (no string allocation needed)
        val nextType = NEXT_OXIDATION_STAGE[currentType] ?: return false

        val oldData = block.blockData
        val newString = oldData.asString.replace(currentType.key.toString(), nextType.key.toString())
        val newData = runCatching { Bukkit.createBlockData(newString) }.getOrElse { nextType.createBlockData() }

        block.setBlockData(newData, true)
        visitedBlocks?.add(block)

        // If block is a door, synchronize the opposite half safely without double-triggering
        if (oldData is Door) {
            val otherHalfBlock = if (oldData.half == Bisected.Half.BOTTOM) {
                block.getRelative(BlockFace.UP)
            } else {
                block.getRelative(BlockFace.DOWN)
            }

            if (otherHalfBlock.type == currentType) {
                val otherOldData = otherHalfBlock.blockData
                val otherNewString = otherOldData.asString.replace(currentType.key.toString(), nextType.key.toString())
                val otherNewData = runCatching { Bukkit.createBlockData(otherNewString) }.getOrElse { nextType.createBlockData() }
                otherHalfBlock.setBlockData(otherNewData, true)
                visitedBlocks?.add(otherHalfBlock) // Prevent double-oxidation of this door half in splash AOE
            }
        }

        return true
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onPlayerWaterBottle(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_BLOCK) return
        val hand = event.hand ?: return
        if (!plugin.config.getBoolean("copper-oxidation.enabled", true)) return

        val block = event.clickedBlock ?: return
        if (!NEXT_OXIDATION_STAGE.containsKey(block.type)) return

        val player = event.player
        val item = player.inventory.getItem(hand)

        if (!isWaterBottle(item, Material.POTION)) return

        event.isCancelled = true

        if (advanceOxidation(block)) {
            val loc = block.location.add(0.5, 0.5, 0.5)

            if (plugin.config.getBoolean("copper-oxidation.effects", true)) {
                block.world.playSound(loc, Sound.ITEM_BOTTLE_EMPTY, 1.0f, 1.0f)
                block.world.playSound(loc, Sound.BLOCK_COPPER_PLACE, 1.0f, 0.8f)
                block.world.spawnParticle(Particle.SPLASH, loc, 15, 0.3, 0.3, 0.3, 0.1)
            }

            if (player.gameMode != GameMode.CREATIVE) {
                if (item.amount == 1) {
                    player.inventory.setItem(hand, ItemStack(Material.GLASS_BOTTLE))
                } else {
                    item.amount -= 1
                    val leftovers = player.inventory.addItem(ItemStack(Material.GLASS_BOTTLE))
                    for (drop in leftovers.values) {
                        player.world.dropItemNaturally(player.location, drop)
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onSplashWaterBottle(event: PotionSplashEvent) {
        if (!plugin.config.getBoolean("copper-oxidation.enabled", true)) return

        val item = event.potion.item
        if (!isWaterBottle(item, Material.SPLASH_POTION)) return

        val centerLoc = event.potion.location
        val world = centerLoc.world
        val radius = plugin.config.getDouble("copper-oxidation.splash-radius", 3.5)
        val radiusSq = radius * radius
        val rCeil = radius.toInt()

        val centerX = centerLoc.blockX
        val centerY = centerLoc.blockY
        val centerZ = centerLoc.blockZ

        val visitedBlocks = HashSet<Block>()
        var oxidizedAny = false

        for (dx in -rCeil..rCeil) {
            for (dy in -rCeil..rCeil) {
                for (dz in -rCeil..rCeil) {
                    if (dx * dx + dy * dy + dz * dz > radiusSq) continue

                    val targetBlock = world.getBlockAt(centerX + dx, centerY + dy, centerZ + dz)

                    // Skip already-modified blocks (prevents door halves from double-oxidizing)
                    if (visitedBlocks.contains(targetBlock)) continue

                    if (NEXT_OXIDATION_STAGE.containsKey(targetBlock.type)) {
                        if (advanceOxidation(targetBlock, visitedBlocks)) {
                            oxidizedAny = true
                        }
                    }
                }
            }
        }

        if (oxidizedAny && plugin.config.getBoolean("copper-oxidation.effects", true)) {
            world.playSound(centerLoc, Sound.ITEM_BOTTLE_EMPTY, 1.0f, 0.8f)
            world.playSound(centerLoc, Sound.BLOCK_COPPER_PLACE, 1.0f, 0.9f)
            world.spawnParticle(Particle.SPLASH, centerLoc, 35, 1.0, 1.0, 1.0, 0.1)
        }
    }
}