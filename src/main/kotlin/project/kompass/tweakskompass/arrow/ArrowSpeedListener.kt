package project.kompass.tweakskompass.listener

import org.bukkit.Bukkit
import org.bukkit.entity.*
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityShootBowEvent
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.util.Vector
import project.kompass.tweakskompass.TweaksKompass
import project.kompass.tweakskompass.arrow.ArrowPacketListener
import project.kompass.tweakskompass.arrow.shouldPassThrough
import kotlin.math.sqrt

class ArrowSpeedListener(private val plugin: TweaksKompass) : Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onEntityShootBow(event: EntityShootBowEvent) {
        if (!plugin.config.getBoolean("arrow-settings.enabled", true)) return

        val arrow = event.projectile as? AbstractArrow ?: return
        val shooter = event.entity
        val entityTypeName = shooter.type.name.uppercase()

        // Uses specific multiplier, falling back to GLOBAL
        val multiplier = plugin.cachedArrowMultipliers[entityTypeName] ?: plugin.globalArrowMultiplier

        if (multiplier <= 0.0 || multiplier == 1.0) return

        val origVel = arrow.velocity
        val speed = origVel.length() * multiplier
        val newVelocity: Vector

        // Pitch adjustment for Mobs targeting an entity so higher velocities don't overshoot
        if (shooter is Mob && shooter.target != null) {
            val target = shooter.target!!
            val arrowLoc = arrow.location
            val targetLoc = target.location

            val d0 = targetLoc.x - arrowLoc.x
            val d1 = (targetLoc.y + target.height * 0.3333333333333333) - arrowLoc.y
            val d2 = targetLoc.z - arrowLoc.z
            val d3 = sqrt(d0 * d0 + d2 * d2)

            val d1Adjusted = d1 + d3 * (0.2 / (multiplier * multiplier))
            val targetDir = Vector(d0, d1Adjusted, d2).normalize()

            var horizDir = Vector(origVel.x, 0.0, origVel.z)
            if (horizDir.lengthSquared() > 0) {
                horizDir.normalize()
            } else {
                horizDir = Vector(d0, 0.0, d2).normalize()
            }

            val horizLength = sqrt(targetDir.x * targetDir.x + targetDir.z * targetDir.z)

            val finalDir = Vector(
                horizDir.x * horizLength,
                targetDir.y,
                horizDir.z * horizLength
            )

            newVelocity = finalDir.multiply(speed)
        } else {
            newVelocity = origVel.clone().multiply(multiplier)
        }

        arrow.velocity = newVelocity
        plugin.customArrowVelocities[arrow.uniqueId] = newVelocity

        // Register to raycast tracker
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
        val arrow = event.entity as? AbstractArrow ?: return

        if (event.hitEntity != null && arrow.shooter is Entity) {
            val hitEntity = event.hitEntity!!
            val shooter = arrow.shooter as Entity

            if (shooter.shouldPassThrough(hitEntity, plugin)) {
                // Cancel collision: Arrow phases through friendly mob teammates
                event.isCancelled = true
                return
            }
        }

        plugin.arrowTrackingManager.untrack(arrow.uniqueId)
        plugin.customArrowVelocities.remove(arrow.uniqueId)
    }
}