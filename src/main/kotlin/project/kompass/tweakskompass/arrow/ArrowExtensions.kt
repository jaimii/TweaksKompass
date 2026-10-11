package project.kompass.tweakskompass.arrow

import org.bukkit.entity.*

/**
 * Checks if a projectile shot by this entity should pass through the target.
 */
fun Entity.shouldPassThrough(target: Entity?): Boolean {
    if (target == null) return false

    // Rule 1: Skeletons pass-through other skeletons
    if (this is AbstractSkeleton && target is AbstractSkeleton) {
        return true
    }

    // Rule 2: Illusioners and Pillagers pass-through all raider types, including witches
    if (this.isIllusionerOrPillager() && target.isRaiderOrWitch()) {
        return true
    }

    return false
}

private fun Entity.isIllusionerOrPillager(): Boolean {
    return this is Illusioner || this is Pillager
}

private fun Entity.isRaiderOrWitch(): Boolean {
    return this is Raider || this is Witch
}