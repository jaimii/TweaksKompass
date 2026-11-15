package project.kompass.tweakskompass.food

import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.Consumable
import io.papermc.paper.datacomponent.item.consumable.ConsumeEffect
import io.papermc.paper.datacomponent.item.consumable.ItemUseAnimation
import net.kyori.adventure.key.Key
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Registry
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import project.kompass.tweakskompass.TweaksKompass
import java.util.EnumMap
import java.util.UUID
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

            // Parse potion effects using non-deprecated 1.21+ Registry
            val effectEntries = mutableListOf<FoodEffectEntry>()
            val rawEffects = plugin.config.getStringList("$path.PotionEffect")
            for (raw in rawEffects) {
                val parts = raw.split(":")
                if (parts.size >= 4) {
                    val effectName = parts[0].trim().lowercase()
                    val effectKey = NamespacedKey.fromString(effectName) ?: NamespacedKey.minecraft(effectName)
                    val effectType = Registry.POTION_EFFECT_TYPE.get(effectKey) ?: continue
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

            val dummyMeta = Bukkit.getItemFactory().getItemMeta(material)
            val foodComp = dummyMeta.food
            foodComp.nutrition = foodLevel
            foodComp.saturation = saturation

            val isEdible = material.isEdible
            var consumableComp: Consumable? = null

            // Do not build a hand-consumable component for placeable CAKE blocks
            if (material != Material.CAKE && (!isEdible || effectEntries.isNotEmpty())) {
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

    // Only mutates items that genuinely require custom DataComponents
    fun applyFoodComponents(item: ItemStack) {
        // Never attach hand-consumable components to placeable CAKE block items
        if (item.type.isAir || item.type == Material.CAKE) return
        val configured = foodMap[item.type] ?: return

        // If the item is already vanilla edible and has no custom effects/commands/instant-eat,
        // do NOT mutate its components so it stacks 100% natively with furnaces, mob drops, and chests.
        if (configured.material.isEdible && configured.effects.isEmpty() && configured.commands.isEmpty() && !configured.instantEat) {
            return
        }

        val meta = item.itemMeta ?: return
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
        return meta == null || (!meta.hasDisplayName() && !meta.hasLore())
    }
}