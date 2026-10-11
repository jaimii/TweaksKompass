package project.kompass.tweakskompass.leash

import com.destroystokyo.paper.event.player.PlayerElytraBoostEvent
import project.kompass.tweakskompass.TweaksKompass
import org.bukkit.entity.LivingEntity
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener

class LeashElytraBoostListener(private val plugin: TweaksKompass) : Listener {

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onPlayerElytraBoost(event: PlayerElytraBoostEvent) {
        val player = event.player

        if (!plugin.leashManager.hasActiveLeashes(player.uniqueId)) return
        if (!plugin.leashManager.isWorldValid(player.world)) return

        var leashed: MutableList<LivingEntity>? = null

        for (nearby in player.getNearbyEntities(15.0, 15.0, 15.0)) {
            if (nearby is LivingEntity && nearby.isLeashed) {
                val holder = nearby.leashHolder
                if (holder != null && holder.uniqueId == player.uniqueId) {
                    if (leashed == null) {
                        leashed = ArrayList(2)
                    }
                    leashed.add(nearby)
                }
            }
        }

        if (leashed == null) return

        for (living in leashed) {
            plugin.leashManager.registerProgrammaticUnleash(living.uniqueId)
            living.setLeashHolder(null)
            plugin.leashManager.untrackLeash(player.uniqueId, living.uniqueId)
        }

        val finalLeashed = leashed

        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            if (!player.isOnline) return@Runnable

            for (living in finalLeashed) {
                if (living.isValid && !living.isDead) {
                    plugin.leashManager.teleportLeashedInstant(player, living)
                    living.setLeashHolder(player)
                    plugin.leashManager.trackLeash(player.uniqueId, living.uniqueId)
                }
            }
        }, 1L)
    }
}