package project.kompass.tweakskompass.util

import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import java.util.EnumSet

object TridentUtil {

    val THROWN_HAND_KEY = NamespacedKey("better_tridents", "thrown_hand")
    val RANGE_KEY = NamespacedKey("better_tridents", "reach_distance")
    val BLOCK_RANGE_KEY = NamespacedKey("better_tridents", "block_reach")
    val SPEED_KEY = NamespacedKey("better_tridents", "attack_speed")
    val CHANNELING_LIGHTNING_KEY = NamespacedKey("better_tridents", "channeling_lightning")
    val LOOTING_KEY = NamespacedKey("better_tridents", "looting_level")

    internal val DAMAGE_TOOLS: EnumSet<Material> = EnumSet.noneOf(Material::class.java)
    internal val SPEARS: EnumSet<Material> = EnumSet.noneOf(Material::class.java)
    internal val BUNDLE_MATERIALS: EnumSet<Material> = EnumSet.noneOf(Material::class.java)

    init {
        for (material in Material.entries) {
            val name = material.name
            if (name.startsWith("LEGACY_")) continue

            if (name.contains("SWORD") || name.contains("AXE") || name.contains("SPEAR") || name.contains("TRIDENT") || name.contains("MACE")) {
                DAMAGE_TOOLS.add(material)
            }
            if (name.contains("SPEAR")) {
                SPEARS.add(material)
            }
            if (name == "BUNDLE" || name.endsWith("_BUNDLE")) {
                BUNDLE_MATERIALS.add(material)
            }
        }
    }

    fun isVictimWet(victim: LivingEntity): Boolean {
        return victim.isInWater || victim.isInRain
    }

    fun isPlayerWet(player: Player): Boolean {
        return player.isInWater || player.isInRain
    }
}