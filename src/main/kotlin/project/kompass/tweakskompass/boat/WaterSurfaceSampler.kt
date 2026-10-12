package project.kompass.tweakskompass.boat

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.data.Levelled
import org.bukkit.block.data.Waterlogged
import org.bukkit.util.Vector
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

class WaterSurfaceSampler {

    data class WaterSample(
        val average: Double,
        val leadingAverage: Double,
        val minimum: Double,
        val maximum: Double,
        val count: Int,
        val leadingCount: Int
    ) {
        fun requiresCorrection(tolerance: Double): Boolean {
            return count < 5 || (maximum - minimum) > tolerance
        }

        fun targetSurface(): Double {
            return if (leadingCount == 0) average else leadingAverage
        }
    }

    fun sample(center: Location, radius: Double, motion: Vector): WaterSample? {
        val forward = horizontalDirection(center, motion)
        val fX = forward.x
        val fZ = forward.z
        val rX = -fZ
        val rZ = fX

        val offsetsX = doubleArrayOf(
            0.0,
            fX * radius + rX * radius,
            fX * radius - rX * radius,
            -fX * radius + rX * radius,
            -fX * radius - rX * radius
        )
        val offsetsZ = doubleArrayOf(
            0.0,
            fZ * radius + rZ * radius,
            fZ * radius - rZ * radius,
            -fZ * radius + rZ * radius,
            -fZ * radius - rZ * radius
        )

        var total = 0.0
        var leadingTotal = 0.0
        var minimum = Double.POSITIVE_INFINITY
        var maximum = Double.NEGATIVE_INFINITY
        var count = 0
        var leadingCount = 0

        val world = center.world ?: return null
        val baseX = center.x
        val baseY = center.blockY
        val baseZ = center.z

        for (i in 0 until 5) {
            val sampleX = floor(baseX + offsetsX[i]).toInt()
            val sampleZ = floor(baseZ + offsetsZ[i]).toInt()
            val height = surfaceAt(world, sampleX, baseY, sampleZ)

            if (height != null) {
                total += height
                minimum = min(minimum, height)
                maximum = max(maximum, height)
                count++
                if (i == 1 || i == 2) {
                    leadingTotal += height
                    leadingCount++
                }
            }
        }

        if (count == 0) return null

        val avg = total / count
        val leadingAvg = if (leadingCount == 0) avg else leadingTotal / leadingCount

        return WaterSample(avg, leadingAvg, minimum, maximum, count, leadingCount)
    }

    private fun horizontalDirection(center: Location, motion: Vector): Vector {
        val hX = motion.x
        val hZ = motion.z
        val lenSq = hX * hX + hZ * hZ
        if (lenSq >= 1.0E-4) {
            val invLen = 1.0 / kotlin.math.sqrt(lenSq)
            return Vector(hX * invLen, 0.0, hZ * invLen)
        }
        val dir = center.direction
        val dX = dir.x
        val dZ = dir.z
        val dLenSq = dX * dX + dZ * dZ
        if (dLenSq >= 1.0E-4) {
            val invLen = 1.0 / kotlin.math.sqrt(dLenSq)
            return Vector(dX * invLen, 0.0, dZ * invLen)
        }
        return Vector(0.0, 0.0, 1.0)
    }

    private fun surfaceAt(world: org.bukkit.World, blockX: Int, startY: Int, blockZ: Int): Double? {
        for (y in startY + 1 downTo startY - 2) {
            val block = world.getBlockAt(blockX, y, blockZ)
            val type = block.type

            val isWaterSource = type == Material.WATER || type == Material.BUBBLE_COLUMN ||
                    type == Material.SEAGRASS || type == Material.TALL_SEAGRASS ||
                    type == Material.KELP || type == Material.KELP_PLANT

            val isWaterloggedBlock = block.blockData is Waterlogged && (block.blockData as Waterlogged).isWaterlogged

            if (!isWaterSource && !isWaterloggedBlock) continue

            val data = block.blockData
            val level = if (data is Levelled) data.level else 0
            val height = if (level == 0 || level >= 8) 1.0 else 1.0 - (level / 8.0)
            return y.toDouble() + height
        }
        return null
    }
}