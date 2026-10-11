package project.kompass.tweakskompass.listener

import project.kompass.tweakskompass.TweaksKompass
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Statistic
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityResurrectEvent

class InventoryTotemListener(private val plugin: TweaksKompass) : Listener {

    private val postmortalKey = NamespacedKey.minecraft("adventure/totem_of_undying")

    @EventHandler(priority = EventPriority.HIGH)
    fun onEntityResurrect(event: EntityResurrectEvent) {
        val player = event.entity as? Player ?: return

        // Check if feature is enabled in config
        if (!plugin.config.getBoolean("inventory-totem.enabled", true)) return

        // If player already held a totem in main/off-hand, vanilla handles it natively
        if (!event.isCancelled || event.hand != null) return

        // Vanilla totems do not save players from void or /kill damage
        val lastCause = player.lastDamageCause?.cause
        if (lastCause == EntityDamageEvent.DamageCause.VOID || lastCause == EntityDamageEvent.DamageCause.KILL) {
            return
        }

        // Search the player's direct inventory slots (excludes shulker boxes and bundles)
        val totemSlot = findTotemSlot(player) ?: return

        // Consume 1 totem from the found inventory slot
        consumeTotem(player, totemSlot)

        // Un-cancel the event to let Minecraft natively trigger resurrection
        // This natively heals, applies potion effects, and displays the screen-pop totem animation!
        event.isCancelled = false

        // 1. Increment in-game statistic for Totem of Undying usage
        player.incrementStatistic(Statistic.USE_ITEM, Material.TOTEM_OF_UNDYING)

        // 2. Award the vanilla "Postmortal" advancement if not already unlocked
        awardPostmortalAdvancement(player)
    }

    // Searches the 36 main inventory slots + armor slots for a direct Totem of Undying item
    private fun findTotemSlot(player: Player): Int? {
        val contents = player.inventory.contents
        for (slot in contents.indices) {
            val item = contents[slot] ?: continue
            // Directly matches TOTEM_OF_UNDYING (will not match shulker boxes or bundles)
            if (item.type == Material.TOTEM_OF_UNDYING) {
                return slot
            }
        }
        return null
    }

    private fun consumeTotem(player: Player, slot: Int) {
        val item = player.inventory.getItem(slot) ?: return
        if (item.amount > 1) {
            item.amount -= 1
            player.inventory.setItem(slot, item)
        } else {
            player.inventory.setItem(slot, null)
        }
    }

    private fun awardPostmortalAdvancement(player: Player) {
        val advancement = Bukkit.getAdvancement(postmortalKey) ?: return
        val progress = player.getAdvancementProgress(advancement)
        if (!progress.isDone) {
            for (criteria in progress.remainingCriteria) {
                progress.awardCriteria(criteria)
            }
        }
    }
}