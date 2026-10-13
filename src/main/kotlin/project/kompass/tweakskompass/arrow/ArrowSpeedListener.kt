package project.kompass.tweakskompass.listener

import org.bukkit.Bukkit
import org.bukkit.entity.*
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityShootBowEvent
import org.bukkit.event.entity.ProjectileHitEvent
import project.kompass.tweakskompass.TweaksKompass
import project.kompass.tweakskompass.arrow.ArrowPacketListener
import project.kompass.tweakskompass.arrow.shouldPassThrough

class ArrowSpeedListener(private val plugin: TweaksKompass) : Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onEntityShootBow(event: EntityShootBowEvent) {
        if (!plugin.config.getBoolean("arrow-settings.enabled", true)) return

        // Safeguard: Never intercept tridents (Drowned, players, or custom mobs)
        val projectile = event.projectile
        if (projectile is Trident) return

        val arrow = projectile as? AbstractArrow ?: return
        val shooter = event.entity as? LivingEntity ?: return
        val entityTypeName = shooter.type.name.uppercase()

        // Resolves multipliers for PLAYER, SKELETON, STRAY, BOGGED, PARCHED, PILLAGER, PIGLIN, ILLUSIONER
        val multiplier = plugin.cachedArrowMultipliers[entityTypeName] ?: plugin.globalArrowMultiplier
        if (multiplier <= 0.0 || multiplier == 1.0) return

        val origVel = arrow.velocity
        val newVelocity = origVel.clone().multiply(multiplier)
        arrow.velocity = newVelocity

        plugin.customArrowVelocities[arrow.uniqueId] = newVelocity
        plugin.customArrowVelocitiesByEntityId[arrow.entityId] = newVelocity

        plugin.arrowTrackingManager.track(arrow, shooter)

        if (shooter is Player) {
            Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                if (!arrow.isValid) return@Runnable
                ArrowPacketListener.sendSyncPacket(shooter, arrow, arrow.velocity)
            }, 1L)
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onProjectileHit(event: ProjectileHitEvent) {
        val projectile = event.entity
        if (projectile is Trident) return

        val arrow = projectile as? AbstractArrow ?: return

        if (event.hitEntity != null && arrow.shooter is Entity) {
            val hitEntity = event.hitEntity!!
            val shooter = arrow.shooter as Entity

            if (shooter.shouldPassThrough(hitEntity, plugin)) {
                event.isCancelled = true
                return
            }
        }

        plugin.arrowTrackingManager.untrack(arrow.uniqueId, arrow.entityId)
        plugin.customArrowVelocities.remove(arrow.uniqueId)
        plugin.customArrowVelocitiesByEntityId.remove(arrow.entityId)
    }
}