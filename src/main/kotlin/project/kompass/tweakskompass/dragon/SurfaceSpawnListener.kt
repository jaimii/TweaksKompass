package project.kompass.tweakskompass.dragon

import com.destroystokyo.paper.event.entity.PreCreatureSpawnEvent
import com.destroystokyo.paper.event.entity.SkeletonHorseTrapEvent
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Tag
import org.bukkit.World
import org.bukkit.entity.*
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.CreatureSpawnEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.generator.structure.Structure
import project.kompass.tweakskompass.TweaksKompass
import java.util.EnumSet
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class SurfaceSpawnListener(
    private val plugin: TweaksKompass,
    private val manager: SurfaceSpawnManager
) : Listener {

    companion object {
        private val GLASS_MATERIALS: Set<Material> = EnumSet.of(
            Material.GLASS, Material.TINTED_GLASS,
            Material.GLASS_PANE
        ).apply {
            for (mat in Material.entries) {
                if (mat.name.endsWith("_GLASS") || mat.name.endsWith("_GLASS_PANE")) {
                    (this as EnumSet).add(mat)
                }
            }
        }

        private val WATER_MATERIALS: Set<Material> = EnumSet.of(
            Material.WATER, Material.BUBBLE_COLUMN,
            Material.SEAGRASS, Material.TALL_SEAGRASS,
            Material.KELP, Material.KELP_PLANT
        )

        private val EXEMPT_CANDIDATE_TYPES: Set<EntityType> = EnumSet.of(
            EntityType.GUARDIAN,
            EntityType.ELDER_GUARDIAN,
            EntityType.WITCH,
            EntityType.PILLAGER,
            EntityType.VINDICATOR,
            EntityType.EVOKER,
            EntityType.ILLUSIONER
        )
    }

    // Tracks recent skeleton trap triggers by SkeletonHorse UUID -> server tick
    private val recentTrapTimes = ConcurrentHashMap<UUID, Int>()

    private var isEnabled = true
    private var suppressionRadiusSq = 96.0 * 96.0
    private var allowSkeletonTraps = true

    init {
        reloadConfig()
    }

    fun reloadConfig() {
        val cfg = plugin.config
        isEnabled = cfg.getBoolean("dragon-surface-spawn.enabled", true)
        val radius = cfg.getDouble("dragon-surface-spawn.suppression-radius", 96.0)
        suppressionRadiusSq = radius * radius
        allowSkeletonTraps = cfg.getBoolean("dragon-surface-spawn.allow-skeleton-traps", true)
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        manager.loadPlayer(event.player)
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        manager.unloadPlayer(event.player.uniqueId)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onDragonDeath(event: EntityDeathEvent) {
        val dragon = event.entity as? EnderDragon ?: return
        val killer = dragon.killer ?: return

        manager.loadPlayer(killer)
        killer.sendRichMessage(
            "<gradient:#00b09b:#96c93d>✦ You have slain the Ender Dragon! Surface hostile mob spawns are now suppressed around you.</gradient>"
        )
    }

    // Captures when a player walks near a skeleton trap horse and triggers the lightning trap
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onSkeletonTrapTrigger(event: SkeletonHorseTrapEvent) {
        if (!isEnabled || !allowSkeletonTraps) return
        val horse = event.entity
        recentTrapTimes[horse.uniqueId] = Bukkit.getCurrentTick()
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPreSpawn(event: PreCreatureSpawnEvent) {
        if (!isEnabled) return
        if (!manager.hasAnyActive()) return

        // 1. Patrols are never suppressed
        if (event.reason == CreatureSpawnEvent.SpawnReason.PATROL) return

        // 2. Skeleton Traps: preserve trap triggering and lightning-spawned horsemen
        if (allowSkeletonTraps) {
            if (event.reason == CreatureSpawnEvent.SpawnReason.TRAP ||
                event.reason == CreatureSpawnEvent.SpawnReason.LIGHTNING
            ) {
                return
            }

            if (event.type == EntityType.SKELETON && isNearRecentSkeletonTrap(event.spawnLocation)) {
                return
            }
        }

        // Only natural environmental spawns are subject to suppression
        if (event.reason != CreatureSpawnEvent.SpawnReason.NATURAL) return

        // Hostile mob check: covers both Monsters and 26.2+ AbstractCubeMobs (Slimes)
        val entityClass = event.type.entityClass ?: return
        if (!Monster::class.java.isAssignableFrom(entityClass) &&
            !AbstractCubeMob::class.java.isAssignableFrom(entityClass)
        ) {
            return
        }

        val loc = event.spawnLocation
        val world = loc.world ?: return

        if (world.environment != World.Environment.NORMAL) return

        var hasSuppressedPlayerNearby = false
        for (player in world.players) {
            if (!manager.isSuppressed(player.uniqueId)) continue
            if (player.location.distanceSquared(loc) <= suppressionRadiusSq) {
                hasSuppressedPlayerNearby = true
                break
            }
        }
        if (!hasSuppressedPlayerNearby) return

        if (!isSurfaceLocation(world, loc)) return

        if (EXEMPT_CANDIDATE_TYPES.contains(event.type) && isExemptStructureSpawn(world, loc, event.type)) {
            return
        }

        event.setCancelled(true)
    }

    private fun isNearRecentSkeletonTrap(loc: Location): Boolean {
        if (recentTrapTimes.isEmpty()) return false
        val currentTick = Bukkit.getCurrentTick()

        // Expire entries older than 30 ticks (1.5s)
        recentTrapTimes.entries.removeIf { currentTick - it.value > 30 }
        if (recentTrapTimes.isEmpty()) return false

        val world = loc.world ?: return false
        for (entity in world.getNearbyEntities(loc, 10.0, 10.0, 10.0)) {
            if (entity is SkeletonHorse && recentTrapTimes.containsKey(entity.uniqueId)) {
                return true
            }
        }
        return false
    }

    private fun isExemptStructureSpawn(world: World, loc: Location, entityType: EntityType): Boolean {
        return when (entityType) {
            EntityType.GUARDIAN, EntityType.ELDER_GUARDIAN -> world.hasStructureAt(loc, Structure.MONUMENT)
            EntityType.WITCH -> world.hasStructureAt(loc, Structure.SWAMP_HUT)
            EntityType.PILLAGER, EntityType.VINDICATOR, EntityType.EVOKER, EntityType.ILLUSIONER -> {
                world.hasStructureAt(loc, Structure.PILLAGER_OUTPOST) || world.hasStructureAt(loc, Structure.MANSION)
            }
            else -> false
        }
    }

    private fun isSurfaceLocation(world: World, loc: Location): Boolean {
        val x = loc.blockX
        val z = loc.blockZ
        val spawnY = loc.blockY

        val highestY = world.getHighestBlockYAt(x, z)
        if (spawnY >= highestY) {
            return true
        }

        for (y in highestY downTo spawnY + 1) {
            val type = world.getType(x, y, z)

            if (type.isAir) continue
            if (Tag.LEAVES.isTagged(type)) continue
            if (GLASS_MATERIALS.contains(type)) continue
            if (WATER_MATERIALS.contains(type)) continue

            return false
        }

        return true
    }
}