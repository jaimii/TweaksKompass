package project.kompass.tweakskompass.entity

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.entity.Creeper
import org.bukkit.entity.IronGolem
import org.bukkit.entity.Snowball
import org.bukkit.entity.Snowman
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityTargetEvent
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.event.entity.ProjectileLaunchEvent
import org.bukkit.persistence.PersistentDataType
import project.kompass.tweakskompass.TweaksKompass
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class SnowmanCreeperListener(private val plugin: TweaksKompass) : Listener {

    private val snowmanShooterKey = NamespacedKey("tweakskompass", "snowman_shooter")
    private val creeperTargets = ConcurrentHashMap<UUID, UUID>()

    @EventHandler(priority = EventPriority.LOWEST)
    fun onSnowballLaunch(event: ProjectileLaunchEvent) {
        val snowball = event.entity as? Snowball ?: return
        val snowman = snowball.shooter as? Snowman ?: return

        snowball.persistentDataContainer.set(
            snowmanShooterKey,
            PersistentDataType.STRING,
            snowman.uniqueId.toString()
        )
    }

    @EventHandler
    fun onCreeperHitBySnowball(event: ProjectileHitEvent) {
        val hitEntity = event.hitEntity as? Creeper ?: return
        val snowball = event.entity as? Snowball ?: return
        val snowman = findAttackingSnowman(hitEntity, snowball) ?: return

        val creeperId = hitEntity.uniqueId
        creeperTargets[creeperId] = snowman.uniqueId
        hitEntity.target = snowman
    }

    private fun findAttackingSnowman(creeper: Creeper, snowball: Snowball): Snowman? {
        val uuidStr = snowball.persistentDataContainer.get(snowmanShooterKey, PersistentDataType.STRING)
        if (uuidStr != null) {
            runCatching { UUID.fromString(uuidStr) }.getOrNull()?.let { uuid ->
                val entity = Bukkit.getEntity(uuid)
                if (entity is Snowman && entity.isValid) return entity
            }
        }

        val shooter = snowball.shooter
        if (shooter is Snowman && shooter.isValid) return shooter

        var closestDistSq = Double.MAX_VALUE
        var closestSnowman: Snowman? = null
        for (nearby in creeper.getNearbyEntities(16.0, 16.0, 16.0)) {
            if (nearby is Snowman && nearby.isValid) {
                if (nearby.target?.uniqueId == creeper.uniqueId) return nearby
                val distSq = creeper.location.distanceSquared(nearby.location)
                if (distSq < closestDistSq) {
                    closestDistSq = distSq
                    closestSnowman = nearby
                }
            }
        }
        return closestSnowman
    }

    @EventHandler
    fun onCreeperTarget(event: EntityTargetEvent) {
        val entity = event.entity as? Creeper ?: return

        if (event.target is IronGolem) {
            event.isCancelled = true
            return
        }

        val snowmanId = creeperTargets[entity.uniqueId] ?: return
        val snowman = Bukkit.getEntity(snowmanId)
        if (snowman != null && snowman.isValid && !snowman.isDead) {
            if (entity.location.distanceSquared(snowman.location) <= 1024.0) {
                if (event.target?.uniqueId != snowmanId) {
                    event.target = snowman
                }
            } else {
                creeperTargets.remove(entity.uniqueId)
            }
        } else {
            creeperTargets.remove(entity.uniqueId)
        }
    }

    // Prevents memory leak when mobs despawn naturally
    @EventHandler
    fun onEntityRemove(event: EntityRemoveFromWorldEvent) {
        val uuid = event.entity.uniqueId
        creeperTargets.remove(uuid)
        creeperTargets.values.removeIf { it == uuid }
    }
}