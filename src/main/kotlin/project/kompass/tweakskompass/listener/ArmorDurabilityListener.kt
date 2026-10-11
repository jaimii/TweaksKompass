package project.kompass.tweakskompass.listener

import project.kompass.tweakskompass.TweaksKompass
import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerItemDamageEvent
import org.bukkit.inventory.ItemStack
import java.util.EnumSet
import java.util.UUID
import java.util.concurrent.TimeUnit

class ArmorDurabilityListener(private val plugin: TweaksKompass) : Listener {

    companion object {
        private val ARMOR_MATERIALS: Set<Material> = EnumSet.noneOf(Material::class.java)

        init {
            for (material in Material.entries) {
                val name = material.name
                if (name.startsWith("LEGACY_")) continue
                if (name.contains("HELMET") || name.contains("CHESTPLATE") || name.contains("LEGGINGS") || name.contains("BOOTS")) {
                    (ARMOR_MATERIALS as EnumSet).add(material)
                }
            }
        }
    }

    private val thornsTriggeredTicks: Cache<UUID, Long> = CacheBuilder.newBuilder()
        .expireAfterWrite(5, TimeUnit.SECONDS)
        .build()

    @EventHandler
    fun onThornsDamage(event: EntityDamageByEntityEvent) {
        if (event.cause == EntityDamageEvent.DamageCause.THORNS) {
            val player = event.damager as? Player ?: return
            thornsTriggeredTicks.put(player.uniqueId, player.world.fullTime)
        }
    }

    @EventHandler
    fun onPlayerItemDamage(event: PlayerItemDamageEvent) {
        if (!plugin.config.getBoolean("custom-armor-durability.enabled", true)) return

        val item = event.item
        if (item == null || !ARMOR_MATERIALS.contains(item.type)) return

        val player = event.player
        var damage = event.damage

        val lastThornsTick = thornsTriggeredTicks.getIfPresent(player.uniqueId)
        val isThornsDamage = lastThornsTick != null &&
                lastThornsTick == player.world.fullTime &&
                item.containsEnchantment(Enchantment.THORNS)

        if (isThornsDamage) {
            damage = (damage / 2).coerceAtLeast(1)
        } else {
            val multiplier = plugin.config.getDouble(
                "custom-armor-durability.multiplier",
                plugin.config.getDouble("armor-durability-multiplier", 0.2)
            )
            val flatModifier = plugin.config.getInt(
                "custom-armor-durability.flat-modifier",
                plugin.config.getInt("armor-durability-flat-modifier", 0)
            )
            damage = (damage * multiplier + flatModifier).toInt().coerceAtLeast(0)
        }

        event.damage = damage
    }
}