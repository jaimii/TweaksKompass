package project.kompass.tweakskompass.arrow

import org.bukkit.entity.*
import project.kompass.tweakskompass.TweaksKompass

/**
 * Checks if a projectile shot by this entity should pass through the target,
 * respecting mounts (Ravagers) and the configuration toggles in TweaksKompass.
 */
fun Entity.shouldPassThrough(target: Entity?, plugin: TweaksKompass): Boolean {
    if (target == null) return false

    // Rule 1: Never hit the mount/vehicle the shooter is riding (e.g. Ravager) or co-passengers
    val mount = this.vehicle
    if (mount != null) {
        if (mount.uniqueId == target.uniqueId) return true
        if (mount.passengers.any { it.uniqueId == target.uniqueId }) return true
    }

    // Also pass through if the target is a mount carrying a friendly rider
    if (target.passengers.any { passenger -> this.shouldPassThrough(passenger, plugin) }) {
        return true
    }

    // Rule 2: Skeletons pass-through other skeletons (including Strays, Bogged, Parched)
    if (plugin.isSkeletonPassthroughEnabled && this is AbstractSkeleton && target is AbstractSkeleton) {
        return true
    }

    // Rule 3: Illusioners and Pillagers pass-through all raider types (including Ravagers & Witches)
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