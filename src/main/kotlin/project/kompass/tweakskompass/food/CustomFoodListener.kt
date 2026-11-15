package project.kompass.tweakskompass.food

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.Statistic
import org.bukkit.Tag
import org.bukkit.block.data.type.Cake
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockCookEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.inventory.CraftItemEvent
import org.bukkit.event.inventory.FurnaceSmeltEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCreativeEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.player.*
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import project.kompass.tweakskompass.TweaksKompass

class CustomFoodListener(
    private val plugin: TweaksKompass,
    private val foodManager: CustomFoodManager
) : Listener {

    private fun isFoodEnabled(): Boolean {
        return plugin.config.getBoolean("custom-foods.enabled", true)
    }

    // 1. Cake Eating (Always-Eat & Custom Slice Stats)
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onCakeInteract(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_BLOCK) return
        if (event.hand != EquipmentSlot.HAND) return

        val block = event.clickedBlock ?: return
        val blockType = block.type
        val isNormalCake = blockType == Material.CAKE
        val isCandleCake = Tag.CANDLE_CAKES.isTagged(blockType)

        if (!isNormalCake && !isCandleCake) return
        if (!isFoodEnabled()) return

        val player = event.player
        if (player.gameMode == GameMode.SPECTATOR) return

        val heldItem = player.inventory.itemInMainHand
        if (isNormalCake && Tag.CANDLES.isTagged(heldItem.type)) {
            val cakeData = block.blockData as? Cake
            if (cakeData != null && cakeData.bites == 0) return
        }

        val isAlwaysEat = plugin.config.getBoolean("food-and-storage.always-eat.enabled", true)
        if (player.foodLevel >= 20 && !isAlwaysEat) return

        val configured = foodManager.getFood(Material.CAKE)
        val foodLevel = configured?.foodLevel ?: 2
        val saturation = configured?.saturation ?: 0.4f

        if (configured != null && isOnCooldown(player, configured)) {
            event.isCancelled = true
            return
        }

        event.isCancelled = true

        if (configured != null) {
            recordCooldown(player, configured)
        }

        player.foodLevel = minOf(20, player.foodLevel + foodLevel)
        player.saturation = minOf(player.foodLevel.toFloat(), player.saturation + saturation)

        val soundLoc = block.location.add(0.5, 0.5, 0.5)
        block.world.playSound(soundLoc, Sound.ENTITY_GENERIC_EAT, 1.0f, 1.0f)

        if (configured != null) {
            executeFoodEffects(player, configured)
        }

        player.incrementStatistic(Statistic.CAKE_SLICES_EATEN)

        if (isCandleCake) {
            val candleMat = resolveCandleMaterial(blockType)
            if (candleMat != null) {
                block.world.dropItemNaturally(soundLoc, ItemStack(candleMat))
            }
            block.setType(Material.CAKE, false)
            val newCakeData = block.blockData as? Cake
            if (newCakeData != null) {
                newCakeData.bites = 1
                block.blockData = newCakeData
            }
        } else {
            val cakeData = block.blockData as? Cake ?: return
            val nextBites = cakeData.bites + 1
            if (nextBites > cakeData.maximumBites) {
                block.type = Material.AIR
            } else {
                cakeData.bites = nextBites
                block.blockData = cakeData
            }
        }
    }

    private fun resolveCandleMaterial(cakeType: Material): Material? {
        if (cakeType == Material.CANDLE_CAKE) return Material.CANDLE
        val prefix = cakeType.name.removeSuffix("_CANDLE_CAKE")
        return Material.getMaterial("${prefix}_CANDLE")
    }

    // 2. Cooking & Furnace Sync
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onFurnaceSmelt(event: FurnaceSmeltEvent) {
        if (!isFoodEnabled()) return
        val result = event.result
        if (foodManager.isConfiguredFood(result.type)) {
            foodManager.applyFoodComponents(result)
            event.result = result
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onCampfireCook(event: BlockCookEvent) {
        if (!isFoodEnabled()) return
        val result = event.result
        if (foodManager.isConfiguredFood(result.type)) {
            foodManager.applyFoodComponents(result)
            event.result = result
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onMobFoodDrops(event: EntityDeathEvent) {
        if (!isFoodEnabled()) return
        for (drop in event.drops) {
            if (foodManager.isConfiguredFood(drop.type)) {
                foodManager.applyFoodComponents(drop)
            }
        }
    }

    // 3. Inventory Clicking & Creative Guard (Prevents Creative Cursor Duplication)
    @EventHandler(priority = EventPriority.LOWEST)
    fun onInventoryClick(event: InventoryClickEvent) {
        if (!isFoodEnabled()) return

        // In Creative mode, event.cursor is the slot payload, NOT the mouse cursor.
        // Calling event.view.setCursor() here duplicates items onto the cursor!
        if (event is InventoryCreativeEvent) {
            val creativeItem = event.cursor
            if (foodManager.isConfiguredFood(creativeItem.type)) {
                foodManager.applyFoodComponents(creativeItem)
                event.cursor = creativeItem
            }
            return
        }

        val player = event.whoClicked as? Player ?: return
        val current = event.currentItem
        val cursor = event.cursor

        if (current != null && foodManager.isConfiguredFood(current.type)) {
            foodManager.applyFoodComponents(current)
        }
        if (cursor != null && foodManager.isConfiguredFood(cursor.type)) {
            foodManager.applyFoodComponents(cursor)
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

    // 4. Standard Eating (Non-Instant Foods)
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
        executeFoodCommands(player, configured)
    }

    // 5. Instant Eat
    @EventHandler(priority = EventPriority.HIGH)
    fun onInstantEat(event: PlayerInteractEvent) {
        if (!isFoodEnabled()) return
        if (event.action != Action.RIGHT_CLICK_AIR && event.action != Action.RIGHT_CLICK_BLOCK) return

        val player = event.player
        val hand = event.hand ?: return
        val item = player.inventory.getItem(hand)
        if (item.type.isAir) return

        val configured = foodManager.getFood(item.type) ?: return
        if (!configured.instantEat) return
        if (!foodManager.isVanillaFood(item)) return

        event.isCancelled = true

        if (isOnCooldown(player, configured)) return

        recordCooldown(player, configured)

        if (player.gameMode != GameMode.CREATIVE) {
            if (item.amount <= 1) {
                player.inventory.setItem(hand, null)
            } else {
                item.amount -= 1
                player.inventory.setItem(hand, item)
            }
        }

        player.foodLevel = minOf(20, player.foodLevel + configured.foodLevel)
        player.saturation = minOf(player.foodLevel.toFloat(), player.saturation + configured.saturation)

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