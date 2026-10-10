package project.kompass.tweakskompass.listener

import project.kompass.tweakskompass.util.TridentUtil
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.*
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.EntityShootBowEvent
import org.bukkit.loot.LootContext
import org.bukkit.persistence.PersistentDataType
import java.util.Random

class RangedLootingListener : Listener {

    private val random = Random()

    @EventHandler
    fun onBowShoot(event: EntityShootBowEvent) {
        if (event.entity !is Player) return

        val bow = event.bow ?: return
        val level = bow.getEnchantmentLevel(Enchantment.LOOTING)
        if (level > 0) {
            val projectile = event.projectile
            projectile.persistentDataContainer.set(
                TridentUtil.LOOTING_KEY,
                PersistentDataType.INTEGER,
                level
            )
        }
    }

    @EventHandler
    fun onEntityDeath(event: EntityDeathEvent) {
        val mob = event.entity as? Mob ?: return
        val damageEvent = mob.lastDamageCause as? EntityDamageByEntityEvent ?: return
        val damager = damageEvent.damager

        var level = 0
        var shooter: Player? = null

        if (damager is Trident) {
            level = damager.itemStack.getEnchantmentLevel(Enchantment.LOOTING)
            shooter = damager.shooter as? Player
        } else if (damager is Projectile) {
            level = damager.persistentDataContainer.getOrDefault(
                TridentUtil.LOOTING_KEY,
                PersistentDataType.INTEGER,
                0
            )
            shooter = damager.shooter as? Player
        }

        if (level > 0 && shooter != null) {
            val lootTable = mob.lootTable
            if (lootTable != null) {
                event.drops.clear()
                event.drops.addAll(
                    lootTable.populateLoot(
                        random,
                        LootContext.Builder(mob.location)
                            .lootedEntity(mob)
                            .killer(shooter)
                            .luck(level.toFloat())
                            .build()
                    )
                )
            }
        }
    }
}