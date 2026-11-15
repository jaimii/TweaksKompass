package project.kompass.tweakskompass.entity

import com.destroystokyo.paper.event.entity.PreCreatureSpawnEvent
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.World
import org.bukkit.entity.EntityType
import org.bukkit.entity.Shulker
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.CreatureSpawnEvent
import org.bukkit.generator.structure.Structure
import org.bukkit.persistence.PersistentDataType
import project.kompass.tweakskompass.TweaksKompass
import java.util.EnumSet
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.floor

class ShulkerRespawnListener(private val plugin: TweaksKompass) : Listener {

    private val respawnedTag = NamespacedKey(plugin, "respawned_shulker")

    companion object {
        private val PURPUR_MATERIALS: Set<Material> = EnumSet.of(
            Material.PURPUR_BLOCK,
            Material.PURPUR_PILLAR,
            Material.PURPUR_SLAB,
            Material.PURPUR_STAIRS
        )
    }

    private var isEnabled = true
    private var spawnChance = 0.75
    private var minRadiusBetweenShulkers = 10.0
    private var minRadiusSq = 100.0

    init {
        reloadConfig()
    }

    fun reloadConfig() {
        val cfg = plugin.config
        isEnabled = cfg.getBoolean("shulker-respawner.enabled", true)
        spawnChance = cfg.getDouble("shulker-respawner.enderman-to-shulker-chance", 0.75).coerceIn(0.0, 1.0)
        minRadiusBetweenShulkers = cfg.getDouble("shulker-respawner.radius-between-spawns", 10.0).coerceAtLeast(1.0)
        minRadiusSq = minRadiusBetweenShulkers * minRadiusBetweenShulkers
    }

    // Intercepts Enderman spawn before entity instantiation on the heap
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onPreEndermanSpawn(event: PreCreatureSpawnEvent) {
        if (!isEnabled) return
        if (event.type != EntityType.ENDERMAN) return
        if (event.reason != CreatureSpawnEvent.SpawnReason.NATURAL) return

        val loc = event.spawnLocation
        val world = loc.world ?: return
        if (world.environment != World.Environment.THE_END) return

        // 1. Roll probability FIRST before doing any block, structure, or chunk queries
        if (ThreadLocalRandom.current().nextDouble() >= spawnChance) return

        val bx = loc.blockX
        val by = loc.blockY
        val bz = loc.blockZ

        // 2. Fast Purpur Foundation Check (Zero CraftBlock allocations)
        if (!hasPurpurFoundation(world, bx, by, bz)) return

        // 3. Native Paper 26.3 Structure Check
        if (!world.hasStructureAt(loc, Structure.END_CITY)) return

        // 4. Zero-allocation chunk scan for nearby Shulkers
        if (hasShulkerNearby(world, loc)) return

        // 5. Cancel Enderman pre-spawn and spawn Shulker
        event.setCancelled(true)
        event.setShouldAbortSpawn(true)

        world.spawn(loc, Shulker::class.java) { shulker ->
            shulker.persistentDataContainer.set(respawnedTag, PersistentDataType.BYTE, 1.toByte())
        }
    }

    private fun hasPurpurFoundation(world: World, x: Int, y: Int, z: Int): Boolean {
        var foundUnder = false
        for (dy in 0..3) {
            if (PURPUR_MATERIALS.contains(world.getType(x, y - dy, z))) {
                foundUnder = true
                break
            }
        }
        if (!foundUnder) return false

        var purpurCount = 0
        for (dy in 0..1) {
            for (dx in -1..1) {
                for (dz in -1..1) {
                    if (PURPUR_MATERIALS.contains(world.getType(x + dx, y - dy, z + dz))) {
                        purpurCount++
                        if (purpurCount >= 3) return true
                    }
                }
            }
        }
        return false
    }

    private fun hasShulkerNearby(world: World, loc: Location): Boolean {
        val lx = loc.x
        val ly = loc.y
        val lz = loc.z
        val minCX = floor((lx - minRadiusBetweenShulkers) / 16.0).toInt()
        val maxCX = floor((lx + minRadiusBetweenShulkers) / 16.0).toInt()
        val minCZ = floor((lz - minRadiusBetweenShulkers) / 16.0).toInt()
        val maxCZ = floor((lz + minRadiusBetweenShulkers) / 16.0).toInt()

        for (cx in minCX..maxCX) {
            for (cz in minCZ..maxCZ) {
                if (!world.isChunkLoaded(cx, cz)) continue
                for (entity in world.getChunkAt(cx, cz).entities) {
                    if (entity is Shulker && entity.isValid) {
                        val dx = entity.x - lx
                        val dy = entity.y - ly
                        val dz = entity.z - lz
                        if (dx * dx + dy * dy + dz * dz <= minRadiusSq) {
                            return true
                        }
                    }
                }
            }
        }
        return false
    }
}