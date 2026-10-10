package project.kompass.tweakskompass.hook

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.plugin.java.JavaPlugin
import java.util.EnumSet

object ConsumeFood2Hook {

    private val consumeFoodMaterials: MutableSet<Material> = EnumSet.noneOf(Material::class.java)
    private var isHooked = false

    fun hookAlwaysEat(plugin: JavaPlugin) {
        val pluginManager = Bukkit.getPluginManager()
        val consumeFoodPlugin = pluginManager.getPlugin("ConsumeFood2") ?: return
        if (!pluginManager.isPluginEnabled(consumeFoodPlugin)) return

        try {
            // Query the VanillaFoodManager instance
            val getVanillaFoodManager = consumeFoodPlugin.javaClass.getMethod("getVanillaFoodManager")
            val foodManager = getVanillaFoodManager.invoke(consumeFoodPlugin) ?: return

            // Query all registered materials (both vanilla and custom non-vanilla foods)
            val getVanillaFoodMaterials = foodManager.javaClass.getMethod("getVanillaFoodMaterials")
            @Suppress("UNCHECKED_CAST")
            val materials = getVanillaFoodMaterials.invoke(foodManager) as? List<Material> ?: return

            val getVanillaFood = foodManager.javaClass.getMethod("getVanillaFood", Material::class.java)

            // Locate the Options enum class
            val optionsClass = Class.forName("me.msicraft.API.Food.Food\$Options")
            val alwaysEatOption = optionsClass.getField("ALWAYS_EAT").get(null)

            consumeFoodMaterials.clear()
            for (material in materials) {
                consumeFoodMaterials.add(material)

                val vanillaFood = getVanillaFood.invoke(foodManager, material) ?: continue

                // Force set ALWAYS_EAT option to true inside ConsumeFood2's data model
                val setOption = vanillaFood.javaClass.getMethod("setOption", optionsClass, Any::class.java)
                setOption.invoke(vanillaFood, alwaysEatOption, true)
            }

            isHooked = true
            plugin.logger.info("[BTK] Successfully synced ALWAYS_EAT across ${materials.size} ConsumeFood2 items (including non-vanilla foods).")
        } catch (e: Throwable) {
            plugin.logger.warning("[BTK] Failed to reflectively map ConsumeFood2 hooks: ${e.message}")
        }
    }

    // Nanosecond O(1) set check to verify if a non-vanilla item is managed by ConsumeFood2
    fun isConsumeFoodMaterial(type: Material): Boolean {
        return isHooked && consumeFoodMaterials.contains(type)
    }
}