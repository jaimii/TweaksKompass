package project.kompass.tweakskompass.listener

import project.kompass.tweakskompass.TweaksKompass
import com.destroystokyo.paper.event.entity.EntityKnockbackByEntityEvent
import io.papermc.paper.event.entity.EntityKnockbackEvent
import io.papermc.paper.event.entity.EntityPushedByEntityAttackEvent
import org.bukkit.Bukkit
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.*
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.util.Vector
import java.util.EnumSet
import java.util.UUID
import java.util.HashMap

class WindChargeListener(private val plugin: TweaksKompass) : Listener {

    // Debounce tracking prevents multi-event stacking on the same explosion tick
    private val lastLaunchTicks = HashMap<UUID, Long>()

    companion object {
        private val WIND_CHARGE_TYPES: Set<EntityType> = EnumSet.noneOf(EntityType::class.java)

        init {
            for (type in EntityType.entries) {
                if (type.name.contains("WIND_CHARGE")) {
                    (WIND_CHARGE_TYPES as EnumSet).add(type)
                }
            }
        }
    }

    private fun isWindChargeOrFireball(entity: Entity?): Boolean {
        if (entity == null) return false
        val name = entity.type.name
        return WIND_CHARGE_TYPES.contains(entity.type) ||
                entity is AbstractWindCharge ||
                entity is Fireball ||
                name.contains("FIREBALL")
    }

    // Calculates player's net explosion knockback resistance (Netherite + Blast Protection)
    private fun getNetExplosionResistance(player: Player): Double {
        val kbRes = player.getAttribute(Attribute.KNOCKBACK_RESISTANCE)?.value ?: 0.0
        val expRes = player.getAttribute(Attribute.EXPLOSION_KNOCKBACK_RESISTANCE)?.value ?: 0.0
        return (kbRes + expRes).coerceIn(0.0, 0.95)
    }

    // Safely applies launch velocity with tick debouncing and strict Y-axis capping
    private fun applySafeLaunch(player: Player, velocity: Vector, maxYCap: Double) {
        if (!player.isOnline || player.isDead) return

        val currentTick = player.world.fullTime
        val lastTick = lastLaunchTicks[player.uniqueId] ?: -1L

        // Debounce: Allow only 1 launch velocity override per tick to prevent multi-event stacking
        if (currentTick == lastTick) return
        lastLaunchTicks[player.uniqueId] = currentTick

        val finalX = velocity.x.coerceIn(-1.5, 1.5)
        val finalY = velocity.y.coerceIn(0.40, maxYCap) // Strict Y cap prevents 400-block launches
        val finalZ = velocity.z.coerceIn(-1.5, 1.5)

        val safeVector = Vector(finalX, finalY, finalZ)

        Bukkit.getScheduler().runTask(plugin, Runnable {
            if (player.isOnline && !player.isDead) {
                player.fallDistance = 0f
                player.velocity = safeVector
            }
        })
    }

    // 1. Launch triggered ONLY when a Wind Charge projectile physically impacts and explodes
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onWindChargeHit(event: ProjectileHitEvent) {
        val projectile = event.entity
        if (!isWindChargeOrFireball(projectile)) return

        val blastLoc = projectile.location
        val world = blastLoc.world

        for (entity in world.getNearbyEntities(blastLoc, 3.5, 3.5, 3.5)) {
            if (entity is Player && entity.isOnline && !entity.isDead) {
                val playerCenter = entity.location.clone().add(0.0, 0.9, 0.0)

                val dir = playerCenter.toVector().subtract(blastLoc.toVector())
                val dist = dir.length()

                if (dist > 3.5) continue

                if (dist > 0.0) {
                    dir.normalize()
                } else {
                    dir.y = 1.0 // Directly underneath
                }

                // 1.32 is the exact 100% full-speed target vector (~16 blocks height)
                val isDirectlyBelow = blastLoc.y <= entity.location.y + 0.3
                val verticalY = if (isDirectlyBelow) 1.32 else (dir.y * 1.32).coerceIn(0.65, 1.32)

                val launchVector = Vector(
                    dir.x * 0.50,
                    verticalY,
                    dir.z * 0.50
                )

                // Max Y cap = 1.35 (~16 blocks height max)
                applySafeLaunch(entity, launchVector, 1.35)
            }
        }
    }

    // 2. Launch triggered ONLY when a smash attack connects with Wind Burst
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onWindBurstSmash(event: EntityDamageByEntityEvent) {
        val player = event.damager as? Player ?: return
        val item = player.inventory.itemInMainHand

        if (item.containsEnchantment(Enchantment.WIND_BURST)) {
            val level = item.getEnchantmentLevel(Enchantment.WIND_BURST)
            if (level <= 0) return

            // Level 1 = 0.89 (~7 blocks) | Level 2 = 1.23 (~14 blocks) | Level 3 = 1.57 (~21 blocks)
            val verticalForce = 0.55 + (level * 0.34)

            val look = player.location.direction
            val currentVel = player.velocity
            val finalX = (currentVel.x * 0.70) + (look.x * 0.15)
            val finalZ = (currentVel.z * 0.70) + (look.z * 0.15)

            val launchVector = Vector(
                finalX,
                verticalForce,
                finalZ
            )

            // Max Y cap = 1.60 (~21 blocks height max)
            applySafeLaunch(player, launchVector, 1.60)

            player.world.playSound(player.location, Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1.0f, 1.0f)

            try {
                player.world.spawnParticle(Particle.GUST_EMITTER_LARGE, player.location, 1)
            } catch (ignored: Throwable) {
                player.world.spawnParticle(Particle.EXPLOSION, player.location, 1)
            }
        }
    }

    // 3. Un-dampens Paper's native 3D knockback vector for nearby explosions
    @EventHandler(priority = EventPriority.LOWEST)
    fun onEntityKnockback(event: EntityKnockbackEvent) {
        val player = event.entity as? Player ?: return

        val resistance = getNetExplosionResistance(player)
        if (resistance <= 0.0) return

        var isExplosive = false

        if (event is EntityKnockbackByEntityEvent && isWindChargeOrFireball(event.hitBy)) {
            isExplosive = true
        } else if (event is EntityPushedByEntityAttackEvent && isWindChargeOrFireball(event.pushedBy)) {
            isExplosive = true
        } else if (event.cause == EntityKnockbackEvent.Cause.EXPLOSION) {
            isExplosive = true
        }

        if (isExplosive) {
            val multiplier = 1.0 / (1.0 - resistance).coerceAtLeast(0.1)
            val rawKb = event.knockback
            val unmitigatedKb = Vector(
                (rawKb.x * multiplier).coerceIn(-1.2, 1.2),
                (rawKb.y * multiplier).coerceIn(0.65, 1.35),
                (rawKb.z * multiplier).coerceIn(-1.2, 1.2)
            )
            event.setKnockback(unmitigatedKb)
        }
    }
}