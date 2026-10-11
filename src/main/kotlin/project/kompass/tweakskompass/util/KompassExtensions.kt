package project.kompass.tweakskompass.util

import project.kompass.tweakskompass.hook.ConsumeFood2Hook
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

// Top-level extension functions placed in a dedicated file to guarantee clean JAR packaging
fun ItemStack?.isTrident(): Boolean {
    return this != null && this.type == Material.TRIDENT
}

fun ItemStack?.isSpear(): Boolean {
    return this != null && KompassUtil.SPEARS.contains(this.type)
}

fun ItemStack?.isDamageDealingTool(): Boolean {
    return this != null && KompassUtil.DAMAGE_TOOLS.contains(this.type)
}

fun ItemStack?.isPotionOrSoup(): Boolean {
    if (this == null || this.type == Material.AIR) return false
    val type = this.type
    return type == Material.POTION || type == Material.SPLASH_POTION || type == Material.LINGERING_POTION ||
            type == Material.MUSHROOM_STEW || type == Material.RABBIT_STEW ||
            type == Material.BEETROOT_SOUP || type == Material.SUSPICIOUS_STEW
}

fun ItemStack?.isBundle(): Boolean {
    return this != null && KompassUtil.BUNDLE_MATERIALS.contains(this.type)
}

fun ItemStack?.isFood(): Boolean {
    if (this == null || this.type == Material.AIR) return false

    // 1. Check native vanilla edible flag
    if (this.type.isEdible) return true

    // 2. Check if registered inside ConsumeFood2
    if (ConsumeFood2Hook.isConsumeFoodMaterial(this.type)) return true

    // 3. Check if carrying a Paper FoodComponent
    if (this.hasItemMeta()) {
        val meta = this.itemMeta
        if (meta != null && meta.hasFood()) return true
    }

    return false
}