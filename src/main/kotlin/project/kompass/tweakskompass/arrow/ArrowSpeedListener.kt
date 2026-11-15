package project.kompass.tweakskompass.arrow

import org.bukkit.Bukkit
import org.bukkit.entity.*
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityShootBowEvent
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.util.Vector
import project.kompass.tweakskompass.TweaksKompass
import kotlin.math.sqrt

class ArrowSpeedListener(private val plugin: TweaksKompass) : Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onEntityShootBow(event: EntityShootBowEvent) {
        if (!plugin.config.getBoolean("arrow-settings.enabled", true)) return

        val projectile = event.projectile
        if (projectile is Trident) return

        val arrow = projectile as? AbstractArrow ?: return
        val shooter = event.entity // Redundant cast removed; event.entity is already LivingEntity
        val entityTypeName = shooter.type.name.uppercase()

        val multiplier = plugin.cachedArrowMultipliers[entityTypeName] ?: plugin.globalArrowMultiplier
        if (multiplier <= 0.0 || multiplier == 1.0) return

        val origVel = arrow.velocity
        val origSpeed = origVel.length()
        if (origSpeed < 0.001) return

        val targetSpeed = origSpeed * multiplier
        val newVelocity: Vector

        val mobTarget = (shooter as? Mob)?.target
        if (mobTarget != null) {
            val arrowLoc = arrow.location
            val dx = mobTarget.x - arrowLoc.x
            val dy = (mobTarget.y + mobTarget.height * 0.3333) - arrowLoc.y
            val dz = mobTarget.z - arrowLoc.z
            val horizDist = sqrt(dx * dx + dz * dz)

            val gravityComp = (horizDist * 0.2) / (multiplier * multiplier)
            val aimVector = Vector(dx, dy + gravityComp, dz)

            newVelocity = if (aimVector.lengthSquared() > 0.0001) {
                aimVector.normalize().multiply(targetSpeed)
            } else {
                origVel.clone().multiply(multiplier)
            }
        } else {
            newVelocity = origVel.clone().multiply(multiplier)
        }

        if (shooter.isInsideVehicle && shooter.vehicle != null) {
            val forward = newVelocity.clone().setY(0.0)
            if (forward.lengthSquared() > 0.0001) {
                forward.normalize().multiply(0.6)
                arrow.teleport(arrow.location.add(forward))
            }
        }

        arrow.velocity = newVelocity

        plugin.customArrowVelocities[arrow.uniqueId] = newVelocity
        plugin.customArrowVelocitiesByEntityId[arrow.entityId] = newVelocity

        plugin.arrowTrackingManager.track(arrow, shooter)

        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            if (!arrow.isValid) return@Runnable
            val currentVel = arrow.velocity
            if (shooter is Player) {
                ArrowPacketListener.sendSyncPacket(shooter, arrow, currentVel)
            }
            for (viewer in arrow.trackedBy) {
                if (viewer.uniqueId != shooter.uniqueId) {
                    ArrowPacketListener.sendSyncPacket(viewer, arrow, currentVel)
                }
            }
        }, 1L)
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onProjectileHit(event: ProjectileHitEvent) {
        val projectile = event.entity
        if (projectile is Trident) return

        val arrow = projectile as? AbstractArrow ?: return
        val hitEntity = event.hitEntity
        val shooter = arrow.shooter as? Entity

        if (hitEntity != null && shooter != null) {
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