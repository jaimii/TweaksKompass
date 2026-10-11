package project.kompass.tweakskompass.leash

import project.kompass.tweakskompass.TweaksKompass
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityPortalEvent

class LeashPortalListener(private val plugin: TweaksKompass) : Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onEntityPortal(event: EntityPortalEvent) {
        val livingEntity = event.entity as? LivingEntity ?: return

        if (livingEntity.isLeashed && livingEntity.leashHolder is Player) {
            if (plugin.leashManager.isWorldValid(livingEntity.world)) {
                event.isCancelled = true
            }
        }
    }
}