package project.kompass.tweakskompass.listener

import project.kompass.tweakskompass.TweaksKompass
import project.kompass.tweakskompass.util.isBundle
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BundleMeta

class BundleCapacityListener(private val plugin: TweaksKompass) : Listener {

    // Calculates the current internal weight of all stored items
    private fun getBundleWeight(bundle: ItemStack): Int {
        val meta = bundle.itemMeta as? BundleMeta ?: return 0
        var weight = 0
        for (item in meta.items) {
            val maxStack = item.maxStackSize.coerceAtLeast(1)
            val itemWeightMultiplier = 64 / maxStack
            weight += item.amount * itemWeightMultiplier
        }
        return weight
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBundleClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val cursor = event.cursor
        val current = event.currentItem

        // Case 1: Holding an item on the cursor and right-clicking a bundle in the inventory
        if (event.click == ClickType.RIGHT && current != null && current.isBundle() && cursor != null && cursor.type != Material.AIR) {
            if (cursor.isBundle()) return // Prevent nested bundles under our custom system

            event.isCancelled = true
            addItemToBundle(current, cursor, player)
            player.setItemOnCursor(cursor)
            event.setCurrentItem(current)
        }

        // Case 2: Holding a bundle on the cursor and right-clicking an item in the inventory
        else if (event.click == ClickType.RIGHT && cursor != null && cursor.isBundle() && current != null && current.type != Material.AIR) {
            if (current.isBundle()) return

            event.isCancelled = true
            addItemToBundle(cursor, current, player)
            player.setItemOnCursor(cursor)
            event.setCurrentItem(current)
        }
    }

    private fun addItemToBundle(bundle: ItemStack, item: ItemStack, player: Player) {
        val meta = bundle.itemMeta as? BundleMeta ?: return
        val capacityStacks = plugin.config.getInt("bundle-capacity-stacks", 1).coerceAtLeast(1)
        val maxCapacity = capacityStacks * 64

        val currentWeight = getBundleWeight(bundle)
        val maxStack = item.maxStackSize.coerceAtLeast(1)
        val itemWeightMultiplier = 64 / maxStack

        val remainingWeight = maxCapacity - currentWeight
        if (remainingWeight <= 0) return

        val maxItemsCanFit = remainingWeight / itemWeightMultiplier
        if (maxItemsCanFit <= 0) return

        val amountToAdd = Math.min(item.amount, maxItemsCanFit)
        if (amountToAdd <= 0) return

        val addStack = item.clone()
        addStack.amount = amountToAdd

        meta.addItem(addStack)
        bundle.itemMeta = meta

        item.amount -= amountToAdd
        player.playSound(player.location, Sound.ITEM_BUNDLE_INSERT, 1.0f, 1.0f)
    }
}