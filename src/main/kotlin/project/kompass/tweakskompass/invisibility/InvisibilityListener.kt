package project.kompass.tweakskompass.invisibility

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityPotionEffectEvent
import org.bukkit.event.entity.EntityTargetEvent
import org.bukkit.event.entity.EntityTargetLivingEntityEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import project.kompass.tweakskompass.TweaksKompass

class InvisibilityListener(
    private val plugin: TweaksKompass,
    private val manager: InvisibilityManager
) : Listener {

    private fun isEnabled(): Boolean = plugin.config.getBoolean("better-invisibility.enabled", true)

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        if (!isEnabled()) return
        val player = event.player
        if (player.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
            manager.hidePlayer(player)
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onPotionEffect(event: EntityPotionEffectEvent) {
        if (!isEnabled()) return
        val player = event.entity as? Player ?: return

        // 1. INVISIBILITY APPLIED
        if (event.action == EntityPotionEffectEvent.Action.ADDED || event.action == EntityPotionEffectEvent.Action.CHANGED) {
            val newEffect = event.newEffect ?: return
            if (newEffect.type == PotionEffectType.INVISIBILITY) {
                // Strip swirl particles without recursive loops
                if (plugin.config.getBoolean("better-invisibility.hide-potion-particles", true) && newEffect.hasParticles()) {
                    event.isCancelled = true
                    val cleanEffect = PotionEffect(
                        PotionEffectType.INVISIBILITY,
                        newEffect.duration,
                        newEffect.amplifier,
                        newEffect.isAmbient,
                        false, // Particles disabled
                        newEffect.hasIcon()
                    )
                    player.addPotionEffect(cleanEffect)
                    return
                }
                manager.hidePlayer(player)
            }
        }

        // 2. INVISIBILITY EXPIRED / CLEARED
        else if (event.action == EntityPotionEffectEvent.Action.REMOVED || event.action == EntityPotionEffectEvent.Action.CLEARED) {
            val oldEffect = event.oldEffect ?: return
            if (oldEffect.type == PotionEffectType.INVISIBILITY) {
                manager.showPlayer(player)
            }
        }
    }

    // 3. REDUCED MOB DETECTION
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onMobTarget(event: EntityTargetLivingEntityEvent) {
        if (!isEnabled()) return
        if (!plugin.config.getBoolean("better-invisibility.reduce-mob-detection", true)) return

        val player = event.target as? Player ?: return
        if (!manager.isTracking(player.entityId) && !player.hasPotionEffect(PotionEffectType.INVISIBILITY)) return

        // Fixed: Use EntityTargetEvent.TargetReason
        val allowRetaliation = plugin.config.getBoolean("better-invisibility.allow-retaliation", true)
        if (allowRetaliation && (event.reason == EntityTargetEvent.TargetReason.TARGET_ATTACKED_ENTITY ||
                    event.reason == EntityTargetEvent.TargetReason.TARGET_ATTACKED_NEARBY_ENTITY)) {
            return
        }

        val mob = event.entity
        val detectionRadius = plugin.config.getDouble("better-invisibility.mob-detection-range", 2.5)
        val distSq = mob.location.distanceSquared(player.location)

        if (distSq > detectionRadius * detectionRadius) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        manager.untrack(event.player.entityId)
    }
}