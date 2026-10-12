package project.kompass.tweakskompass.food

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.inventory.CraftItemEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.player.*
import org.bukkit.inventory.ItemStack
import project.kompass.tweakskompass.TweaksKompass

class CustomFoodListener(
    private val plugin: TweaksKompass,
    private val foodManager: CustomFoodManager
) : Listener {

    private fun isFoodEnabled(): Boolean {
        return plugin.config.getBoolean("custom-foods.enabled", true)
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onInventoryClick(event: InventoryClickEvent) {
        if (!isFoodEnabled()) return
        val player = event.whoClicked as? Player ?: return

        val current = event.currentItem
        val cursor = event.cursor

        if (current != null && foodManager.isConfiguredFood(current.type)) {
            foodManager.applyFoodComponents(current)
            event.currentItem = current
        }
        if (cursor != null && foodManager.isConfiguredFood(cursor.type)) {
            foodManager.applyFoodComponents(cursor)
            event.view.setCursor(cursor)
        }

        if (event.slotType == InventoryType.SlotType.RESULT || event.inventory.type == InventoryType.MERCHANT) {
            Bukkit.getScheduler().runTask(plugin, Runnable {
                if (player.isOnline) {
                    for (item in player.inventory.storageContents) {
                        if (item != null && foodManager.isConfiguredFood(item.type)) {
                            foodManager.applyFoodComponents(item)
                        }
                    }
                }
            })
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onCraft(event: CraftItemEvent) {
        if (!isFoodEnabled()) return
        val player = event.whoClicked as? Player ?: return

        Bukkit.getScheduler().runTask(plugin, Runnable {
            if (player.isOnline) {
                for (item in player.inventory.storageContents) {
                    if (item != null && foodManager.isConfiguredFood(item.type)) {
                        foodManager.applyFoodComponents(item)
                    }
                }
            }
        })
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onPickup(event: EntityPickupItemEvent) {
        if (!isFoodEnabled()) return
        val item = event.item.itemStack

        if (foodManager.isConfiguredFood(item.type)) {
            foodManager.applyFoodComponents(item)
            event.item.itemStack = item
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onJoin(event: PlayerJoinEvent) {
        if (!isFoodEnabled()) return
        for (item in event.player.inventory.storageContents) {
            if (item != null && foodManager.isConfiguredFood(item.type)) {
                foodManager.applyFoodComponents(item)
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onHeld(event: PlayerItemHeldEvent) {
        if (!isFoodEnabled()) return
        val item = event.player.inventory.getItem(event.newSlot) ?: return
        if (foodManager.isConfiguredFood(item.type)) {
            foodManager.applyFoodComponents(item)
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerConsume(event: PlayerItemConsumeEvent) {
        if (!isFoodEnabled()) return
        val player = event.player
        val item = event.item
        val configured = foodManager.getFood(item.type) ?: return

        if (!foodManager.isVanillaFood(item)) return

        if (isOnCooldown(player, configured)) {
            event.isCancelled = true
            return
        }

        recordCooldown(player, configured)
        // Consumable component natively handles potion effects in Paper 1.21; only execute commands
        executeFoodCommands(player, configured)
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onInstantEat(event: PlayerInteractEvent) {
        if (!isFoodEnabled()) return
        if (event.action != Action.RIGHT_CLICK_AIR && event.action != Action.RIGHT_CLICK_BLOCK) return

        val player = event.player
        val hand = event.hand ?: return
        val item = player.inventory.getItem(hand)
        if (item == null || item.type.isAir) return

        val configured = foodManager.getFood(item.type) ?: return
        if (!configured.instantEat) return
        if (!foodManager.isVanillaFood(item)) return

        event.isCancelled = true

        if (isOnCooldown(player, configured)) return

        recordCooldown(player, configured)

        // Deduct 1 item safely
        if (player.gameMode != org.bukkit.GameMode.CREATIVE) {
            if (item.amount <= 1) {
                player.inventory.setItem(hand, null)
            } else {
                item.amount -= 1
                player.inventory.setItem(hand, item)
            }
        }

        player.foodLevel = minOf(20, player.foodLevel + configured.foodLevel)
        player.saturation = minOf(player.foodLevel.toFloat(), player.saturation + configured.saturation)

        // Return containers safely
        val container = when {
            configured.material.name.endsWith("_STEW") || configured.material == Material.BEETROOT_SOUP -> ItemStack(Material.BOWL)
            configured.material == Material.HONEY_BOTTLE -> ItemStack(Material.GLASS_BOTTLE)
            else -> null
        }
        if (container != null) {
            val leftovers = player.inventory.addItem(container)
            for (drop in leftovers.values) {
                player.world.dropItemNaturally(player.location, drop)
            }
        }

        player.world.playSound(player.location, Sound.ENTITY_GENERIC_EAT, 1.0f, 1.0f)
        executeFoodEffects(player, configured)
    }

    private fun executeFoodEffects(player: Player, food: ConfiguredFood) {
        for (entry in food.effects) {
            if (Math.random() <= entry.chance) {
                player.addPotionEffect(entry.effect)
            }
        }
        executeFoodCommands(player, food)
    }

    private fun executeFoodCommands(player: Player, food: ConfiguredFood) {
        for (cmd in food.commands) {
            val parsed = parsePlaceholders(player, cmd.command)
            if (cmd.isConsole) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed)
            } else {
                player.performCommand(parsed)
            }
        }
    }

    private fun parsePlaceholders(player: Player, text: String): String {
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                val papiClass = Class.forName("me.clip.placeholderapi.PlaceholderAPI")
                val setPlaceholders = papiClass.getMethod("setPlaceholders", Player::class.java, String::class.java)
                return setPlaceholders.invoke(null, player, text) as String
            } catch (ignored: Throwable) {}
        }
        return text.replace("%player_name%", player.name)
    }

    private fun isOnCooldown(player: Player, food: ConfiguredFood): Boolean {
        val now = System.currentTimeMillis()
        val uuid = player.uniqueId

        when (foodManager.cooldownType) {
            FoodCooldownType.DISABLE -> return false
            FoodCooldownType.GLOBAL -> {
                val until = foodManager.globalCooldowns[uuid] ?: 0L
                if (until > now) {
                    val remaining = ((until - now) / 1000.0).coerceAtLeast(0.1)
                    player.sendMessage(Component.text("You must wait %.1fs before eating again!".format(remaining), NamedTextColor.RED))
                    return true
                }
            }
            FoodCooldownType.PERSONAL -> {
                val userMap = foodManager.personalCooldowns[uuid]
                val until = userMap?.get(food.material) ?: 0L
                if (until > now) {
                    val remaining = ((until - now) / 1000.0).coerceAtLeast(0.1)
                    player.sendMessage(Component.text("You must wait %.1fs before eating %s again!".format(remaining, food.material.name), NamedTextColor.RED))
                    return true
                }
            }
        }
        return false
    }

    private fun recordCooldown(player: Player, food: ConfiguredFood) {
        val now = System.currentTimeMillis()
        val uuid = player.uniqueId

        when (foodManager.cooldownType) {
            FoodCooldownType.DISABLE -> {}
            FoodCooldownType.GLOBAL -> {
                foodManager.globalCooldowns[uuid] = now + (foodManager.globalCooldownSeconds * 1000).toLong()
            }
            FoodCooldownType.PERSONAL -> {
                if (food.cooldownSeconds > 0) {
                    val userMap = foodManager.personalCooldowns.computeIfAbsent(uuid) { java.util.concurrent.ConcurrentHashMap() }
                    userMap[food.material] = now + (food.cooldownSeconds * 1000).toLong()
                }
            }
        }
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        val uuid = event.player.uniqueId
        foodManager.globalCooldowns.remove(uuid)
        foodManager.personalCooldowns.remove(uuid)
    }
}