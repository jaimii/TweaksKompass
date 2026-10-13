package project.kompass.tweakskompass.listener

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent
import io.papermc.paper.event.entity.EntityKnockbackEvent
import org.bukkit.Bukkit
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerRespawnEvent
import project.kompass.tweakskompass.TweaksKompass

class WindChargeListener(private val plugin: TweaksKompass) : Listener {

    private fun isFeatureEnabled(): Boolean {
        return plugin.config.getBoolean("wind-charge.enabled", true) &&
                plugin.config.getBoolean("wind-charge.ignore-armor-dampening", true)
    }

    // 1. Neutralize Blast Protection's Explosion Knockback Dampening
    private fun stripBlastProtectionKnockbackResistance(player: Player) {
        val attribute = player.getAttribute(Attribute.EXPLOSION_KNOCKBACK_RESISTANCE) ?: return
        val modifiersToRemove = attribute.modifiers.filter { modifier ->
            val key = modifier.key.toString().lowercase()
            key.contains("blast_protection")
        }

        for (modifier in modifiersToRemove) {
            attribute.removeModifier(modifier)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onArmorChange(event: PlayerArmorChangeEvent) {
        if (!isFeatureEnabled()) return
        stripBlastProtectionKnockbackResistance(event.player)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        if (!isFeatureEnabled()) return
        stripBlastProtectionKnockbackResistance(event.player)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerRespawn(event: PlayerRespawnEvent) {
        if (!isFeatureEnabled()) return
        Bukkit.getScheduler().runTask(plugin, Runnable {
            if (event.player.isOnline) {
                stripBlastProtectionKnockbackResistance(event.player)
            }
        })
    }

    // 2. Un-Dampen Residual Resistance in Explosion Knockback
    @EventHandler(priority = EventPriority.LOWEST)
    fun onEntityKnockback(event: EntityKnockbackEvent) {
        if (!isFeatureEnabled()) return

        val player = event.entity as? Player ?: return
        if (event.cause != EntityKnockbackEvent.Cause.EXPLOSION) return

        val expRes = player.getAttribute(Attribute.EXPLOSION_KNOCKBACK_RESISTANCE)?.value ?: 0.0
        val kbRes = player.getAttribute(Attribute.KNOCKBACK_RESISTANCE)?.value ?: 0.0
        val totalResistance = (expRes + kbRes).coerceIn(0.0, 0.95)

        if (totalResistance > 0.0) {
            val multiplier = 1.0 / (1.0 - totalResistance)
            val rawKnockback = event.knockback
            event.knockback = rawKnockback.clone().multiply(multiplier)
        }
    }
}