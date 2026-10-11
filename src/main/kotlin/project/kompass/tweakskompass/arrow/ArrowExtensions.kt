package project.kompass.tweakskompass.arrow

import org.bukkit.entity.*
import project.kompass.tweakskompass.TweaksKompass

/**
 * Checks if a projectile shot by this entity should pass through the target,
 * respecting the configuration toggles in TweaksKompass.
 */
fun Entity.shouldPassThrough(target: Entity?, plugin: TweaksKompass): Boolean {
    if (target == null) return false

    // Rule 1: Skeletons pass-through other skeletons
    if (plugin.isSkeletonPassthroughEnabled && this is AbstractSkeleton && target is AbstractSkeleton) {
        return true
    }

    // Rule 2: Illusioners and Pillagers pass-through all raider types, including witches
    if (plugin.isIllagerPassthroughEnabled && this.isIllusionerOrPillager() && target.isRaiderOrWitch()) {
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