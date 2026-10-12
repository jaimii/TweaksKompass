package project.kompass.tweakskompass.food

import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.Consumable
import io.papermc.paper.datacomponent.item.consumable.ConsumeEffect
import io.papermc.paper.datacomponent.item.consumable.ItemUseAnimation
import net.kyori.adventure.key.Key
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import project.kompass.tweakskompass.TweaksKompass
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class CustomFoodManager(private val plugin: TweaksKompass) {

    private val foodMap = EnumMap<Material, ConfiguredFood>(Material::class.java)
    val globalCooldowns = ConcurrentHashMap<UUID, Long>()
    val personalCooldowns = ConcurrentHashMap<UUID, MutableMap<Material, Long>>()

    var cooldownType = FoodCooldownType.DISABLE
        private set
    var globalCooldownSeconds = 0.0
        private set

    init {
        reload()
    }

    fun reload() {
        foodMap.clear()

        val cooldownTypeStr = plugin.config.getString("custom-foods.settings.cooldown-type", "DISABLE")?.uppercase() ?: "DISABLE"
        cooldownType = runCatching { FoodCooldownType.valueOf(cooldownTypeStr) }.getOrDefault(FoodCooldownType.DISABLE)
        globalCooldownSeconds = plugin.config.getDouble("custom-foods.settings.global-cooldown", 0.0)

        val foodSection = plugin.config.getConfigurationSection("custom-foods.items") ?: return

        for (key in foodSection.getKeys(false)) {
            val material = Material.getMaterial(key.uppercase()) ?: continue
            val path = "custom-foods.items.$key"

            val foodLevel = plugin.config.getInt("$path.FoodLevel", if (material.isEdible) 4 else 1)
            val saturation = plugin.config.getDouble("$path.Saturation", 2.0).toFloat()
            val cooldown = plugin.config.getDouble("$path.CoolDown", 0.0)
            val instantEat = plugin.config.getBoolean("$path.InstantEat", false)

            // Parse potion effects
            val effectEntries = mutableListOf<FoodEffectEntry>()
            val rawEffects = plugin.config.getStringList("$path.PotionEffect")
            for (raw in rawEffects) {
                val parts = raw.split(":")
                if (parts.size >= 4) {
                    val effectName = parts[0].trim().lowercase()
                    val effectKey = org.bukkit.NamespacedKey.fromString(effectName)
                        ?: org.bukkit.NamespacedKey.minecraft(effectName)

                    val effectType = org.bukkit.Registry.POTION_EFFECT_TYPE.get(effectKey) ?: continue
                    val level = parts[1].toIntOrNull()?.coerceAtLeast(1) ?: 1
                    val durationSec = parts[2].toIntOrNull() ?: 5
                    val chance = parts[3].toFloatOrNull()?.coerceIn(0.0f, 1.0f) ?: 1.0f

                    val effect = PotionEffect(effectType, durationSec * 20, level - 1)
                    effectEntries.add(FoodEffectEntry(effect, chance))
                }
            }

            // Parse commands
            val commandEntries = mutableListOf<FoodCommandEntry>()
            val rawCommands = plugin.config.getStringList("$path.Command")
            for (raw in rawCommands) {
                val parts = raw.split(":", limit = 2)
                if (parts.size == 2) {
                    val isConsole = parts[0].trim().equals("CONSOLE", ignoreCase = true)
                    commandEntries.add(FoodCommandEntry(isConsole, parts[1].trim()))
                }
            }

            // Pre-build FoodComponent using Bukkit ItemFactory
            val dummyMeta = Bukkit.getItemFactory().getItemMeta(material)
            val foodComp = dummyMeta.food
            foodComp.nutrition = foodLevel
            foodComp.saturation = saturation
            foodComp.setCanAlwaysEat(plugin.config.getBoolean("food-and-storage.always-eat.enabled", true))

            // Pre-build Consumable component for non-edible items or custom effects
            val isEdible = material.isEdible
            var consumableComp: Consumable? = null

            if (!isEdible || effectEntries.isNotEmpty()) {
                val builder = Consumable.consumable()
                if (!isEdible) {
                    builder.animation(ItemUseAnimation.EAT)
                    builder.sound(Key.key("entity.generic.eat"))
                    builder.hasConsumeParticles(true)
                }
                if (effectEntries.isNotEmpty()) {
                    val consumeEffects = effectEntries.map {
                        ConsumeEffect.applyStatusEffects(listOf(it.effect), it.chance)
                    }
                    builder.effects(consumeEffects)
                }
                consumableComp = builder.build()
            }

            foodMap[material] = ConfiguredFood(
                material, foodLevel, saturation, cooldown, instantEat,
                effectEntries, commandEntries, foodComp, consumableComp
            )
        }

        plugin.logger.info("[FoodSystem] Loaded and pre-cached ${foodMap.size} custom food configurations.")
    }

    fun getFood(material: Material): ConfiguredFood? = foodMap[material]

    fun isConfiguredFood(material: Material): Boolean = foodMap.containsKey(material)

    // Applies the exact pre-cached component instance to guarantee 100% item stackability
    fun applyFoodComponents(item: ItemStack) {
        if (item.type.isAir) return
        val configured = foodMap[item.type] ?: return

        val meta = item.itemMeta
        // Do not overwrite renamed or custom lore items
        if (meta.hasDisplayName() || meta.hasLore()) return

        meta.setFood(configured.foodComponent)
        item.itemMeta = meta

        if (configured.consumableComponent != null) {
            item.setData(DataComponentTypes.CONSUMABLE, configured.consumableComponent)
        }
    }

    fun isVanillaFood(item: ItemStack?): Boolean {
        if (item == null || item.type.isAir) return false
        if (!foodMap.containsKey(item.type)) return false
        val meta = item.itemMeta
        return !meta.hasDisplayName() && !meta.hasLore()
    }
}