package project.kompass.tweakskompass.entity

import org.bukkit.Bukkit
import org.bukkit.DyeColor
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.block.Dispenser
import org.bukkit.block.data.Directional
import org.bukkit.entity.Shulker
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockDispenseEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.PotionMeta
import org.bukkit.potion.PotionType
import project.kompass.tweakskompass.TweaksKompass
import java.util.EnumMap

class ShulkerColorListener(private val plugin: TweaksKompass) : Listener {

    companion object {
        private val DYE_MAP: Map<Material, DyeColor> = EnumMap<Material, DyeColor>(Material::class.java).apply {
            for (color in DyeColor.entries) {
                val mat = Material.getMaterial("${color.name}_DYE")
                if (mat != null) {
                    put(mat, color)
                }
            }
        }
    }

    private fun isFeatureEnabled(): Boolean {
        return plugin.config.getBoolean("shulker-colors.enabled", true)
    }

    // 1. Player Dyeing & Washing Interaction
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onPlayerInteractShulker(event: PlayerInteractEntityEvent) {
        if (!isFeatureEnabled()) return
        val player = event.player
        if (player.gameMode == GameMode.SPECTATOR) return

        val shulker = event.rightClicked as? Shulker ?: return
        val hand = event.hand
        val item = player.inventory.getItem(hand)
        if (item.type.isAir) return

        val dyeColor = DYE_MAP[item.type]

        // A. Dyeing Shulker
        if (dyeColor != null) {
            if (shulker.color == dyeColor) return

            event.isCancelled = true
            shulker.color = dyeColor

            playDyeEffects(shulker, item.type)
            player.swingHand(hand)

            if (player.gameMode != GameMode.CREATIVE) {
                if (item.amount <= 1) {
                    player.inventory.setItem(hand, null)
                } else {
                    item.amount -= 1
                    player.inventory.setItem(hand, item)
                }
            }
            return
        }

        // B. Washing Shulker (Water Bottle)
        if (plugin.config.getBoolean("shulker-colors.allow-wash", true)) {
            if (item.type == Material.POTION && shulker.color != null) {
                val meta = item.itemMeta as? PotionMeta ?: return
                if (meta.basePotionType == PotionType.WATER) {
                    event.isCancelled = true
                    shulker.color = null

                    val loc = shulker.location.add(0.0, 0.5, 0.0)
                    shulker.world.playSound(loc, Sound.ITEM_BOTTLE_EMPTY, 1.0f, 1.0f)
                    shulker.world.spawnParticle(Particle.SPLASH, loc, 15, 0.3, 0.3, 0.3, 0.1)
                    player.swingHand(hand)

                    if (player.gameMode != GameMode.CREATIVE) {
                        if (item.amount <= 1) {
                            player.inventory.setItem(hand, ItemStack(Material.GLASS_BOTTLE))
                        } else {
                            item.amount -= 1
                            player.inventory.setItem(hand, item)
                            val leftover = player.inventory.addItem(ItemStack(Material.GLASS_BOTTLE))
                            for (drop in leftover.values) {
                                player.world.dropItemNaturally(player.location, drop)
                            }
                        }
                    }
                }
            }
        }
    }

    // 2. Dispenser Dyeing
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onDispenserDyeShulker(event: BlockDispenseEvent) {
        if (!isFeatureEnabled()) return
        if (!plugin.config.getBoolean("shulker-colors.dispenser-dyeing", true)) return
        if (event.block.type != Material.DISPENSER) return

        val dyeItem = event.item
        val dyeColor = DYE_MAP[dyeItem.type] ?: return

        val block = event.block
        val directional = block.blockData as? Directional ?: return
        val targetBlock = block.getRelative(directional.facing)

        val center = targetBlock.location.add(0.5, 0.5, 0.5)
        val shulker = targetBlock.world.getNearbyEntities(center, 0.6, 0.6, 0.6)
            .filterIsInstance<Shulker>()
            .firstOrNull() ?: return

        if (shulker.color == dyeColor) return

        event.isCancelled = true
        shulker.color = dyeColor
        playDyeEffects(shulker, dyeItem.type)

        Bukkit.getScheduler().runTask(plugin, Runnable {
            val dispenserState = block.state as? Dispenser ?: return@Runnable
            val inv = dispenserState.inventory
            for (slot in 0 until inv.size) {
                val stack = inv.getItem(slot) ?: continue
                if (stack.type == dyeItem.type) {
                    if (stack.amount <= 1) {
                        inv.setItem(slot, null)
                    } else {
                        stack.amount -= 1
                        inv.setItem(slot, stack)
                    }
                    break
                }
            }
        })
    }

    private fun playDyeEffects(shulker: Shulker, dyeMaterial: Material) {
        val loc = shulker.location.add(0.0, 0.5, 0.0)
        val world = shulker.world

        if (plugin.config.getBoolean("shulker-colors.play-sound", true)) {
            world.playSound(loc, Sound.ITEM_DYE_USE, SoundCategory.PLAYERS, 1.0f, 1.0f)
        }

        if (plugin.config.getBoolean("shulker-colors.spawn-particles", true)) {
            world.spawnParticle(
                Particle.ITEM,
                loc,
                15,
                0.25, 0.25, 0.25,
                0.05,
                ItemStack(dyeMaterial)
            )
        }
    }
}