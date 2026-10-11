package project.kompass.tweakskompass.arrow

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Sound
import org.bukkit.entity.*
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.Vector
import project.kompass.tweakskompass.TweaksKompass
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.sqrt

class ArrowTrackingManager(private val plugin: TweaksKompass) {

    private class TrackedArrow(
        val arrow: AbstractArrow,
        val shooter: LivingEntity,
        val lastLoc: Location,
        val spawnTime: Long = System.currentTimeMillis()
    )

    private val trackedArrows = CopyOnWriteArrayList<TrackedArrow>()
    private var tickTask: BukkitTask? = null

    // Pre-allocated reusable location to eliminate GC pressure
    private val tempLoc = Location(null, 0.0, 0.0, 0.0)

    fun start() {
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            if (trackedArrows.isEmpty()) return@Runnable

            val iterator = trackedArrows.iterator()
            val currentTime = System.currentTimeMillis()

            while (iterator.hasNext()) {
                val tracked = iterator.next()
                val arrow = tracked.arrow
                val shooter = tracked.shooter

                // Cleanup on despawn/invalidation or 5s lifetime (100 ticks)
                if (!arrow.isValid || currentTime - tracked.spawnTime > 5000) {
                    trackedArrows.remove(tracked)
                    plugin.customArrowVelocities.remove(arrow.uniqueId)
                    continue
                }

                plugin.customArrowVelocities[arrow.uniqueId] = arrow.velocity

                val currentLoc = arrow.getLocation(tempLoc) ?: continue

                if (currentLoc.world != tracked.lastLoc.world) {
                    tracked.lastLoc.x = currentLoc.x
                    tracked.lastLoc.y = currentLoc.y
                    tracked.lastLoc.z = currentLoc.z
                    tracked.lastLoc.world = currentLoc.world
                    continue
                }

                val dx = currentLoc.x - tracked.lastLoc.x
                val dy = currentLoc.y - tracked.lastLoc.y
                val dz = currentLoc.z - tracked.lastLoc.z
                val distanceSq = dx * dx + dy * dy + dz * dz

                if (distanceSq > 0.0001) {
                    val distance = sqrt(distanceSq)
                    val direction = Vector(dx / distance, dy / distance, dz / distance)

                    // Raytrace collision check
                    val result = arrow.world.rayTraceEntities(
                        tracked.lastLoc,
                        direction,
                        distance,
                        0.3
                    ) { entity ->
                        entity is LivingEntity &&
                                entity !is Player &&
                                entity !is ArmorStand &&
                                !entity.isDead &&
                                entity.uniqueId != shooter.uniqueId &&
                                !shooter.shouldPassThrough(entity)
                    }

                    if (result != null && result.hitEntity is LivingEntity) {
                        val target = result.hitEntity as LivingEntity
                        trackedArrows.remove(tracked)
                        plugin.customArrowVelocities.remove(arrow.uniqueId)

                        var damage = arrow.damage * distance
                        if (arrow.isCritical) {
                            damage += Math.random() * (damage / 2.0) + 1.0
                        }

                        target.damage(damage, arrow)

                        val hitLocation = result.hitPosition.toLocation(arrow.world)
                        arrow.world.playSound(hitLocation, Sound.ENTITY_ARROW_HIT, 1.0f, 1.2f)
                        arrow.remove()
                        continue
                    }
                }

                if (arrow.isInBlock || arrow.isOnGround) {
                    trackedArrows.remove(tracked)
                    plugin.customArrowVelocities.remove(arrow.uniqueId)
                    continue
                }

                tracked.lastLoc.x = currentLoc.x
                tracked.lastLoc.y = currentLoc.y
                tracked.lastLoc.z = currentLoc.z
                tracked.lastLoc.world = currentLoc.world
            }
        }, 1L, 1L)
    }

    fun stop() {
        tickTask?.cancel()
        trackedArrows.clear()
    }

    fun track(arrow: AbstractArrow, shooter: LivingEntity) {
        trackedArrows.add(TrackedArrow(arrow, shooter, arrow.location.clone()))
    }

    fun untrack(arrowId: UUID) {
        trackedArrows.removeIf { it.arrow.uniqueId == arrowId }
    }
}