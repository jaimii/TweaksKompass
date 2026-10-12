package project.kompass.tweakskompass.food

import io.papermc.paper.datacomponent.item.Consumable
import org.bukkit.Material
import org.bukkit.inventory.meta.components.FoodComponent
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

enum class FoodCooldownType {
    DISABLE, GLOBAL, PERSONAL
}

data class FoodEffectEntry(
    val effect: PotionEffect,
    val chance: Float
)

data class FoodCommandEntry(
    val isConsole: Boolean,
    val command: String
)

data class ConfiguredFood(
    val material: Material,
    val foodLevel: Int,
    val saturation: Float,
    val cooldownSeconds: Double,
    val instantEat: Boolean,
    val effects: List<FoodEffectEntry>,
    val commands: List<FoodCommandEntry>,
    val foodComponent: FoodComponent,
    val consumableComponent: Consumable?
)