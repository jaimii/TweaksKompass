package project.kompass.tweakskompass.entity

import org.bukkit.Bukkit
import org.bukkit.entity.*
import org.bukkit.scheduler.BukkitRunnable
import project.kompass.tweakskompass.TweaksKompass

class GolemAttackTask(private val plugin: TweaksKompass) : BukkitRunnable() {

    override fun run() {
        if (!plugin.config.getBoolean("entity-tweaks.golem-attack-system", true)) return

        for (world in Bukkit.getWorlds()) {
            // 1. IRON GOLEMS
            val ironGolems = world.getEntitiesByClass(IronGolem::class.java)
            for (golem in ironGolems) {
                if (!golem.isValid || golem.isDead) continue

                val currentTarget = golem.target
                if (currentTarget != null && isHighPriority(currentTarget)) continue

                var closestHighPriority: LivingEntity? = null
                var closestHighDistSq = Double.MAX_VALUE

                var closestStandardThreat: LivingEntity? = null
                var closestStandardDistSq = Double.MAX_VALUE

                val golemLoc = golem.location

                // Single-pass inspection: Evaluates all threats simultaneously with 0 extra list allocations
                for (nearby in golem.getNearbyEntities(10.0, 10.0, 10.0)) {
                    if (nearby !is LivingEntity || !nearby.isValid || nearby.isDead) continue
                    if (nearby is ZombieVillager) continue // Never target curing villagers

                    val distSq = golemLoc.distanceSquared(nearby.location)

                    if (isHighPriority(nearby)) {
                        if (distSq < closestHighDistSq) {
                            closestHighDistSq = distSq
                            closestHighPriority = nearby
                        }
                    } else if (nearby is Monster) {
                        if (distSq < closestStandardDistSq) {
                            closestStandardDistSq = distSq
                            closestStandardThreat = nearby
                        }
                    }
                }

                val targetToSet = closestHighPriority ?: closestStandardThreat
                if (targetToSet != null && golem.target != targetToSet) {
                    golem.target = targetToSet
                }
            }

            // 2. SNOW GOLEMS
            val snowGolems = world.getEntitiesByClass(Snowman::class.java)
            for (snowman in snowGolems) {
                if (!snowman.isValid || snowman.isDead) continue

                val currentTarget = snowman.target
                if (currentTarget is Creeper && currentTarget.isValid && !currentTarget.isDead) continue

                var closestCreeper: Creeper? = null
                var closestDistSq = Double.MAX_VALUE
                val snowmanLoc = snowman.location

                for (nearby in snowman.getNearbyEntities(15.0, 15.0, 15.0)) {
                    if (nearby is Creeper && nearby.isValid && !nearby.isDead) {
                        val distSq = snowmanLoc.distanceSquared(nearby.location)
                        if (distSq < closestDistSq) {
                            closestDistSq = distSq
                            closestCreeper = nearby
                        }
                    }
                }

                if (closestCreeper != null && snowman.target != closestCreeper) {
                    snowman.target = closestCreeper
                }
            }
        }
    }

    private fun isHighPriority(entity: Entity): Boolean {
        return (entity is Zombie && entity !is ZombieVillager) || entity is Raider
    }
}