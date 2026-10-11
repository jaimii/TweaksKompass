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

class BundleCapacityListener(private val plugin: BTK) : Listener {

    private fun isEnabled(): Boolean {
        return plugin.config.getBoolean("food-and-storage.bundle-capacity.enabled", true)
    }

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
        if (!isEnabled()) return

        val player = event.whoClicked as? Player ?: return
        val cursor = event.cursor
        val current = event.currentItem

        if (event.click == ClickType.RIGHT && current != null && current.isBundle() && cursor != null && cursor.type != Material.AIR) {
            if (cursor.isBundle()) return

            event.isCancelled = true
            addItemToBundle(current, cursor, player)
            player.setItemOnCursor(cursor)
            event.setCurrentItem(current)
        } else if (event.click == ClickType.RIGHT && cursor != null && cursor.isBundle() && current != null && current.type != Material.AIR) {
            if (current.isBundle()) return

            event.isCancelled = true
            addItemToBundle(cursor, current, player)
            player.setItemOnCursor(cursor)
            event.setCurrentItem(current)
        }
    }

    private fun addItemToBundle(bundle: ItemStack, item: ItemStack, player: Player) {
        val meta = bundle.itemMeta as? BundleMeta ?: return
        val capacityStacks = plugin.config.getInt(
            "food-and-storage.bundle-capacity.stacks",
            plugin.config.getInt("bundle-capacity-stacks", 2)
        ).coerceAtLeast(1)
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