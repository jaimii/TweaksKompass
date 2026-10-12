package project.kompass.tweakskompass.listener

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

    private fun updateStackSize(item: ItemStack?) {
        if (item == null || item.type.isAir) return
        if (!plugin.config.getBoolean("food-and-storage.custom-stacks.enabled", true)) return

        val type = item.type
        val potionLimit = plugin.config.getInt("food-and-storage.custom-stacks.potions", 8)
        val soupLimit = plugin.config.getInt("food-and-storage.custom-stacks.soups", 16)
        val cakeLimit = plugin.config.getInt("food-and-storage.custom-stacks.cakes", 16)
        val totemLimit = plugin.config.getInt("food-and-storage.custom-stacks.totems", 4)

        if (type == Material.POTION || type == Material.SPLASH_POTION || type == Material.LINGERING_POTION) {
            val meta = item.itemMeta
            if (meta != null && (!meta.hasMaxStackSize() || meta.maxStackSize != potionLimit)) {
                meta.setMaxStackSize(potionLimit)
                item.itemMeta = meta
            }
        } else if (type == Material.MUSHROOM_STEW || type == Material.RABBIT_STEW ||
            type == Material.BEETROOT_SOUP || type == Material.SUSPICIOUS_STEW) {
            val meta = item.itemMeta
            if (meta != null && (!meta.hasMaxStackSize() || meta.maxStackSize != soupLimit)) {
                meta.setMaxStackSize(soupLimit)
                item.itemMeta = meta
            }
        } else if (type == Material.CAKE) {
            val meta = item.itemMeta
            if (meta != null && (!meta.hasMaxStackSize() || meta.maxStackSize != cakeLimit)) {
                meta.setMaxStackSize(cakeLimit)
                item.itemMeta = meta
            }
        } else if (type == Material.TOTEM_OF_UNDYING) {
            val meta = item.itemMeta
            if (meta != null && (!meta.hasMaxStackSize() || meta.maxStackSize != totemLimit)) {
                meta.setMaxStackSize(totemLimit)
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
        // Critical: Drop click guard prevents ArrayIndexOutOfBoundsException (-999)
        if (event.slot < 0) return

        val cursor = event.cursor
        val currentItem = event.currentItem

        if (!isStackTarget(cursor) && !isStackTarget(currentItem)) return

        updateStackSize(currentItem)
        updateStackSize(cursor)

        val slot = event.slot
        val clickedInv = event.clickedInventory

        Bukkit.getScheduler().runTask(plugin, Runnable {
            clickedInv?.let {
                if (slot in 0 until it.size) {
                    val slotItem = it.getItem(slot)
                    if (isStackTarget(slotItem)) {
                        updateStackSize(slotItem)
                        it.setItem(slot, slotItem)
                    }
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
        if (event.slot < 0) return

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
                    isPotion -> plugin.config.getInt("food-and-storage.custom-stacks.potions", 8)
                    isSoup -> plugin.config.getInt("food-and-storage.custom-stacks.soups", 16)
                    isCake -> plugin.config.getInt("food-and-storage.custom-stacks.cakes", 16)
                    isTotem -> plugin.config.getInt("food-and-storage.custom-stacks.totems", 4)
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

        val slot = event.slot
        val clickedInv = event.clickedInventory

        Bukkit.getScheduler().runTask(plugin, Runnable {
            val player = event.whoClicked as? Player ?: return@Runnable
            if (player.isOnline) {
                val cursorItem = player.itemOnCursor
                if (isStackTarget(cursorItem)) {
                    updateStackSize(cursorItem)
                    player.setItemOnCursor(cursorItem)
                }

                clickedInv?.let {
                    if (slot in 0 until it.size) {
                        val slotItem = it.getItem(slot)
                        if (isStackTarget(slotItem)) {
                            updateStackSize(slotItem)
                            it.setItem(slot, slotItem)
                        }
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