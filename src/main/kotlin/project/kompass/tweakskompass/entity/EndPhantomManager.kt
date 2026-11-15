package project.kompass.tweakskompass.entity

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent
import com.destroystokyo.paper.event.entity.PreCreatureSpawnEvent
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.entity.EntityType
import org.bukkit.entity.Phantom
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.CreatureSpawnEvent
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.Vector
import project.kompass.tweakskompass.TweaksKompass
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.floor
import kotlin.math.sqrt

class EndPhantomManager(private val plugin: TweaksKompass) : Listener {

    // Consolidated primitive state object (replaces 4 separate maps and eliminates Location retention)
    private class PhantomState(
        var lastX: Double,
        var lastY: Double,
        var lastZ: Double,
        var worldUid: UUID,
        var stuckCount: Int = 0,
        var stuckAttempts: Int = 0,
        var lastTreeAvoidTime: Long = 0L
    )

    private val phantomStates = ConcurrentHashMap<UUID, PhantomState>()

    // Pre-computed spherical offsets (dx, dy, dz) sorted from closest to farthest
    private var sortedSphereOffsets: IntArray = IntArray(0)

    private var spawnTask: BukkitTask? = null
    private var monitorTask: BukkitTask? = null

    private var isEnabled = true
    private var allowOverworldSpawning = false
    private var allowEndSpawning = true
    private var endSpawnChance = 0.08
    private var maxPhantomsPerChunk = 8
    private var spawnCheckRadius = 64.0
    private var endSpawnIntervalTicks = 200L

    private var movementImprovementsEnabled = true
    private var stuckDetectionTicks = 40L
    private var stuckThreshold = 3
    private var stuckDistanceThresholdSq = 1.0
    private var maxStuckAttempts = 5
    private var treeAvoidanceEnabled = true
    private var treeAvoidanceRadius = 3

    init {
        reloadConfig()
    }

    fun reloadConfig() {
        val cfg = plugin.config
        isEnabled = cfg.getBoolean("end-phantoms.enabled", true)
        allowOverworldSpawning = cfg.getBoolean("end-phantoms.allow-overworld-spawning", false)
        allowEndSpawning = cfg.getBoolean("end-phantoms.allow-end-spawning", true)
        endSpawnChance = cfg.getDouble("end-phantoms.end-spawn-chance", 0.08).coerceIn(0.0, 1.0)
        maxPhantomsPerChunk = cfg.getInt("end-phantoms.max-phantoms-per-chunk", 8).coerceIn(1, 64)
        spawnCheckRadius = cfg.getDouble("end-phantoms.spawn-check-radius", 64.0).coerceIn(16.0, 256.0)
        endSpawnIntervalTicks = cfg.getLong("end-phantoms.end-spawn-interval-ticks", 200L).coerceAtLeast(20L)

        movementImprovementsEnabled = cfg.getBoolean("end-phantoms.movement-improvements.enabled", true)
        stuckDetectionTicks = cfg.getLong("end-phantoms.movement-improvements.stuck-detection-ticks", 40L).coerceAtLeast(20L)
        stuckThreshold = cfg.getInt("end-phantoms.movement-improvements.stuck-threshold", 3).coerceAtLeast(1)
        val distThresh = cfg.getDouble("end-phantoms.movement-improvements.stuck-distance-threshold", 1.0)
        stuckDistanceThresholdSq = distThresh * distThresh
        maxStuckAttempts = cfg.getInt("end-phantoms.movement-improvements.max-stuck-attempts", 5).coerceAtLeast(1)
        treeAvoidanceEnabled = cfg.getBoolean("end-phantoms.movement-improvements.tree-avoidance-enabled", true)
        treeAvoidanceRadius = cfg.getInt("end-phantoms.movement-improvements.tree-avoidance-radius", 3).coerceIn(1, 12)

        rebuildSphereOffsets(treeAvoidanceRadius)
    }

    private fun rebuildSphereOffsets(r: Int) {
        val rSq = r * r
        val triples = ArrayList<IntArray>()
        for (dx in -r..r) {
            for (dy in -r..r) {
                for (dz in -r..r) {
                    val dSq = dx * dx + dy * dy + dz * dz
                    if (dSq <= rSq) {
                        triples.add(intArrayOf(dx, dy, dz, dSq))
                    }
                }
            }
        }
        // Sort by distance squared ascending so the first match in a scan is always the closest block
        triples.sortBy { it[3] }
        val flat = IntArray(triples.size * 3)
        for (i in triples.indices) {
            val t = triples[i]
            flat[i * 3] = t[0]
            flat[i * 3 + 1] = t[1]
            flat[i * 3 + 2] = t[2]
        }
        sortedSphereOffsets = flat
    }

    fun startTasks() {
        stopTasks()
        if (!isEnabled) return

        if (allowEndSpawning) {
            spawnTask = Bukkit.getScheduler().runTaskTimer(
                plugin,
                Runnable { spawnPhantomsInEnd() },
                endSpawnIntervalTicks,
                endSpawnIntervalTicks
            )
        }

        if (movementImprovementsEnabled) {
            monitorTask = Bukkit.getScheduler().runTaskTimer(
                plugin,
                Runnable { monitorEndPhantoms() },
                stuckDetectionTicks,
                stuckDetectionTicks
            )
        }
    }

    fun stopTasks() {
        spawnTask?.cancel()
        spawnTask = null
        monitorTask?.cancel()
        monitorTask = null
        phantomStates.clear()
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPrePhantomSpawn(event: PreCreatureSpawnEvent) {
        if (!isEnabled) return
        if (event.type != EntityType.PHANTOM) return

        val loc = event.spawnLocation
        val world = loc.world ?: return

        if (world.environment == World.Environment.NORMAL) {
            if (!allowOverworldSpawning) {
                event.setCancelled(true)
                event.setShouldAbortSpawn(true)
                return
            }
            if (countPhantomsInChunk(world, loc.blockX shr 4, loc.blockZ shr 4) >= maxPhantomsPerChunk) {
                event.setCancelled(true)
                event.setShouldAbortSpawn(true)
            }
        } else if (world.environment == World.Environment.THE_END) {
            if (!allowEndSpawning || countPhantomsInChunk(world, loc.blockX shr 4, loc.blockZ shr 4) >= maxPhantomsPerChunk) {
                event.setCancelled(true)
                event.setShouldAbortSpawn(true)
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onCreatureSpawn(event: CreatureSpawnEvent) {
        if (!isEnabled) return
        val phantom = event.entity as? Phantom ?: return
        val world = phantom.world

        if (world.environment == World.Environment.NORMAL && !allowOverworldSpawning) {
            event.isCancelled = true
            return
        }

        if (world.environment == World.Environment.THE_END) {
            if (!allowEndSpawning) {
                event.isCancelled = true
                return
            }
            val cx = phantom.location.blockX shr 4
            val cz = phantom.location.blockZ shr 4
            if (countPhantomsInChunk(world, cx, cz) >= maxPhantomsPerChunk) {
                event.isCancelled = true
                return
            }

            val nearest = findNearestSurvivalPlayer(phantom, 64.0)
            if (nearest != null) {
                phantom.target = nearest
            }
        }
    }

    private fun spawnPhantomsInEnd() {
        if (!isEnabled || !allowEndSpawning || endSpawnChance <= 0.0) return

        val rng = ThreadLocalRandom.current()
        for (player in Bukkit.getOnlinePlayers()) {
            if (!player.isValid || player.isDead) continue
            if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) continue

            val world = player.world
            if (world.environment != World.Environment.THE_END) continue
            if (rng.nextDouble() >= endSpawnChance) continue

            val px = player.x
            val py = player.y
            val pz = player.z
            val cx = floor(px).toInt() shr 4
            val cz = floor(pz).toInt() shr 4

            if (countPhantomsInChunk(world, cx, cz) >= maxPhantomsPerChunk) continue
            if (countPhantomsNear(world, px, py, pz, spawnCheckRadius) >= maxPhantomsPerChunk) continue

            val spawnLoc = findSafeSpawnLocation(world, cx, cz) ?: continue
            world.spawn(spawnLoc, Phantom::class.java) { phantom ->
                phantom.target = player
            }
        }
    }

    private fun countPhantomsInChunk(world: World, chunkX: Int, chunkZ: Int): Int {
        if (!world.isChunkLoaded(chunkX, chunkZ)) return 0
        var count = 0
        for (entity in world.getChunkAt(chunkX, chunkZ).entities) {
            if (entity is Phantom && entity.isValid && !entity.isDead) {
                count++
            }
        }
        return count
    }

    private fun countPhantomsNear(world: World, cxPos: Double, cyPos: Double, czPos: Double, radius: Double): Int {
        val radiusSq = radius * radius
        val minCX = floor((cxPos - radius) / 16.0).toInt()
        val maxCX = floor((cxPos + radius) / 16.0).toInt()
        val minCZ = floor((czPos - radius) / 16.0).toInt()
        val maxCZ = floor((czPos + radius) / 16.0).toInt()

        var count = 0
        for (cx in minCX..maxCX) {
            for (cz in minCZ..maxCZ) {
                if (!world.isChunkLoaded(cx, cz)) continue
                for (entity in world.getChunkAt(cx, cz).entities) {
                    if (entity is Phantom && entity.isValid && !entity.isDead) {
                        val dx = entity.x - cxPos
                        val dy = entity.y - cyPos
                        val dz = entity.z - czPos
                        if (dx * dx + dy * dy + dz * dz <= radiusSq) {
                            count++
                        }
                    }
                }
            }
        }
        return count
    }

    private fun findSafeSpawnLocation(world: World, chunkX: Int, chunkZ: Int): Location? {
        val baseX = chunkX shl 4
        val baseZ = chunkZ shl 4
        val rng = ThreadLocalRandom.current()

        for (attempt in 0 until 15) {
            val bx = baseX + rng.nextInt(16)
            val bz = baseZ + rng.nextInt(16)
            val highestY = world.getHighestBlockYAt(bx, bz)

            if (highestY <= world.minHeight + 2) continue

            val by = highestY + 12
            if (findNearestChorusFruit(world, bx, by, bz) == null) {
                return Location(world, bx + 0.5, by.toDouble(), bz + 0.5)
            }
        }
        return null
    }

    private fun monitorEndPhantoms() {
        if (!isEnabled) return

        val now = System.currentTimeMillis()
        for (world in Bukkit.getWorlds()) {
            if (world.environment != World.Environment.THE_END) continue
            // Fast-exit if no players are in The End
            if (world.players.isEmpty()) continue

            val worldUid = world.uid
            for (phantom in world.getEntitiesByClass(Phantom::class.java)) {
                val id = phantom.uniqueId
                if (!phantom.isValid || phantom.isDead) {
                    phantomStates.remove(id)
                    continue
                }

                // Keep End Phantoms automatically hostile to nearby players
                val currentTarget = phantom.target
                if (currentTarget == null || !currentTarget.isValid || currentTarget.isDead) {
                    val nearestPlayer = findNearestSurvivalPlayer(phantom, 64.0)
                    if (nearestPlayer != null) {
                        phantom.target = nearestPlayer
                    }
                }

                if (!movementImprovementsEnabled) continue

                val px = phantom.x
                val py = phantom.y
                val pz = phantom.z
                val bx = floor(px).toInt()
                val by = floor(py).toInt()
                val bz = floor(pz).toInt()

                val state = phantomStates[id]
                if (state == null || state.worldUid != worldUid) {
                    phantomStates[id] = PhantomState(px, py, pz, worldUid)
                    continue
                }

                // 1. Stuck Detection using primitive coordinates (0 Location allocations)
                val dx = px - state.lastX
                val dy = py - state.lastY
                val dz = pz - state.lastZ
                if (dx * dx + dy * dy + dz * dz < stuckDistanceThresholdSq) {
                    state.stuckCount++
                    if (state.stuckCount >= stuckThreshold) {
                        if (state.stuckAttempts < maxStuckAttempts) {
                            helpPhantomEscape(phantom, world, bx, by, bz)
                            state.stuckAttempts++
                            state.stuckCount = 0
                        } else {
                            phantom.remove()
                            phantomStates.remove(id)
                            continue
                        }
                    }
                } else {
                    state.stuckCount = 0
                    state.stuckAttempts = 0
                }

                // 2. Chorus Fruit Tree Avoidance using pre-sorted sphere offsets
                if (treeAvoidanceEnabled && now - state.lastTreeAvoidTime > 1500L) {
                    val nearestChorus = findNearestChorusFruit(world, bx, by, bz)
                    if (nearestChorus != null) {
                        guideAwayFromChorus(phantom, px, py, pz, nearestChorus)
                        state.lastTreeAvoidTime = now
                    }
                }

                state.lastX = px
                state.lastY = py
                state.lastZ = pz
            }
        }
    }

    private fun helpPhantomEscape(phantom: Phantom, world: World, bx: Int, by: Int, bz: Int) {
        val offsets = arrayOf(
            intArrayOf(0, 8, 0),
            intArrayOf(5, 4, 0),
            intArrayOf(-5, 4, 0),
            intArrayOf(0, 4, 5),
            intArrayOf(0, 4, -5),
            intArrayOf(8, 2, 0),
            intArrayOf(-8, 2, 0),
            intArrayOf(0, 2, 8),
            intArrayOf(0, 2, -8)
        )

        for (offset in offsets) {
            val tx = bx + offset[0]
            val ty = by + offset[1]
            val tz = bz + offset[2]

            if (isAirClearance(world, tx, ty, tz) && findNearestChorusFruit(world, tx, ty, tz) == null) {
                phantom.teleport(Location(world, tx + 0.5, ty.toDouble(), tz + 0.5, phantom.yaw, phantom.pitch))
                return
            }
        }

        phantom.teleport(Location(world, bx + 0.5, (by + 6).toDouble(), bz + 0.5, phantom.yaw, phantom.pitch))
    }

    private fun isAirClearance(world: World, x: Int, y: Int, z: Int): Boolean {
        if (!world.isChunkLoaded(x shr 4, z shr 4)) return false
        for (dy in 0..2) {
            if (!world.getType(x, y + dy, z).isAir) return false
        }
        return true
    }

    // Because sortedSphereOffsets is sorted by distance ascending, the FIRST match is always the nearest!
    private fun findNearestChorusFruit(world: World, baseX: Int, baseY: Int, baseZ: Int): Vector? {
        val offsets = sortedSphereOffsets
        var i = 0
        while (i < offsets.size) {
            val bx = baseX + offsets[i]
            val by = baseY + offsets[i + 1]
            val bz = baseZ + offsets[i + 2]
            i += 3

            if (!world.isChunkLoaded(bx shr 4, bz shr 4)) continue

            val type = world.getType(bx, by, bz)
            if (type == Material.CHORUS_PLANT || type == Material.CHORUS_FLOWER) {
                return Vector(bx + 0.5, by + 0.5, bz + 0.5)
            }
        }
        return null
    }

    private fun guideAwayFromChorus(phantom: Phantom, px: Double, py: Double, pz: Double, chorusPos: Vector) {
        val dx = px - chorusPos.x
        val dy = (py - chorusPos.y).coerceAtLeast(0.5)
        val dz = pz - chorusPos.z

        val dist = sqrt(dx * dx + dy * dy + dz * dz)
        if (dist > 0.001) {
            val speed = 0.35
            phantom.velocity = Vector(
                (dx / dist) * speed,
                (dy / dist) * speed,
                (dz / dist) * speed
            )
        }
    }

    private fun findNearestSurvivalPlayer(phantom: Phantom, maxRange: Double): Player? {
        val px = phantom.x
        val py = phantom.y
        val pz = phantom.z
        val maxRangeSq = maxRange * maxRange
        var nearest: Player? = null
        var nearestDistSq = Double.MAX_VALUE

        for (player in phantom.world.players) {
            if (!player.isValid || player.isDead) continue
            if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) continue

            val dx = px - player.x
            val dy = py - player.y
            val dz = pz - player.z
            val dSq = dx * dx + dy * dy + dz * dz
            if (dSq <= maxRangeSq && dSq < nearestDistSq) {
                nearestDistSq = dSq
                nearest = player
            }
        }
        return nearest
    }

    @EventHandler
    fun onEntityRemove(event: EntityRemoveFromWorldEvent) {
        if (event.entity is Phantom) {
            phantomStates.remove(event.entity.uniqueId)
        }
    }
}