package project.kompass.tweakskompass.listener

import org.bukkit.*
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.data.type.Leaves
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.LeavesDecayEvent
import org.bukkit.scheduler.BukkitTask
import project.kompass.tweakskompass.TweaksKompass
import java.util.*

class FastLeafDecayListener(private val plugin: TweaksKompass) : Listener {

    private data class ScheduledLeaf(
        val worldUid: UUID,
        val packedPos: Long,
        val executeTick: Long
    )

    companion object {
        // Only inspect the 6 orthogonal cardinal faces (avoids 13 redundant diagonal queries per block)
        private val CARDINAL_FACES = arrayOf(
            BlockFace.UP,
            BlockFace.DOWN,
            BlockFace.NORTH,
            BlockFace.SOUTH,
            BlockFace.EAST,
            BlockFace.WEST
        )

        private fun packCoords(x: Int, y: Int, z: Int): Long {
            return ((x.toLong() and 0x3FFFFFF) shl 38) or ((z.toLong() and 0x3FFFFFF) shl 12) or (y.toLong() and 0xFFF)
        }

        private fun unpackX(packed: Long): Int = (packed shr 38).toInt()
        private fun unpackY(packed: Long): Int = (packed and 0xFFF).toShort().toInt()
        private fun unpackZ(packed: Long): Int = ((packed shr 12) and 0x3FFFFFF).toInt()
    }

    // Primitive coordinate sets prevent duplicate scheduling with zero memory leaks
    private val scheduledCoords = HashMap<UUID, HashSet<Long>>()
    private val decayQueue = ArrayDeque<ScheduledLeaf>()
    private var decayTask: BukkitTask? = null

    // Pre-cached config flags
    private var isEnabled = true
    private var breakDelayTicks = 4L
    private var decayDelayTicks = 2L
    private var maxDecayPerTick = 20
    private var spawnParticles = true
    private var playSound = true
    private var dropItems = true
    private val allowedWorlds = HashSet<String>()

    init {
        reloadConfig()
    }

    fun reloadConfig() {
        val cfg = plugin.config
        isEnabled = cfg.getBoolean("fast-leaf-decay.enabled", true)
        breakDelayTicks = cfg.getLong("fast-leaf-decay.break-delay-ticks", 4L).coerceAtLeast(1L)
        decayDelayTicks = cfg.getLong("fast-leaf-decay.decay-delay-ticks", 2L).coerceAtLeast(1L)
        maxDecayPerTick = cfg.getInt("fast-leaf-decay.max-decay-per-tick", 20).coerceAtLeast(1)
        spawnParticles = cfg.getBoolean("fast-leaf-decay.spawn-particles", true)
        playSound = cfg.getBoolean("fast-leaf-decay.play-sound", true)
        dropItems = cfg.getBoolean("fast-leaf-decay.drop-items", true)

        allowedWorlds.clear()
        allowedWorlds.addAll(cfg.getStringList("fast-leaf-decay.worlds"))
    }

    fun cleanup() {
        decayTask?.cancel()
        decayTask = null
        decayQueue.clear()
        scheduledCoords.clear()
    }

    private fun isWorldAllowed(worldName: String): Boolean {
        if (allowedWorlds.isEmpty() || allowedWorlds.contains("ALL")) return true
        return allowedWorlds.contains(worldName)
    }

    // 1. Player breaks a log or tree part
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        if (!isEnabled) return
        val block = event.block
        val type = block.type

        if (Tag.LOGS.isTagged(type) || Tag.FENCES.isTagged(type) || Tag.LEAVES.isTagged(type)) {
            inspectNeighbors(block, breakDelayTicks)
        }
    }

    // 2. Cascade trigger: Leaf naturally decays, triggering neighbors
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onLeavesDecay(event: LeavesDecayEvent) {
        if (!isEnabled) return
        inspectNeighbors(event.block, decayDelayTicks)
    }

    private fun inspectNeighbors(centerBlock: Block, delay: Long) {
        val world = centerBlock.world
        if (!isWorldAllowed(world.name)) return

        val currentTick = world.gameTime
        val targetTick = currentTick + delay
        val worldUid = world.uid

        val worldSet = scheduledCoords.computeIfAbsent(worldUid) { HashSet() }

        for (face in CARDINAL_FACES) {
            val neighbor = centerBlock.getRelative(face)
            val nType = neighbor.type

            if (Tag.LEAVES.isTagged(nType)) {
                val leaves = neighbor.blockData as? Leaves ?: continue

                // Player-placed persistent leaves never decay
                if (leaves.isPersistent) continue

                val packed = packCoords(neighbor.x, neighbor.y, neighbor.z)
                if (worldSet.add(packed)) {
                    decayQueue.add(ScheduledLeaf(worldUid, packed, targetTick))
                    ensureWorkerRunning()
                }
            }
        }
    }

    private fun ensureWorkerRunning() {
        if (decayTask != null) return

        decayTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            if (decayQueue.isEmpty()) {
                decayTask?.cancel()
                decayTask = null
                return@Runnable
            }

            val currentWorldTimeCache = HashMap<UUID, Long>()
            var processedThisTick = 0

            val iterator = decayQueue.iterator()
            while (iterator.hasNext() && processedThisTick < maxDecayPerTick) {
                val leaf = iterator.next()
                val world = Bukkit.getWorld(leaf.worldUid)

                // Chunk/world unloaded guard
                if (world == null) {
                    iterator.remove()
                    scheduledCoords[leaf.worldUid]?.remove(leaf.packedPos)
                    continue
                }

                val currentTick = currentWorldTimeCache.computeIfAbsent(leaf.worldUid) { world.gameTime }

                if (currentTick >= leaf.executeTick) {
                    iterator.remove()
                    scheduledCoords[leaf.worldUid]?.remove(leaf.packedPos)

                    val x = unpackX(leaf.packedPos)
                    val y = unpackY(leaf.packedPos)
                    val z = unpackZ(leaf.packedPos)

                    if (world.isChunkLoaded(x shr 4, z shr 4)) {
                        val block = world.getBlockAt(x, y, z)
                        processDecay(block)
                    }

                    processedThisTick++
                }
            }

            // Cleanup empty world coordinate sets
            scheduledCoords.values.removeIf { it.isEmpty() }
        }, 1L, 1L)
    }

    private fun processDecay(block: Block) {
        val type = block.type
        if (!Tag.LEAVES.isTagged(type)) return

        val leaves = block.blockData as? Leaves ?: return
        if (leaves.isPersistent) return

        if (leaves.distance < 7) return

        val event = LeavesDecayEvent(block)
        Bukkit.getPluginManager().callEvent(event)
        if (event.isCancelled) return

        val loc = block.location
        val world = block.world

        if (spawnParticles) {
            val centerLoc = loc.clone().add(0.5, 0.5, 0.5)
            world.spawnParticle(
                Particle.BLOCK,
                centerLoc,
                8,
                0.2, 0.2, 0.2,
                0.0,
                type.createBlockData()
            )
        }

        if (playSound) {
            world.playSound(loc, Sound.BLOCK_GRASS_BREAK, SoundCategory.BLOCKS, 0.5f, 1.2f)
        }

        if (dropItems) {
            block.breakNaturally()
        } else {
            block.setType(Material.AIR, false)
        }

        // Trigger cascade on neighbor leaves
        inspectNeighbors(block, decayDelayTicks)
    }
}