package project.kompass.tweakskompass.listener

import project.kompass.tweakskompass.TweaksKompass
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.entity.ItemSpawnEvent
import org.bukkit.event.inventory.*
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import java.util.EnumSet

class PotionSoupStackListener : Listener {

    companion object {
        private val STACK_TARGET_MATERIALS: Set<Material> = EnumSet.of(
            Material.POTION,
            Material.SPLASH_POTION,
            Material.LINGERING_POTION,
            Material.MUSHROOM_STEW,
            Material.RABBIT_STEW,
            Material.BEETROOT_SOUP,
            Material.SUSPICIOUS_STEW,
            Material.CAKE,
            Material.TOTEM_OF_UNDYING
        )
    }

    // Nanosecond O(1) bitmask check
    private fun isStackTarget(item: ItemStack?): Boolean {
        return item != null && STACK_TARGET_MATERIALS.contains(item.type)
    }

    private fun updateStackSize(item: ItemStack?) {
        if (item == null || item.type == Material.AIR) return
        val type = item.type

        // 1. Potions: Custom stack size 8
        if (type == Material.POTION || type == Material.SPLASH_POTION || type == Material.LINGERING_POTION) {
            val meta = item.itemMeta
            if (meta != null && (!meta.hasMaxStackSize() || meta.maxStackSize != 8)) {
                meta.setMaxStackSize(8)
                item.itemMeta = meta
            }
        }
        // 2. Soups and Stews: Custom stack size 16
        else if (type == Material.MUSHROOM_STEW || type == Material.RABBIT_STEW ||
            type == Material.BEETROOT_SOUP || type == Material.SUSPICIOUS_STEW) {
            val meta = item.itemMeta
            if (meta != null && (!meta.hasMaxStackSize() || meta.maxStackSize != 16)) {
                meta.setMaxStackSize(16)
                item.itemMeta = meta
            }
        }
        // 3. Cakes: Custom stack size 16
        else if (type == Material.CAKE) {
            val meta = item.itemMeta
            if (meta != null && (!meta.hasMaxStackSize() || meta.maxStackSize != 16)) {
                meta.setMaxStackSize(16)
                item.itemMeta = meta
            }
        }
        // 4. Totems of Undying: Custom stack size 4
        else if (type == Material.TOTEM_OF_UNDYING) {
            val meta = item.itemMeta
            if (meta != null && (!meta.hasMaxStackSize() || meta.maxStackSize != 4)) {
                meta.setMaxStackSize(4)
                item.itemMeta = meta
            }
        }
    }

    private fun updateInventory(inventory: Inventory?) {
        if (inventory == null) return
        for (item in inventory.contents) {
            if (isStackTarget(item)) {
                updateStackSize(item)
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        updateInventory(event.player.inventory)
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onInventoryOpen(event: InventoryOpenEvent) {
        updateInventory(event.inventory)
        updateInventory(event.player.inventory)
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onInventoryClick(event: InventoryClickEvent) {
        if (event is InventoryCreativeEvent) return

        val cursor = event.cursor
        val currentItem = event.currentItem

        if (!isStackTarget(cursor) && !isStackTarget(currentItem)) return

        updateStackSize(currentItem)
        updateStackSize(cursor)

        Bukkit.getScheduler().runTask(JavaPlugin.getPlugin(TweaksKompass::class.java), Runnable {
            event.clickedInventory?.let {
                val slotItem = it.getItem(event.slot)
                if (isStackTarget(slotItem)) {
                    updateStackSize(slotItem)
                    it.setItem(event.slot, slotItem)
                }
            }
            val player = event.whoClicked as? Player
            if (player != null && player.isOnline) {
                val cursorItem = player.itemOnCursor
                if (isStackTarget(cursorItem)) {
                    updateStackSize(cursorItem)
                    player.setItemOnCursor(cursorItem)
                }
            }
        })
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onInventoryCreative(event: InventoryCreativeEvent) {
        val cursor = event.cursor
        val currentItem = event.currentItem

        if (!isStackTarget(cursor) && !isStackTarget(currentItem)) return

        val oldCursor = event.whoClicked.itemOnCursor
        if (oldCursor != null && oldCursor.type != Material.AIR &&
            cursor != null && cursor.type != Material.AIR &&
            oldCursor.isSimilar(cursor)) {

            val type = oldCursor.type
            val isPotion = (type == Material.POTION || type == Material.SPLASH_POTION || type == Material.LINGERING_POTION)
            val isSoup = (type == Material.MUSHROOM_STEW || type == Material.RABBIT_STEW ||
                    type == Material.BEETROOT_SOUP || type == Material.SUSPICIOUS_STEW)
            val isCake = (type == Material.CAKE)
            val isTotem = (type == Material.TOTEM_OF_UNDYING)

            if (isPotion || isSoup || isCake || isTotem) {
                val max = when {
                    isPotion -> 8
                    isSoup -> 16
                    isCake -> 16
                    isTotem -> 4
                    else -> 64
                }
                val newAmount = Math.min(oldCursor.amount + cursor.amount, max)

                val stacked = oldCursor.clone()
                stacked.amount = newAmount
                updateStackSize(stacked)

                event.whoClicked.setItemOnCursor(stacked)
                event.setResult(org.bukkit.event.Event.Result.ALLOW)
            }
        } else {
            updateStackSize(currentItem)
            updateStackSize(cursor)
        }

        Bukkit.getScheduler().runTask(JavaPlugin.getPlugin(TweaksKompass::class.java), Runnable {
            val player = event.whoClicked as? Player ?: return@Runnable
            if (player.isOnline) {
                val cursorItem = player.itemOnCursor
                if (isStackTarget(cursorItem)) {
                    updateStackSize(cursorItem)
                    player.setItemOnCursor(cursorItem)
                }

                event.clickedInventory?.let {
                    val slotItem = it.getItem(event.slot)
                    if (isStackTarget(slotItem)) {
                        updateStackSize(slotItem)
                        it.setItem(event.slot, slotItem)
                    }
                }
            }
        })
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onInventoryDrag(event: InventoryDragEvent) {
        val oldCursor = event.oldCursor
        val cursor = event.cursor

        var involved = isStackTarget(oldCursor) || isStackTarget(cursor)
        if (!involved) {
            for (item in event.newItems.values) {
                if (isStackTarget(item)) {
                    involved = true
                    break
                }
            }
        }
        if (!involved) return

        for (item in event.newItems.values) {
            updateStackSize(item)
        }
        updateStackSize(oldCursor)
        updateStackSize(cursor)

        Bukkit.getScheduler().runTask(JavaPlugin.getPlugin(TweaksKompass::class.java), Runnable {
            for (slot in event.newItems.keys) {
                val slotItem = event.view.getItem(slot)
                if (isStackTarget(slotItem)) {
                    updateStackSize(slotItem)
                    event.view.setItem(slot, slotItem)
                }
            }
            val player = event.whoClicked as? Player
            if (player != null && player.isOnline) {
                val cursorItem = player.itemOnCursor
                if (isStackTarget(cursorItem)) {
                    updateStackSize(cursorItem)
                    player.setItemOnCursor(cursorItem)
                }
            }
        })
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onItemSpawn(event: ItemSpawnEvent) {
        val itemEntity = event.entity
        val item = itemEntity.itemStack
        if (!isStackTarget(item)) return

        updateStackSize(item)
        itemEntity.itemStack = item
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onItemPickup(event: EntityPickupItemEvent) {
        val itemEntity = event.item
        val item = itemEntity.itemStack
        if (!isStackTarget(item)) return

        updateStackSize(item)
        itemEntity.itemStack = item
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onBrew(event: BrewEvent) {
        updateInventory(event.contents)
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onCraft(event: CraftItemEvent) {
        val result = event.recipe.result
        if (!isStackTarget(result)) return

        val currentItem = event.currentItem
        val cursor = event.cursor

        updateStackSize(currentItem)
        updateStackSize(cursor)
        updateStackSize(result)
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onPlayerInteract(event: PlayerInteractEvent) {
        if (!event.hasItem()) return
        val item = event.item
        if (!isStackTarget(item)) return

        updateStackSize(item)
        event.player.inventory.setItem(event.hand ?: org.bukkit.inventory.EquipmentSlot.HAND, item)
    }
}