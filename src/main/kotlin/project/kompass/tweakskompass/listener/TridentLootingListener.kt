package project.kompass.tweakskompass.listener

import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Mob
import org.bukkit.entity.Player
import org.bukkit.entity.Trident
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.loot.LootContext
import java.util.Random

class TridentLootingListener : Listener {

    @EventHandler
    fun onEntityDeath(event: EntityDeathEvent) {
        val damageEvent = event.entity.lastDamageCause as? EntityDamageByEntityEvent ?: return
        val trident = damageEvent.damager as? Trident ?: return

        val level = trident.itemStack.getEnchantmentLevel(Enchantment.LOOTING)
        if (level <= 0) return
        val shooter = trident.shooter as? Player ?: return

        val mob = event.entity as? Mob ?: return
        val lootTable = mob.lootTable
        if (lootTable != null) {
            event.drops.clear()
            event.drops.addAll(lootTable.populateLoot(
                Random(),
                LootContext.Builder(event.entity.location)
                    .lootedEntity(event.entity)
                    .killer(shooter)
                    .luck(level.toFloat())
                    .build()
            ))
        }
    }
}