package project.kompass.tweakskompass.leash

import io.papermc.paper.entity.Leashable
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityUnleashEvent
import project.kompass.tweakskompass.TweaksKompass

class LeashSnapListener(private val plugin: TweaksKompass) : Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onLeadSnap(event: EntityUnleashEvent) {
        val entity = event.entity
        if (!entity.isValid || entity.isDead) return

        // 1. Programmatic teleportation / internal untethering: NEVER drop a lead
        if (plugin.leashManager.isProgrammaticUnleash(entity.uniqueId)) {
            event.isDropLeash = false
            return
        }

        if (entity is Leashable) {
            val holder = entity.leashHolder
            if (holder == null || !holder.isValid || holder.isDead) return
            if (holder is Player && !holder.isOnline) return

            val reason = event.reason
            val isGlidingPlayer = holder is Player && holder.isGliding

            // 2. Intentional Player Unleash: allow normal release and untrack cleanly
            if (reason == EntityUnleashEvent.UnleashReason.PLAYER_UNLEASH && !isGlidingPlayer) {
                if (holder is Player) {
                    plugin.leashManager.untrackLeash(holder.uniqueId, entity.uniqueId)
                }
                return
            }

            // 3. Distance Snapping & Elytra Gliding: Prevent lead breakage and do not drop extra leads
            if (reason == EntityUnleashEvent.UnleashReason.DISTANCE || isGlidingPlayer) {
                if (!plugin.leashManager.isWorldValid(entity.world)) return

                event.isCancelled = true
                event.isDropLeash = false

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