package project.kompass.tweakskompass.anvil

import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.inventory.PrepareAnvilEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.Damageable
import org.bukkit.inventory.meta.EnchantmentStorageMeta
import project.kompass.tweakskompass.TweaksKompass

class AnvilNoTooExpensiveListener(private val plugin: TweaksKompass) : Listener {

    private fun getEnchantLevel(item: ItemStack, enchant: Enchantment): Int {
        return if (item.type == Material.ENCHANTED_BOOK) {
            val meta = item.itemMeta as? EnchantmentStorageMeta
            meta?.getStoredEnchantLevel(enchant) ?: 0
        } else {
            item.getEnchantmentLevel(enchant)
        }
    }

    private fun addEnchant(item: ItemStack, enchant: Enchantment, level: Int) {
        if (item.type == Material.ENCHANTED_BOOK) {
            val meta = item.itemMeta as? EnchantmentStorageMeta ?: return
            meta.addStoredEnchant(enchant, level, true)
            item.itemMeta = meta
        } else {
            item.addUnsafeEnchantment(enchant, level)
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onAnvilPrepare(event: PrepareAnvilEvent) {
        if (!plugin.config.getBoolean("anvil-settings.remove-too-expensive-limit", true)) return

        val player = event.view.player as? Player ?: return
        val view = event.view

        view.maximumRepairCost = Int.MAX_VALUE - 10

        if (view.repairCost in 0..39) {
            onRemove(player)
            return
        }

        val input = event.inventory.getItem(0) ?: return
        if (input.type.isAir) return
        val combineWith = event.inventory.getItem(1)

        val rename = event.view.renameText
        val hasRename = !rename.isNullOrEmpty()

        val result = input.clone()
        var cost = 40.0f
        var applied = 0

        // Handle Enchantment Merging & Material Repairs
        if (combineWith != null && !combineWith.type.isAir) {
            val meta = combineWith.itemMeta
            val incomingEnchants = if (meta is EnchantmentStorageMeta) {
                meta.storedEnchants
            } else {
                meta?.enchants ?: emptyMap()
            }

            for ((enchant, level) in incomingEnchants) {
                if (!enchant.canEnchantItem(result) && result.type != Material.ENCHANTED_BOOK) continue
                if (result.enchantments.keys.any { it.conflictsWith(enchant) && it != enchant }) continue

                val curLvl = getEnchantLevel(result, enchant)
                var nextLvl = if (curLvl == level) curLvl + 1 else maxOf(curLvl, level)
                nextLvl = minOf(nextLvl, enchant.maxLevel)

                if (nextLvl > curLvl) {
                    addEnchant(result, enchant, nextLvl)
                    applied++
                    cost += nextLvl * 1.5f
                }
            }

            // Material durability repair
            if (applied == 0 && combineWith.type == input.type && result.itemMeta is Damageable) {
                val damageMeta = result.itemMeta as Damageable
                if (damageMeta.damage > 0) {
                    damageMeta.damage = maxOf(0, damageMeta.damage - (input.type.maxDurability / 4))
                    result.itemMeta = damageMeta
                    applied++
                    cost += 2.0f
                }
            }
        }

        // Handle Renaming
        if (hasRename) {
            val resultMeta = result.itemMeta
            if (resultMeta != null) {
                resultMeta.displayName(Component.text(rename))
                result.itemMeta = resultMeta
                cost += 1.0f
                applied++
            }
        }

        if (applied == 0) return

        val finalCost = cost.toInt()
        event.result = result
        view.repairCost = finalCost

        AnvilPacketListener.preparing[player.uniqueId] = finalCost
        AnvilPacketListener.sendSpoofedCreativeAbilities(player)
    }

    @EventHandler
    fun onClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        if (event.view.topInventory.type == InventoryType.ANVIL) {
            onRemove(player)
        }
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        onRemove(event.player)
    }

    private fun onRemove(player: Player) {
        if (AnvilPacketListener.preparing.remove(player.uniqueId) != null) {
            AnvilPacketListener.restoreAbilities(player)
        }
    }
}