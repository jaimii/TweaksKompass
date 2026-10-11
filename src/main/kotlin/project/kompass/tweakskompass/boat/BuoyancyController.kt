package project.kompass.tweakskompass.boat

object BuoyancyController {

    fun verticalVelocity(
        currentY: Double,
        targetY: Double,
        currentVelocity: Double,
        strength: Double,
        damping: Double,
        maximum: Double
    ): Double {
        val rawVelocity = currentVelocity * (1.0 - damping) + (targetY - currentY) * strength
        return rawVelocity.coerceIn(-maximum, maximum)
    }
}