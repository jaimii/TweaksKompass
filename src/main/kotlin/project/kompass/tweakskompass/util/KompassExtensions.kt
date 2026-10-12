package project.kompass.tweakskompass.util

import org.bukkit.Material
import org.bukkit.inventory.ItemStack

fun ItemStack?.isTrident(): Boolean {
    return this != null && this.type == Material.TRIDENT
}

fun ItemStack?.isSpear(): Boolean {
    return this != null && TridentUtil.SPEARS.contains(this.type)
}

fun ItemStack?.isDamageDealingTool(): Boolean {
    return this != null && TridentUtil.DAMAGE_TOOLS.contains(this.type)
}

fun ItemStack?.isPotionOrSoup(): Boolean {
    if (this == null || this.type == Material.AIR) return false
    val type = this.type
    return type == Material.POTION || type == Material.SPLASH_POTION || type == Material.LINGERING_POTION ||
            type == Material.MUSHROOM_STEW || type == Material.RABBIT_STEW ||
            type == Material.BEETROOT_SOUP || type == Material.SUSPICIOUS_STEW
}

fun ItemStack?.isBundle(): Boolean {
    return this != null && TridentUtil.BUNDLE_MATERIALS.contains(this.type)
}

fun ItemStack?.isFood(): Boolean {
    if (this == null || this.type == Material.AIR) return false
    if (this.type.isEdible) return true

    // Check modern Paper FoodComponent
    val meta = this.itemMeta
    if (meta != null && meta.hasFood()) return true

    return false
}