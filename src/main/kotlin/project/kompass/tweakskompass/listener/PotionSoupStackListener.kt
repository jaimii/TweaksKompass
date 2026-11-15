package project.kompass.tweakskompass.listener

import io.papermc.paper.datacomponent.DataComponentTypes
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.entity.ItemSpawnEvent
import org.bukkit.event.inventory.*
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import project.kompass.tweakskompass.TweaksKompass
import java.util.EnumSet

class PotionSoupStackListener(private val plugin: TweaksKompass) : Listener {

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

    private fun isStackTarget(item: ItemStack?): Boolean {
        if (!plugin.config.getBoolean("food-and-storage.custom-stacks.enabled", true)) return false
        return item != null && STACK_TARGET_MATERIALS.contains(item.type)
    }

    private fun getTargetLimit(type: Material): Int? {
        if (!plugin.config.getBoolean("food-and-storage.custom-stacks.enabled", true)) return null
        return when (type) {
            Material.POTION, Material.SPLASH_POTION, Material.LINGERING_POTION ->
                plugin.config.getInt("food-and-storage.custom-stacks.potions", 8)
            Material.MUSHROOM_STEW, Material.RABBIT_STEW, Material.BEETROOT_SOUP, Material.SUSPICIOUS_STEW ->
                plugin.config.getInt("food-and-storage.custom-stacks.soups", 16)
            Material.CAKE ->
                plugin.config.getInt("food-and-storage.custom-stacks.cakes", 16)
            Material.TOTEM_OF_UNDYING ->
                plugin.config.getInt("food-and-storage.custom-stacks.totems", 4)
            else -> null
        }?.coerceIn(1, 99)
    }

    // Uses Paper 1.21 native DataComponentTypes.MAX_STACK_SIZE (0 ItemMeta cloning)
    private fun updateStackSize(item: ItemStack?): Boolean {
        if (item == null || item.type.isAir) return false
        val limit = getTargetLimit(item.type) ?: return false

        val currentMax = item.getData(DataComponentTypes.MAX_STACK_SIZE)
        if (currentMax == null || currentMax != limit) {
            item.setData(DataComponentTypes.MAX_STACK_SIZE, limit)
            return true
        }
        return false
    }

    private fun updateInventory(inventory: Inventory?): Boolean {
        if (inventory == null) return false
        var modified = false
        for (item in inventory.contents) {
            if (isStackTarget(item)) {
                if (updateStackSize(item)) {
                    modified = true
                }
            }
        }
        return modified
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        if (updateInventory(event.player.inventory)) {
            event.player.updateInventory()
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onInventoryOpen(event: InventoryOpenEvent) {
        val modTop = updateInventory(event.inventory)
        val modBot = updateInventory(event.player.inventory)
        if (modTop || modBot) {
            (event.player as? Player)?.updateInventory()
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onInventoryClick(event: InventoryClickEvent) {
        if (event is InventoryCreativeEvent) return
        if (event.slot < 0) return

        val cursor = event.cursor
        val currentItem = event.currentItem

        if (!isStackTarget(cursor) && !isStackTarget(currentItem)) return

        updateStackSize(currentItem)
        updateStackSize(cursor)

        val slot = event.slot
        val clickedInv = event.clickedInventory

        Bukkit.getScheduler().runTask(plugin, Runnable {
            var changed = false
            clickedInv?.let {
                if (slot in 0 until it.size) {
                    val slotItem = it.getItem(slot)
                    if (updateStackSize(slotItem)) {
                        it.setItem(slot, slotItem)
                        changed = true
                    }
                }
            }

            val player = event.whoClicked as? Player
            if (player != null && player.isOnline) {
                val cursorItem = player.itemOnCursor
                if (updateStackSize(cursorItem)) {
                    player.setItemOnCursor(cursorItem)
                    changed = true
                }
                if (changed) {
                    player.updateInventory()
                }
            }
        })
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onInventoryCreative(event: InventoryCreativeEvent) {
        val cursor = event.cursor
        val currentItem = event.currentItem

        if (!isStackTarget(cursor) && !isStackTarget(currentItem)) return

        updateStackSize(currentItem)
        if (updateStackSize(cursor)) {
            event.cursor = cursor
        }

        // NMS handleSetCreativeModeSlot calls setRemoteSlot(), which suppresses slot packets back to the client.
        // Calling player.updateInventory() on the next tick forces the client to receive the new max_stack_size component!
        Bukkit.getScheduler().runTask(plugin, Runnable {
            val player = event.whoClicked as? Player ?: return@Runnable
            if (player.isOnline) {
                updateInventory(player.inventory)
                player.updateInventory()
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

        Bukkit.getScheduler().runTask(plugin, Runnable {
            for (slot in event.newItems.keys) {
                if (slot in 0 until event.view.topInventory.size + event.view.bottomInventory.size) {
                    val slotItem = event.view.getItem(slot)
                    if (isStackTarget(slotItem)) {
                        updateStackSize(slotItem)
                        event.view.setItem(slot, slotItem)
                    }
                }
            }
            val player = event.whoClicked as? Player
            if (player != null && player.isOnline) {
                val cursorItem = player.itemOnCursor
                if (updateStackSize(cursorItem)) {
                    player.setItemOnCursor(cursorItem)
                }
                player.updateInventory()
            }
        })
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onItemSpawn(event: ItemSpawnEvent) {
        val itemEntity = event.entity
        val item = itemEntity.itemStack
        if (updateStackSize(item)) {
            itemEntity.itemStack = item
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onItemPickup(event: EntityPickupItemEvent) {
        val itemEntity = event.item
        val item = itemEntity.itemStack
        if (updateStackSize(item)) {
            itemEntity.itemStack = item
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onBrew(event: BrewEvent) {
        updateInventory(event.contents)
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onCraft(event: CraftItemEvent) {
        val result = event.recipe.result
        if (!isStackTarget(result)) return

        updateStackSize(event.currentItem)
        updateStackSize(event.cursor)
        updateStackSize(result)

        Bukkit.getScheduler().runTask(plugin, Runnable {
            val player = event.whoClicked as? Player ?: return@Runnable
            if (player.isOnline && updateInventory(player.inventory)) {
                player.updateInventory()
            }
        })
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onPlayerInteract(event: PlayerInteractEvent) {
        if (!event.hasItem()) return
        val item = event.item ?: return
        if (updateStackSize(item)) {
            event.player.inventory.setItem(event.hand ?: org.bukkit.inventory.EquipmentSlot.HAND, item)
        }
    }
}