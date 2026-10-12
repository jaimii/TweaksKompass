package project.kompass.tweakskompass.listener

import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.attribute.Attribute
import org.bukkit.block.data.type.Campfire
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.scheduler.BukkitRunnable
import project.kompass.tweakskompass.TweaksKompass
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class MagicalCampfireListener(private val plugin: TweaksKompass) : Listener {

    private val miniMessage = MiniMessage.miniMessage()

    private data class PlayerCampfireState(
        val blockX: Int,
        val blockY: Int,
        val blockZ: Int,
        val matchedCampfire: Boolean,
        val matchedSoulCampfire: Boolean
    )

    // Standing-still cache avoids 343 getBlockAt calls per second per stationary player
    private val playerStates = ConcurrentHashMap<UUID, PlayerCampfireState>()

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
                    scanAndHeal(player, campfireEnabled, soulCampfireEnabled, maxHealth)
                }
            }
        }.runTaskTimer(plugin, 20L, 20L)
    }

    private fun scanAndHeal(player: Player, campfireEnabled: Boolean, soulCampfireEnabled: Boolean, maxHealth: Double) {
        val pLoc = player.location
        val cX = pLoc.blockX
        val cY = pLoc.blockY
        val cZ = pLoc.blockZ
        val uuid = player.uniqueId

        val cached = playerStates[uuid]

        // Fast path: If player hasn't moved block coordinates, reuse the scan result!
        val (matchedCampfire, matchedSoulCampfire) = if (cached != null && cached.blockX == cX && cached.blockY == cY && cached.blockZ == cZ) {
            Pair(cached.matchedCampfire, cached.matchedSoulCampfire)
        } else {
            val scanResult = performScan(player, cX, cY, cZ, campfireEnabled, soulCampfireEnabled)
            playerStates[uuid] = PlayerCampfireState(cX, cY, cZ, scanResult.first, scanResult.second)
            scanResult
        }

        if (matchedCampfire) {
            applyHeal(player, "campfire", maxHealth)
        } else if (matchedSoulCampfire) {
            applyHeal(player, "soul-campfire", maxHealth)
        }
    }

    private fun performScan(
        player: Player,
        cX: Int, cY: Int, cZ: Int,
        campfireEnabled: Boolean,
        soulCampfireEnabled: Boolean
    ): Pair<Boolean, Boolean> {
        val range = maxOf(
            plugin.config.getInt("magical-campfire.campfire.range", 3),
            plugin.config.getInt("magical-campfire.soul-campfire.range", 3)
        ).coerceIn(1, 10)

        val world = player.world
        var matchedCampfire = false
        var matchedSoulCampfire = false

        scanLoop@ for (x in -range..range) {
            for (y in -range..range) {
                for (z in -range..range) {
                    val block = world.getBlockAt(cX + x, cY + y, cZ + z)
                    val type = block.type

                    if (campfireEnabled && type == Material.CAMPFIRE) {
                        val data = block.blockData as? Campfire
                        if (data == null || data.isLit) {
                            matchedCampfire = true
                            break@scanLoop
                        }
                    } else if (soulCampfireEnabled && type == Material.SOUL_CAMPFIRE) {
                        val data = block.blockData as? Campfire
                        if (data == null || data.isLit) {
                            matchedSoulCampfire = true
                            break@scanLoop
                        }
                    }
                }
            }
        }

        return Pair(matchedCampfire, matchedSoulCampfire)
    }

    private fun applyHeal(player: Player, configKey: String, maxHealth: Double) {
        val path = "magical-campfire.$configKey"
        val healAmount = plugin.config.getDouble("$path.amount", 1.0)
        val actionbarEnabled = plugin.config.getBoolean("$path.actionbar.enabled", true)
        val actionbarMessage = plugin.config.getString("$path.actionbar.message", "<green>You feel the warmth healing you.</green>")

        if (player.health < maxHealth) {
            player.health = minOf(player.health + healAmount, maxHealth)
        }

        if (actionbarEnabled && !actionbarMessage.isNullOrEmpty()) {
            player.sendActionBar(miniMessage.deserialize(actionbarMessage))
        }
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        playerStates.remove(event.player.uniqueId)
    }
}