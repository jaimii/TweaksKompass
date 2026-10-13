package project.kompass.tweakskompass.crafting

import org.bukkit.Bukkit
import org.bukkit.DyeColor
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.*
import project.kompass.tweakskompass.TweaksKompass

object RecipeManager {

    fun registerRecipes(plugin: TweaksKompass) {
        registerSmeltingRecipes(plugin)
        registerRedyeingRecipes(plugin)
    }

    private fun registerSmeltingRecipes(plugin: TweaksKompass) {
        val orePairs = listOf(
            Triple(Material.RAW_IRON_BLOCK, Material.IRON_BLOCK, "iron"),
            Triple(Material.RAW_COPPER_BLOCK, Material.COPPER_BLOCK, "copper"),
            Triple(Material.RAW_GOLD_BLOCK, Material.GOLD_BLOCK, "gold")
        )

        for ((raw, block, name) in orePairs) {
            val furnaceKey = NamespacedKey(plugin, "smelt_raw_${name}_block")
            if (Bukkit.getRecipe(furnaceKey) == null) {
                val recipe = FurnaceRecipe(furnaceKey, ItemStack(block), raw, 6.3f, 200)
                Bukkit.addRecipe(recipe)
            }

            val blastKey = NamespacedKey(plugin, "blast_raw_${name}_block")
            if (Bukkit.getRecipe(blastKey) == null) {
                val recipe = BlastingRecipe(blastKey, ItemStack(block), raw, 6.3f, 100)
                Bukkit.addRecipe(recipe)
            }
        }

        val sandTypes = listOf(
            Pair(Material.SAND, "sand"),
            Pair(Material.RED_SAND, "red_sand")
        )
        for ((sand, name) in sandTypes) {
            val key = NamespacedKey(plugin, "blast_${name}_to_glass")
            if (Bukkit.getRecipe(key) == null) {
                val recipe = BlastingRecipe(key, ItemStack(Material.GLASS), sand, 0.1f, 100)
                Bukkit.addRecipe(recipe)
            }
        }
    }

    private fun registerRedyeingRecipes(plugin: TweaksKompass) {
        val allConcrete = Material.entries.filter { it.name.endsWith("_CONCRETE") && !it.name.contains("POWDER") }
        val allPowder = Material.entries.filter { it.name.endsWith("_CONCRETE_POWDER") }
        val allTerracotta = Material.entries.filter { it.name.endsWith("_TERRACOTTA") && it != Material.TERRACOTTA }

        // Pre-allocate choices once to avoid heap allocation churn inside the loop
        val concreteChoice = RecipeChoice.ExactChoice(allConcrete.map { ItemStack(it) })
        val powderChoice = RecipeChoice.ExactChoice(allPowder.map { ItemStack(it) })
        val terracottaChoice = RecipeChoice.ExactChoice(allTerracotta.map { ItemStack(it) })

        for (color in DyeColor.entries) {
            val dye = Material.getMaterial("${color.name}_DYE") ?: continue

            // 1. Concrete
            val targetConcrete = Material.getMaterial("${color.name}_CONCRETE")
            if (targetConcrete != null) {
                val key = NamespacedKey(plugin, "redye_concrete_${color.name.lowercase()}")
                if (Bukkit.getRecipe(key) == null) {
                    val recipe = ShapedRecipe(key, ItemStack(targetConcrete, 8))
                    recipe.shape("###", "#D#", "###")
                    recipe.setIngredient('#', concreteChoice)
                    recipe.setIngredient('D', dye)
                    Bukkit.addRecipe(recipe)
                }
            }

            // 2. Concrete Powder
            val targetPowder = Material.getMaterial("${color.name}_CONCRETE_POWDER")
            if (targetPowder != null) {
                val key = NamespacedKey(plugin, "redye_powder_${color.name.lowercase()}")
                if (Bukkit.getRecipe(key) == null) {
                    val recipe = ShapedRecipe(key, ItemStack(targetPowder, 8))
                    recipe.shape("###", "#D#", "###")
                    recipe.setIngredient('#', powderChoice)
                    recipe.setIngredient('D', dye)
                    Bukkit.addRecipe(recipe)
                }
            }

            // 3. Stained Terracotta
            val targetTerracotta = Material.getMaterial("${color.name}_TERRACOTTA")
            if (targetTerracotta != null) {
                val key = NamespacedKey(plugin, "redye_terracotta_${color.name.lowercase()}")
                if (Bukkit.getRecipe(key) == null) {
                    val recipe = ShapedRecipe(key, ItemStack(targetTerracotta, 8))
                    recipe.shape("###", "#D#", "###")
                    recipe.setIngredient('#', terracottaChoice)
                    recipe.setIngredient('D', dye)
                    Bukkit.addRecipe(recipe)
                }
            }
        }
    }
}