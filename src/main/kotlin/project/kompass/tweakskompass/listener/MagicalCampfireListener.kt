package project.kompass.tweakskompass.listener

import project.kompass.tweakskompass.TweaksKompass
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.attribute.Attribute
import org.bukkit.block.data.type.Campfire
import org.bukkit.entity.Player
import org.bukkit.event.Listener
import org.bukkit.scheduler.BukkitRunnable

class MagicalCampfireListener(private val plugin: TweaksKompass) : Listener {

    private val miniMessage = MiniMessage.miniMessage()

    fun startCampfireTask() {
        object : BukkitRunnable() {
            override fun run() {
                val enabled = plugin.config.getBoolean("magical-campfire.enabled", true)
                if (!enabled) return

                val campfireEnabled = plugin.config.getBoolean("magical-campfire.campfire.enabled", true)
                val soulCampfireEnabled = plugin.config.getBoolean("magical-campfire.soul-campfire.enabled", true)

                if (!campfireEnabled && !soulCampfireEnabled) return

                for (player in plugin.server.onlinePlayers) {
                    if (player.isDead || player.gameMode == GameMode.SPECTATOR) continue

                    val maxHealth = player.getAttribute(Attribute.MAX_HEALTH)?.value ?: 20.0

                    var processed = false
                    if (campfireEnabled) {
                        processed = handleCampfire(player, Material.CAMPFIRE, "campfire", maxHealth)
                    }
                    if (!processed && soulCampfireEnabled) {
                        handleCampfire(player, Material.SOUL_CAMPFIRE, "soul-campfire", maxHealth)
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L) // Runs every 1 second (20 ticks) for responsive feedback
    }

    private fun handleCampfire(player: Player, targetMaterial: Material, configKey: String, maxHealth: Double): Boolean {
        val path = "magical-campfire.$configKey"

        // Fallbacks support require-lit, is_lit, or is-lit config keys
        val requireLit = plugin.config.getBoolean("$path.require-lit",
            plugin.config.getBoolean("$path.is_lit",
                plugin.config.getBoolean("$path.is-lit", true)))

        val range = plugin.config.getInt("$path.range", 3).coerceIn(1, 10)
        val healAmount = plugin.config.getDouble("$path.amount", 1.0)

        val actionbarEnabled = plugin.config.getBoolean("$path.actionbar.enabled", true)
        val actionbarMessage = plugin.config.getString("$path.actionbar.message", "<green>You feel the warmth healing you.")

        val world = player.world
        val pLoc = player.location
        val centerX = pLoc.blockX
        val centerY = pLoc.blockY
        val centerZ = pLoc.blockZ

        var inRange = false

        // Standard world block queries
        scanLoop@ for (x in -range..range) {
            for (y in -range..range) {
                for (z in -range..range) {
                    val block = world.getBlockAt(centerX + x, centerY + y, centerZ + z)
                    if (block.type == targetMaterial) {
                        if (!requireLit) {
                            inRange = true
                            break@scanLoop
                        }
                        val data = block.blockData as? Campfire
                        if (data != null && data.isLit) {
                            inRange = true
                            break@scanLoop
                        }
                    }
                }
            }
        }

        if (inRange) {
            // Apply healing if player is damaged
            if (player.health < maxHealth) {
                val newHealth = minOf(player.health + healAmount, maxHealth)
                player.health = newHealth
            }

            // Always display actionbar feedback when near campfire
            if (actionbarEnabled && !actionbarMessage.isNullOrEmpty()) {
                player.sendActionBar(miniMessage.deserialize(actionbarMessage))
            }
            return true
        }

        return false
    }
}