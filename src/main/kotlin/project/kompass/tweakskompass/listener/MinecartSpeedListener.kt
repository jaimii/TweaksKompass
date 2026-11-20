package project.kompass.tweakskompass.listener

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.data.Rail
import org.bukkit.entity.Minecart
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.vehicle.VehicleCreateEvent
import org.bukkit.event.vehicle.VehicleMoveEvent
import org.bukkit.event.world.ChunkLoadEvent
import project.kompass.tweakskompass.TweaksKompass
import java.util.EnumSet
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.sign

class MinecartSpeedListener(private val plugin: TweaksKompass) : Listener {

    companion object {
        private val RAIL_MATERIALS: Set<Material> = EnumSet.of(
            Material.RAIL,
            Material.POWERED_RAIL,
            Material.DETECTOR_RAIL,
            Material.ACTIVATOR_RAIL
        )

        private val REGULATED_RAIL_SHAPES: Set<Rail.Shape> = EnumSet.of(
            Rail.Shape.NORTH_EAST,
            Rail.Shape.NORTH_WEST,
            Rail.Shape.SOUTH_EAST,
            Rail.Shape.SOUTH_WEST,
            Rail.Shape.ASCENDING_EAST,
            Rail.Shape.ASCENDING_WEST,
            Rail.Shape.ASCENDING_NORTH,
            Rail.Shape.ASCENDING_SOUTH
        )
    }

    private var isEnabled = true
    private var maxSpeed = 1.0
    private var cornerFixEnabled = true
    private var cornerSpeed = 0.4
    private var lookAheadSteps = 2

    init {
        reloadConfig()
    }

    fun reloadConfig() {
        val cfg = plugin.config
        isEnabled = cfg.getBoolean("minecart-settings.enabled", true)
        maxSpeed = cfg.getDouble("minecart-settings.max-speed", 1.0).coerceAtLeast(0.4)
        cornerFixEnabled = cfg.getBoolean("minecart-settings.corner-fix.enabled", true)
        cornerSpeed = cfg.getDouble("minecart-settings.corner-fix.corner-speed", 0.4).coerceIn(0.1, maxSpeed)
        lookAheadSteps = ceil(maxSpeed).toInt().coerceIn(1, 3)

        applyAllLoadedMinecarts()
    }

    fun applyAllLoadedMinecarts() {
        if (!isEnabled) return
        for (world in Bukkit.getWorlds()) {
            for (entity in world.entities) {
                if (entity is Minecart) {
                    entity.maxSpeed = maxSpeed
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onVehicleCreate(event: VehicleCreateEvent) {
        if (!isEnabled) return
        val minecart = event.vehicle as? Minecart ?: return
        minecart.maxSpeed = maxSpeed
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onChunkLoad(event: ChunkLoadEvent) {
        if (!isEnabled) return
        for (entity in event.chunk.entities) {
            if (entity is Minecart) {
                entity.maxSpeed = maxSpeed
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onVehicleMove(event: VehicleMoveEvent) {
        if (!isEnabled) return
        val minecart = event.vehicle as? Minecart ?: return

        val from = event.from
        val to = event.to

        val toX = to.blockX
        val toY = to.blockY
        val toZ = to.blockZ

        if (from.blockX == toX && from.blockY == toY && from.blockZ == toZ) {
            return
        }

        val world = to.world ?: return
        val deltaX = to.x - from.x
        val deltaZ = to.z - from.z

        if (cornerFixEnabled && requiresSlowdown(world, toX, toY, toZ, deltaX, deltaZ)) {
            if (minecart.maxSpeed != cornerSpeed) {
                minecart.maxSpeed = cornerSpeed
            }

            if (abs(deltaX) > cornerSpeed || abs(deltaZ) > cornerSpeed) {
                val vel = minecart.velocity
                vel.x = vel.x.coerceIn(-cornerSpeed, cornerSpeed)
                vel.z = vel.z.coerceIn(-cornerSpeed, cornerSpeed)
                minecart.velocity = vel
            }
        } else {
            if (minecart.maxSpeed != maxSpeed) {
                minecart.maxSpeed = maxSpeed
            }
        }
    }

    private fun requiresSlowdown(
        world: World,
        x: Int,
        y: Int,
        z: Int,
        deltaX: Double,
        deltaZ: Double
    ): Boolean {
        val currentShape = getRailShapeAt(world, x, y, z) ?: return false
        if (REGULATED_RAIL_SHAPES.contains(currentShape)) {
            return true
        }

        val dx = if (abs(deltaX) > 0.01) sign(deltaX).toInt() else 0
        val dz = if (abs(deltaZ) > 0.01) sign(deltaZ).toInt() else 0
        if (dx == 0 && dz == 0) return false

        for (step in 1..lookAheadSteps) {
            val nextX = x + dx * step
            val nextZ = z + dz * step

            if (!world.isChunkLoaded(nextX shr 4, nextZ shr 4)) {
                return true
            }

            val nextShape = getRailShapeAt(world, nextX, y, nextZ)
            if (nextShape == null || REGULATED_RAIL_SHAPES.contains(nextShape)) {
                return true
            }
        }

        return false
    }

    private fun getRailShapeAt(world: World, x: Int, y: Int, z: Int): Rail.Shape? {
        if (RAIL_MATERIALS.contains(world.getType(x, y, z))) {
            return (world.getBlockData(x, y, z) as? Rail)?.shape
        }
        if (RAIL_MATERIALS.contains(world.getType(x, y - 1, z))) {
            return (world.getBlockData(x, y - 1, z) as? Rail)?.shape
        }
        if (RAIL_MATERIALS.contains(world.getType(x, y + 1, z))) {
            return (world.getBlockData(x, y + 1, z) as? Rail)?.shape
        }
        return null
    }
}