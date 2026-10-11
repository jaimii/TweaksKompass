package project.kompass.tweakskompass.listener

import project.kompass.tweakskompass.TweaksKompass
import project.kompass.tweakskompass.boat.BuoyancyController
import project.kompass.tweakskompass.boat.LaunchTracker
import project.kompass.tweakskompass.boat.WaterSurfaceSampler
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.block.data.Levelled
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Boat
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInputEvent
import org.bukkit.event.vehicle.VehicleDestroyEvent
import org.bukkit.event.vehicle.VehicleMoveEvent
import kotlin.math.abs

class BoatListener(private val plugin: TweaksKompass) : Listener {

    private val launchTracker = LaunchTracker()
    private val waterSampler = WaterSurfaceSampler()

    fun isBoat(entity: Entity?): Boolean {
        if (entity == null) return false
        return entity is Boat || entity.type.name.contains("BOAT") || entity.type.name.contains("RAFT")
    }

    fun getDrivenBoat(player: Player): Boat? {
        val vehicle = player.vehicle
        return if (isBoat(vehicle)) vehicle as? Boat else null
    }

    // 1. BOAT JUMPING (SPACEBAR INPUT)
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onPlayerInput(event: PlayerInputEvent) {
        if (!event.input.isJump) return
        if (!plugin.config.getBoolean("better-boats.jump.enabled", true)) return

        val player = event.player
        val boat = getDrivenBoat(player) ?: return

        val previous = player.currentInput
        val justJumped = previous == null || !previous.isJump

        if (justJumped && (boat.isOnGround || boat.isInWater)) {
            handleBoatJump(player, boat)
        }
    }

    fun handleBoatJump(player: Player, boat: Boat) {
        val jumpForce = plugin.config.getDouble("better-boats.jump.force", 0.35)
        val velocity = boat.velocity
        velocity.y = jumpForce
        boat.velocity = velocity

        // Mark boat in launch tracker so buoyancy doesn't cancel the jump arc
        launchTracker.mark(boat, 15L)

        val volume = plugin.config.getDouble("better-boats.jump.sound-volume", 1.0).toFloat()
        val pitch = plugin.config.getDouble("better-boats.jump.sound-pitch", 0.8).toFloat()
        boat.world.playSound(boat.location, Sound.ENTITY_BOAT_PADDLE_LAND, volume, pitch)
    }

    // 2. SMOOTH BUOYANCY & FROST WALKER FREEZING
    @EventHandler(priority = EventPriority.LOWEST)
    fun onVehicleMove(event: VehicleMoveEvent) {
        val vehicle = event.vehicle
        if (vehicle !is Boat) return

        val from = event.from
        val to = event.to ?: return

        // --- FEATURE A: FROST BOATS (Freezing water under boat) ---
        if (plugin.config.getBoolean("better-boats.frost-walker.enabled", true)) {
            // Fast-Exit: Only evaluate freezing when crossing block boundaries
            if (from.blockX != to.blockX || from.blockZ != to.blockZ) {
                val frostLevel = getFrostWalkerLevel(vehicle)
                if (frostLevel > 0) {
                    freezeWaterUnderBoat(vehicle, frostLevel)
                }
            }
        }

        // --- FEATURE B: SMOOTH BUOYANCY & UPSTREAM TRAVEL ---
        if (!plugin.config.getBoolean("better-boats.smooth.enabled", true)) return
        if (vehicle.passengers.isEmpty()) return
        if (vehicle.isOnGround || launchTracker.isActive(vehicle)) return

        val loc = vehicle.location
        val vel = vehicle.velocity

        val sampleRadius = plugin.config.getDouble("better-boats.smooth.sample-radius", 0.8)
        val sample = waterSampler.sample(loc, sampleRadius, vel) ?: return

        val flatTolerance = plugin.config.getDouble("better-boats.smooth.flat-tolerance", 0.05)
        if (!sample.requiresCorrection(flatTolerance)) return

        val targetOffset = plugin.config.getDouble("better-boats.smooth.target-offset", 0.1)
        val strength = plugin.config.getDouble("better-boats.smooth.strength", 0.4)
        val damping = plugin.config.getDouble("better-boats.smooth.damping", 0.1)
        val maxSpeed = plugin.config.getDouble("better-boats.smooth.max-vertical-speed", 0.5)
        val minChange = plugin.config.getDouble("better-boats.smooth.minimum-change", 0.01)

        val nextY = BuoyancyController.verticalVelocity(
            loc.y,
            sample.targetSurface() + targetOffset,
            vel.y,
            strength,
            damping,
            maxSpeed
        )

        if (abs(nextY - vel.y) >= minChange) {
            vel.y = nextY
            vehicle.velocity = vel
        }
    }

    @EventHandler
    fun onVehicleDestroy(event: VehicleDestroyEvent) {
        val vehicle = event.vehicle
        if (vehicle is Boat) {
            launchTracker.clear(vehicle)
        }
    }

    private fun getFrostWalkerLevel(boat: Boat): Int {
        var highest = 0
        for (passenger in boat.passengers) {
            if (passenger is Player) {
                val boots = passenger.inventory.boots ?: continue
                val level = boots.getEnchantmentLevel(Enchantment.FROST_WALKER)
                if (level > highest) highest = level
            }
        }
        return highest
    }

    private fun freezeWaterUnderBoat(boat: Boat, level: Int) {
        val radius = 2 + level
        val radiusSq = radius * radius
        val world = boat.world
        val boatX = boat.location.blockX
        val boatY = boat.location.blockY
        val boatZ = boat.location.blockZ

        for (dx in -radius..radius) {
            for (dz in -radius..radius) {
                if (dx * dx + dz * dz > radiusSq) continue

                val targetX = boatX + dx
                val targetZ = boatZ + dz

                // Only check 2 blocks vertically at the water surface level
                for (y in boatY downTo boatY - 1) {
                    val block = world.getBlockAt(targetX, y, targetZ)
                    if (block.type == Material.WATER) {
                        val above = world.getBlockAt(targetX, y + 1, targetZ)
                        // Fixed: using above.type.isAir instead of above.isAir
                        if (above.type.isAir || (!above.type.isSolid && above.type != Material.WATER)) {
                            val data = block.blockData
                            // Only freeze source water blocks to match vanilla Frost Walker mechanics
                            if (data is Levelled && data.level == 0) {
                                block.setType(Material.FROSTED_ICE, true)
                            }
                        }
                    }
                }
            }
        }
    }
}