package project.kompass.tweakskompass.listener

import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Sound
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import project.kompass.tweakskompass.TweaksKompass
import project.kompass.tweakskompass.event.AfterVoidTeleportEvent
import project.kompass.tweakskompass.event.VoidTeleportEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class VoidTeleportListener(private val plugin: TweaksKompass) : Listener {

    private val miniMessage = MiniMessage.miniMessage()
    private val teleportCooldown = ConcurrentHashMap<UUID, Long>()
    private val fallImmuneEntities = ConcurrentHashMap<UUID, Long>()

    // Pre-cached config values
    private var isEnabled = true
    private var voidThresholdY = 0.0
    private var fallHeight = 512.0
    private var targetWorldName = "DEFAULT_OVERWORLD"
    private var immuneUntilLanding = true
    private var teleportVehicle = true
    private var resetFallDistance = true
    private var slowFallingSeconds = 5
    private var soundName = "item.chorus_fruit.teleport"
    private var messageTemplate = "<gradient:#4facfe:#00f2fe>You plunge through the cosmic void, falling into the Overworld skies!</gradient>"

    init {
        reloadConfig()
    }

    fun reloadConfig() {
        val cfg = plugin.config
        isEnabled = cfg.getBoolean("void-teleport.enabled", true)
        voidThresholdY = cfg.getDouble("void-teleport.void-threshold-y", 0.0)
        fallHeight = cfg.getDouble("void-teleport.fall-height", 512.0)
        targetWorldName = cfg.getString("void-teleport.target-world", "DEFAULT_OVERWORLD") ?: "DEFAULT_OVERWORLD"
        immuneUntilLanding = cfg.getBoolean("void-teleport.immune-until-landing", true)
        teleportVehicle = cfg.getBoolean("void-teleport.teleport-vehicle", true)
        resetFallDistance = cfg.getBoolean("void-teleport.reset-fall-distance", true)
        slowFallingSeconds = cfg.getInt("void-teleport.slow-falling-seconds", 5)
        soundName = cfg.getString("void-teleport.sound", "item.chorus_fruit.teleport") ?: "item.chorus_fruit.teleport"
        messageTemplate = cfg.getString(
            "void-teleport.message",
            "<gradient:#4facfe:#00f2fe>You plunge through the cosmic void, falling into the Overworld skies!</gradient>"
        ) ?: ""
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onPlayerMove(event: PlayerMoveEvent) {
        if (!isEnabled) return
        if (!event.hasChangedPosition()) return

        val player = event.player
        if (player.world.environment != World.Environment.THE_END) return

        if (event.to.y <= voidThresholdY) {
            handleVoidFall(player)
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onVoidDamage(event: EntityDamageEvent) {
        if (!isEnabled) return
        if (event.cause != EntityDamageEvent.DamageCause.VOID) return

        val player = event.entity as? Player ?: return
        if (player.world.environment != World.Environment.THE_END) return

        event.isCancelled = true
        handleVoidFall(player)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onFallDamage(event: EntityDamageEvent) {
        if (event.cause != EntityDamageEvent.DamageCause.FALL) return

        val entity = event.entity
        val expireTime = fallImmuneEntities[entity.uniqueId] ?: return

        if (System.currentTimeMillis() <= expireTime) {
            event.isCancelled = true
        }

        fallImmuneEntities.remove(entity.uniqueId)
    }

    private fun handleVoidFall(player: Player) {
        val uuid = player.uniqueId
        val now = System.currentTimeMillis()
        val last = teleportCooldown[uuid] ?: 0L

        if (now - last < 3000L) return
        teleportCooldown[uuid] = now

        val overworld = resolveTargetWorld() ?: return
        val originLoc = player.location
        val destLoc = Location(
            overworld,
            originLoc.x,
            fallHeight,
            originLoc.z,
            originLoc.yaw,
            originLoc.pitch
        )

        val vehicle = player.vehicle

        val preEvent = VoidTeleportEvent(player, originLoc, destLoc, vehicle)
        Bukkit.getPluginManager().callEvent(preEvent)
        if (preEvent.isCancelled) return

        val finalDest = preEvent.destination

        if (vehicle != null && teleportVehicle) {
            vehicle.eject()
            val vehicleDest = finalDest.clone()

            vehicle.teleportAsync(vehicleDest).thenAccept { success ->
                if (success && vehicle.isValid) {
                    vehicle.fallDistance = 0f
                    if (immuneUntilLanding) {
                        fallImmuneEntities[vehicle.uniqueId] = System.currentTimeMillis() + 60_000L
                    }
                }
            }
        }

        player.teleportAsync(finalDest).thenAccept { success ->
            if (success && player.isOnline) {
                if (resetFallDistance) {
                    player.fallDistance = 0f
                }

                if (immuneUntilLanding) {
                    fallImmuneEntities[player.uniqueId] = System.currentTimeMillis() + 60_000L
                }

                if (slowFallingSeconds > 0) {
                    player.addPotionEffect(
                        PotionEffect(
                            PotionEffectType.SLOW_FALLING,
                            slowFallingSeconds * 20,
                            0,
                            false,
                            true,
                            true
                        )
                    )
                }

                if (soundName.isNotEmpty()) {
                    try {
                        player.playSound(finalDest, soundName, 1.0f, 1.0f)
                    } catch (ignored: Throwable) {
                        player.playSound(finalDest, Sound.ITEM_CHORUS_FRUIT_TELEPORT, 1.0f, 1.0f)
                    }
                }

                if (messageTemplate.isNotEmpty()) {
                    player.sendMessage(miniMessage.deserialize(messageTemplate))
                }

                Bukkit.getPluginManager().callEvent(AfterVoidTeleportEvent(player, originLoc, finalDest))
            }
        }
    }

    private fun resolveTargetWorld(): World? {
        if (!targetWorldName.equals("DEFAULT_OVERWORLD", ignoreCase = true)) {
            val explicit = Bukkit.getWorld(targetWorldName)
            if (explicit != null) return explicit
        }
        return Bukkit.getWorlds().firstOrNull { it.environment == World.Environment.NORMAL }
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        teleportCooldown.remove(event.player.uniqueId)
        fallImmuneEntities.remove(event.player.uniqueId)
    }
}