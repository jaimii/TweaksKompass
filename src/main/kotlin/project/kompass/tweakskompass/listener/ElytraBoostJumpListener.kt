package project.kompass.tweakskompass.listener

import project.kompass.tweakskompass.TweaksKompass
import org.bukkit.*
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.Vector
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class ElytraBoostJumpListener(private val plugin: TweaksKompass) : Listener {

    private val lastInteractionTick = ConcurrentHashMap<UUID, Int>()
    private val activeCharges = ConcurrentHashMap<UUID, BukkitRunnable>()

    private var windChargeBoostEnabled = true
    private var windChargeSound = "entity.wind_charge.wind_burst"
    private var windChargeParticle = "gust_emitter_large"
    private var windChargeMultiplier = 1.2

    private var chargedJumpEnabled = true
    private var verticalForce = 1.8
    private var horizontalForce = 0.3
    private var jumpSound = "entity.generic.explode"
    private var jumpSecondarySound = "entity.wind_charge.wind_burst"

    init {
        updateValues()
    }

    fun updateValues() {
        windChargeBoostEnabled = plugin.config.getBoolean("better-elytras.wind-charge-boost.enabled", true)
        windChargeSound = plugin.config.getString("better-elytras.wind-charge-boost.sound", "entity.wind_charge.wind_burst") ?: "entity.wind_charge.wind_burst"
        windChargeParticle = plugin.config.getString("better-elytras.wind-charge-boost.particle", "gust_emitter_large") ?: "gust_emitter_large"
        windChargeMultiplier = plugin.config.getDouble("better-elytras.wind-charge-boost.multiplier", 1.2)

        chargedJumpEnabled = plugin.config.getBoolean("better-elytras.charged-jump.enabled", true)
        verticalForce = plugin.config.getDouble("better-elytras.charged-jump.vertical-force", 1.8)
        horizontalForce = plugin.config.getDouble("better-elytras.charged-jump.horizontal-force", 0.3)
        jumpSound = plugin.config.getString("better-elytras.charged-jump.sound", "entity.generic.explode") ?: "entity.generic.explode"
        jumpSecondarySound = plugin.config.getString("better-elytras.charged-jump.secondary-sound", "entity.wind_charge.wind_burst") ?: "entity.wind_charge.wind_burst"
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val action = event.action
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return

        val item = event.item ?: return
        val itemType = item.type
        val isWindCharge = itemType == Material.WIND_CHARGE
        val isFirework = itemType == Material.FIREWORK_ROCKET

        if (!isWindCharge && !isFirework) return

        val player = event.player
        val chestplate = player.inventory.chestplate
        if (chestplate == null || chestplate.type != Material.ELYTRA) return

        if (event.hand == EquipmentSlot.OFF_HAND) {
            val mainType = player.inventory.itemInMainHand.type
            if (mainType == Material.WIND_CHARGE || mainType == Material.FIREWORK_ROCKET) return
        }

        val uuid = player.uniqueId
        val currentTick = player.ticksLived

        // Prevent double execution inside the exact same game-tick
        if (lastInteractionTick[uuid] == currentTick) return
        lastInteractionTick[uuid] = currentTick

        // --- FEATURE 1: Wind Charge Boost (While Gliding) ---
        if (player.isGliding) {
            if (isWindCharge && windChargeBoostEnabled) {
                event.isCancelled = true

                if (player.gameMode != GameMode.CREATIVE) {
                    item.subtract(1)
                }

                val lookDir = player.location.direction
                val currentVelocity = player.velocity
                var newVelocity = currentVelocity.add(lookDir.multiply(windChargeMultiplier))

                // Speed Capping
                val maxSpeedBps = plugin.config.getDouble("better-elytras.max-speed", 75.0)
                val maxSpeedPerTick = maxSpeedBps / 20.0
                if (newVelocity.lengthSquared() > maxSpeedPerTick * maxSpeedPerTick) {
                    newVelocity = newVelocity.normalize().multiply(maxSpeedPerTick)
                }

                player.velocity = newVelocity

                val loc = player.location
                playSound(player, loc, windChargeSound, 1.0f, 1.0f)
                spawnParticle(player, loc, windChargeParticle, 5)
            }
            return
        }

        // --- FEATURE 2: Charged Jump (Grounded / Not Gliding) ---
        if (isWindCharge && chargedJumpEnabled) {
            event.isCancelled = true
            if (activeCharges.containsKey(uuid)) return

            playSound(player, player.location, "entity.wind_charge.throw", 0.8f, 0.5f)

            val chargeTask = object : BukkitRunnable() {
                var ticks = 0

                override fun run() {
                    if (!player.isOnline) {
                        cancelCharge(uuid)
                        return
                    }

                    // Abort if item switched away from Wind Charge
                    val mainItem = player.inventory.itemInMainHand
                    val offItem = player.inventory.itemInOffHand
                    if (mainItem.type != Material.WIND_CHARGE && offItem.type != Material.WIND_CHARGE) {
                        cancelCharge(uuid)
                        return
                    }

                    val loc = player.location
                    player.world.spawnParticle(Particle.GUST, loc.add(0.0, 0.2, 0.0), 3, 0.2, 0.05, 0.2, 0.01)

                    ticks += 2
                    if (ticks >= 20) { // 1.0s charge completed
                        executeLaunch(player)
                        cancelCharge(uuid)
                    }
                }
            }

            chargeTask.runTaskTimer(plugin, 0L, 2L)
            activeCharges[uuid] = chargeTask
        }
    }

    private fun executeLaunch(player: Player) {
        var item = player.inventory.itemInMainHand
        if (item.type != Material.WIND_CHARGE) {
            item = player.inventory.itemInOffHand
            if (item.type != Material.WIND_CHARGE) return
        }

        if (player.gameMode != GameMode.CREATIVE) {
            item.subtract(1)
        }

        val loc = player.location
        playSound(player, loc, jumpSound, 1.0f, 0.8f)
        playSound(player, loc, jumpSecondarySound, 1.0f, 1.0f)

        player.world.spawnParticle(Particle.EXPLOSION_EMITTER, loc, 2)
        player.world.spawnParticle(Particle.GUST_EMITTER_LARGE, loc, 5)

        val direction = player.location.direction.setY(0.0)
        if (direction.lengthSquared() > 0) {
            direction.normalize().multiply(horizontalForce)
        }

        val launchVelocity = Vector(direction.x, verticalForce, direction.z)
        player.velocity = launchVelocity

        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            if (player.isOnline) {
                player.isGliding = true
            }
        }, 2L)
    }

    private fun cancelCharge(uuid: UUID) {
        val task = activeCharges.remove(uuid)
        task?.cancel()
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val uuid = event.player.uniqueId
        cancelCharge(uuid)
        lastInteractionTick.remove(uuid)
    }

    private fun playSound(player: Player, loc: Location, soundKey: String, volume: Float, pitch: Float) {
        try {
            player.playSound(loc, soundKey, org.bukkit.SoundCategory.PLAYERS, volume, pitch)
        } catch (t: Throwable) {
            player.playSound(loc, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f)
        }
    }

    private fun spawnParticle(player: Player, loc: Location, particleKey: String, count: Int) {
        try {
            val particle = Particle.valueOf(particleKey.uppercase())
            player.world.spawnParticle(particle, loc, count)
        } catch (e: IllegalArgumentException) {
            player.world.spawnParticle(Particle.GUST_EMITTER_LARGE, loc, count)
        }
    }
}