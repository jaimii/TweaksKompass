package project.kompass.tweakskompass.leash

import project.kompass.tweakskompass.TweaksKompass
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerTeleportEvent

class LeashTeleportListener(private val plugin: TweaksKompass) : Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerTeleport(event: PlayerTeleportEvent) {
        val player = event.player

        if (!plugin.leashManager.hasActiveLeashes(player.uniqueId)) return

        val fromWorld = event.from.world ?: return
        val toLoc = event.to ?: return
        val toWorld = toLoc.world ?: return

        if (!plugin.leashManager.isWorldValid(fromWorld) || !plugin.leashManager.isWorldValid(toWorld)) return

        plugin.leashManager.teleportLeashed(player)
    }
}