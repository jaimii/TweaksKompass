package project.kompass.tweakskompass.leash

import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.Tag
import org.bukkit.entity.LeashHitch
import org.bukkit.entity.LivingEntity
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.PlayerLeashEntityEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.inventory.EquipmentSlot
import project.kompass.tweakskompass.TweaksKompass

class LeashInteractListener(
    private val plugin: TweaksKompass,
    private val leashRegistry: LeashRegistry
) : Listener {

    @EventHandler
    fun onPlayerInteractEntity(event: PlayerInteractEntityEvent) {
        if (!plugin.config.getBoolean("lead-any-mob.enabled", true)) return
        if (event.hand != EquipmentSlot.HAND) return
        val entity = event.rightClicked as? LivingEntity ?: return

        val player = event.player
        val itemInHand = player.inventory.itemInMainHand
        if (itemInHand.type != Material.LEAD) return

        if (leashRegistry.isLeashableInVanilla(entity.type) || entity.isLeashed) return
        if (!plugin.leashManager.isWorldValid(player.world)) return

        if (player.gameMode != GameMode.CREATIVE) {
            itemInHand.amount = itemInHand.amount - 1
        }

        plugin.leashManager.trackLeash(player.uniqueId, entity.uniqueId)

        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            if (entity.isValid && !entity.isDead && player.isOnline) {
                entity.setLeashHolder(player)
            }
        }, 1L)
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerLeashEntity(event: PlayerLeashEntityEvent) {
        plugin.leashManager.trackLeash(event.player.uniqueId, event.entity.uniqueId)
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            if (!player.isOnline) return@Runnable
            for (nearby in player.getNearbyEntities(15.0, 15.0, 15.0)) {
                if (nearby is LivingEntity && nearby.isLeashed) {
                    val holder = nearby.leashHolder
                    if (holder != null && holder.uniqueId == player.uniqueId) {
                        plugin.leashManager.trackLeash(player.uniqueId, nearby.uniqueId)
                    }
                }
            }
        }, 20L)
    }

    @EventHandler
    fun onFenceAttach(event: PlayerInteractEvent) {
        if (!plugin.config.getBoolean("lead-any-mob.enabled", true)) return
        if (event.action != Action.RIGHT_CLICK_BLOCK) return
        val clickedBlock = event.clickedBlock ?: return

        val player = event.player
        if (!plugin.leashManager.hasActiveLeashes(player.uniqueId)) return
        if (!Tag.FENCES.isTagged(clickedBlock.type)) return

        val clickedLoc = clickedBlock.location

        var hitch: LeashHitch? = clickedLoc.world.getNearbyEntities(clickedLoc.clone().add(0.5, 0.5, 0.5), 0.5, 0.5, 0.5)
            .filterIsInstance<LeashHitch>()
            .firstOrNull()

        for (nearby in player.getNearbyEntities(10.0, 10.0, 10.0)) {
            if (nearby is LivingEntity && nearby.isLeashed && player == nearby.leashHolder) {
                if (!leashRegistry.isLeashableInVanilla(nearby.type)) {
                    if (hitch == null) {
                        hitch = nearby.world.spawn(clickedLoc, LeashHitch::class.java)
                    }
                    nearby.setLeashHolder(hitch)
                    plugin.leashManager.untrackLeash(player.uniqueId, nearby.uniqueId)
                    event.isCancelled = true
                }
            }
        }
    }
}