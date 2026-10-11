package project.kompass.tweakskompass.leash

import project.kompass.tweakskompass.TweaksKompass
import io.papermc.paper.entity.Leashable
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityUnleashEvent

class LeashSnapListener(private val plugin: TweaksKompass) : Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onLeadSnap(event: EntityUnleashEvent) {
        val entity = event.entity
        if (!entity.isValid || entity.isDead) return

        // Allow programmatic untethering to bypass cancellation so no lead item drops
        if (plugin.leashManager.isProgrammaticUnleash(entity.uniqueId)) return

        if (entity is Leashable) {
            val holder = entity.leashHolder

            if (holder is Player) {
                plugin.leashManager.untrackLeash(holder.uniqueId, entity.uniqueId)
            }

            if (holder == null || !holder.isValid || holder.isDead) return
            if (holder is Player && !holder.isOnline) return

            val reason = event.reason
            val isGlidingPlayer = holder is Player && holder.isGliding

            if (reason == EntityUnleashEvent.UnleashReason.PLAYER_UNLEASH && !isGlidingPlayer) {
                return
            }

            if (reason == EntityUnleashEvent.UnleashReason.DISTANCE ||
                reason == EntityUnleashEvent.UnleashReason.UNKNOWN ||
                isGlidingPlayer
            ) {
                if (!plugin.leashManager.isWorldValid(entity.world)) return

                // Prevent the lead from breaking/dropping
                event.isCancelled = true

                if (entity is LivingEntity && holder is LivingEntity) {
                    if (isGlidingPlayer) {
                        plugin.server.scheduler.runTaskLater(plugin, Runnable {
                            if (entity.isValid && !entity.isDead && holder.isValid && !holder.isDead) {
                                plugin.leashManager.teleportLeashedInstant(holder, entity)
                            }
                        }, 1L)
                    } else {
                        plugin.leashManager.teleportLeashed(holder, entity)
                    }
                } else {
                    plugin.server.scheduler.runTaskLater(plugin, Runnable {
                        if (entity.isValid && holder.isValid) {
                            entity.teleport(holder.location.add(0.0, 1.0, 0.0))
                        }
                    }, 1L)
                }
            }
        }
    }
}