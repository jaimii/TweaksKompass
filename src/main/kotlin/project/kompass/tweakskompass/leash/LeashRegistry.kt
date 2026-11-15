package project.kompass.tweakskompass.leash

import org.bukkit.entity.EntityType
import java.util.EnumSet

class LeashRegistry {

    private val _leashableInVanilla: EnumSet<EntityType> = EnumSet.noneOf(EntityType::class.java)
    val leashableInVanilla: Set<EntityType> get() = _leashableInVanilla

    init {
        setupVanillaList()
    }

    private fun setupVanillaList() {
        val standardTypes = listOf(
            "ALLAY", "ARMADILLO", "AXOLOTL", "BEE", "CAMEL", "CAT", "CHICKEN", "COW",
            "DOLPHIN", "DONKEY", "FOX", "FROG", "GLOW_SQUID", "GOAT", "HOGLIN", "HORSE",
            "IRON_GOLEM", "LLAMA", "TRADER_LLAMA", "MOOSHROOM", "MULE", "OCELOT",
            "PARROT", "PIG", "POLAR_BEAR", "RABBIT", "SHEEP", "SKELETON_HORSE", "SNIFFER",
            "SNOW_GOLEM", "SQUID", "STRIDER", "WOLF", "ZOGLIN", "ZOMBIE_HORSE",
            // 26.1 - 26.3 & Mounts of Mayhem additions
            "COPPER_GOLEM", "CAMEL_HUSK", "NAUTILUS", "ZOMBIE_NAUTILUS", "HAPPY_GHAST", "SULFUR_CUBE"
        )

        for (name in standardTypes) {
            addIfPresent(name)
        }

        // Dynamically include all boat and raft variants (including 26.3 Poplar and Bamboo)
        for (type in EntityType.entries) {
            if (type.name.contains("BOAT") || type.name.contains("RAFT")) {
                _leashableInVanilla.add(type)
            }
        }
    }

    private fun addIfPresent(name: String) {
        try {
            val type = EntityType.valueOf(name.uppercase())
            _leashableInVanilla.add(type)
        } catch (ignored: IllegalArgumentException) {
            // Entity type not present on current runtime; safely skipped
        }
    }

    fun isLeashableInVanilla(type: EntityType): Boolean {
        return _leashableInVanilla.contains(type)
    }
}