package project.kompass.tweakskompass.leash

import org.bukkit.entity.EntityType
import java.util.EnumSet

class LeashRegistry {
    // EnumSet provides O(1) bitwise lookup operations
    private val leashableInVanilla: Set<EntityType> = EnumSet.noneOf(EntityType::class.java)

    init {
        setupVanillaList()
    }

    private fun setupVanillaList() {
        val standardTypes = listOf(
            "ALLAY", "ARMADILLO", "AXOLOTL", "BEE", "CAMEL", "CAT", "CHICKEN", "COW",
            "DOLPHIN", "DONKEY", "FOX", "FROG", "GLOW_SQUID", "GOAT", "HOGLIN", "HORSE",
            "IRON_GOLEM", "LLAMA", "TRADER_LLAMA", "MOOSHROOM", "MULE", "OCELOT",
            "PARROT", "PIG", "POLAR_BEAR", "RABBIT", "SHEEP", "SKELETON_HORSE", "SNIFFER",
            "SNOW_GOLEM", "SQUID", "STRIDER", "WOLF", "ZOGLIN", "ZOMBIE_HORSE"
        )

        for (name in standardTypes) {
            addIfPresent(name)
        }

        // Custom / Mounts of Mayhem specific entity types safely resolved
        val customTypes = listOf("COPPER_GOLEM", "CAMEL_HUSK", "NAUTILUS", "ZOMBIE_NAUTILUS")
        for (name in customTypes) {
            addIfPresent(name)
        }

        // Add all Boat and Chest Boat variants dynamically
        for (type in EntityType.entries) {
            if (type.name.contains("BOAT")) {
                (leashableInVanilla as EnumSet).add(type)
            }
        }
    }

    private fun addIfPresent(name: String) {
        try {
            val type = EntityType.valueOf(name.uppercase())
            (leashableInVanilla as EnumSet).add(type)
        } catch (ignored: IllegalArgumentException) {
            // Entity type not present on current runtime; skip safely
        }
    }

    fun isLeashableInVanilla(type: EntityType): Boolean {
        return leashableInVanilla.contains(type)
    }
}