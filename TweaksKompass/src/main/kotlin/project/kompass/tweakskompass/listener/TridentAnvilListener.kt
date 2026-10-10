package project.kompass.tweakskompass.listener

import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.PrepareAnvilEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.EnchantmentStorageMeta

class TridentAnvilListener : Listener {

    companion object {
        private const val ANVIL_REPAIR_COST = 5
    }

    // Handles reading enchantments accurately for both standard items and Enchanted Books
    private fun getEnchantLevel(item: ItemStack, enchant: Enchantment): Int {
        return if (item.type == Material.ENCHANTED_BOOK) {
            val meta = item.itemMeta as? EnchantmentStorageMeta
            meta?.getStoredEnchantLevel(enchant) ?: 0
        } else {
            item.getEnchantmentLevel(enchant)
        }
    }

    // Handles writing enchantments accurately for both standard items and Enchanted Books
    private fun addEnchant(item: ItemStack, enchant: Enchantment, level: Int) {
        if (item.type == Material.ENCHANTED_BOOK) {
            val meta = item.itemMeta as? EnchantmentStorageMeta ?: return
            meta.addStoredEnchant(enchant, level, true)
            item.itemMeta = meta
        } else {
            item.addUnsafeEnchantment(enchant, level)
        }
    }

    @EventHandler
    fun onAnvilUse(event: PrepareAnvilEvent) {
        val first = event.inventory.getItem(0)
        val second = event.inventory.getItem(1)
        if (first == null || second == null || first.type == Material.AIR) return

        val result = first.clone()
        val incoming = if (second.type == Material.ENCHANTED_BOOK) {
            (second.itemMeta as EnchantmentStorageMeta).storedEnchants
        } else {
            second.enchantments
        }

        var added = 0

        for ((enchant, incomingLvl) in incoming) {
            val cur = getEnchantLevel(result, enchant)
            var next = if (cur == incomingLvl) cur + 1 else Math.max(cur, incomingLvl)

            // Capped at vanilla max level limit (prevents overleveling while allowing level-ups)
            val maxLevel = enchant.maxLevel
            next = Math.min(next, maxLevel)

            if (next > cur) {
                addEnchant(result, enchant, next)
                added++
            }
        }

        if (added > 0) {
            val meta = result.itemMeta ?: return
            val renameText = event.view.renameText

            if (!renameText.isNullOrEmpty()) {
                meta.displayName(Component.text(renameText))
            }

            result.itemMeta = meta
            event.result = result
            event.view.repairCost = Math.min(added, ANVIL_REPAIR_COST)
        }
    }
}