package project.kompass.tweakskompass.leash

import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.Tag
import org.bukkit.entity.LeashHitch
import org.bukkit.entity.LivingEntity
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.PlayerLeashEntityEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import project.kompass.tweakskompass.TweaksKompass

class LeashInteractListener(
    private val plugin: TweaksKompass,
    private val leashRegistry: LeashRegistry
) : Listener {

    private fun isFeatureEnabled(): Boolean = plugin.config.getBoolean("lead-any-mob.enabled", true)

    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerInteractEntity(event: PlayerInteractEntityEvent) {
        if (!isFeatureEnabled()) return
        if (event.hand != EquipmentSlot.HAND) return
        val entity = event.rightClicked as? LivingEntity ?: return
        val player = event.player

        val isVanilla = leashRegistry.isLeashableInVanilla(entity.type)

        // Line 38: Direct call on leashHolder (.uniqueId instead of ?.uniqueId)
        if (entity.isLeashed && entity.leashHolder.uniqueId == player.uniqueId) {
            if (!isVanilla) {
                event.isCancelled = true
                plugin.leashManager.untrackLeash(player.uniqueId, entity.uniqueId)

                plugin.leashManager.registerProgrammaticUnleash(entity.uniqueId)
                entity.setLeashHolder(null)

                if (player.gameMode != GameMode.CREATIVE) {
                    entity.world.dropItemNaturally(entity.location, ItemStack(Material.LEAD))
                }
                entity.world.playSound(entity.location, Sound.ITEM_LEAD_UNTIED, 1.0f, 1.0f)
            }
            return
        }

        val itemInHand = player.inventory.itemInMainHand
        if (itemInHand.type != Material.LEAD) return
        if (entity.isLeashed) return
        if (isVanilla) return
        if (!plugin.leashManager.isWorldValid(player.world)) return

        event.isCancelled = true

        if (player.gameMode != GameMode.CREATIVE) {
            itemInHand.subtract(1)
        }

        plugin.leashManager.trackLeash(player.uniqueId, entity.uniqueId)
        entity.setLeashHolder(player)
        entity.world.playSound(entity.location, Sound.ITEM_LEAD_TIED, 1.0f, 1.0f)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerLeashEntity(event: PlayerLeashEntityEvent) {
        plugin.leashManager.trackLeash(event.player.uniqueId, event.entity.uniqueId)
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            if (!player.isOnline) return@Runnable
            for (nearby in player.getNearbyEntities(16.0, 16.0, 16.0)) {
                // Line 83: Direct call on leashHolder (.uniqueId instead of ?.uniqueId)
                if (nearby is LivingEntity && nearby.isLeashed && nearby.leashHolder.uniqueId == player.uniqueId) {
                    plugin.leashManager.trackLeash(player.uniqueId, nearby.uniqueId)
                }
            }
        }, 20L)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onFenceAttach(event: PlayerInteractEvent) {
        if (!isFeatureEnabled()) return
        if (event.action != Action.RIGHT_CLICK_BLOCK) return
        val clickedBlock = event.clickedBlock ?: return
        if (!Tag.FENCES.isTagged(clickedBlock.type)) return

        val player = event.player
        val pUid = player.uniqueId

        // Line 102: Direct call on leashHolder (.uniqueId instead of ?.uniqueId)
        val leashedMobs = player.world.getNearbyEntities(player.location, 10.0, 10.0, 10.0) { entity ->
            entity is LivingEntity && entity.isLeashed && entity.leashHolder.uniqueId == pUid
        }.filterIsInstance<LivingEntity>()

        if (leashedMobs.isEmpty()) return

        event.isCancelled = true

        val fenceLoc = clickedBlock.location
        val center = fenceLoc.clone().add(0.5, 0.5, 0.5)

        val hitch = fenceLoc.world.getNearbyEntities(center, 0.8, 0.8, 0.8)
            .filterIsInstance<LeashHitch>()
            .firstOrNull() ?: fenceLoc.world.spawn(fenceLoc, LeashHitch::class.java)

        for (mob in leashedMobs) {
            mob.setLeashHolder(hitch)
            plugin.leashManager.untrackLeash(pUid, mob.uniqueId)
        }

        player.world.playSound(fenceLoc, Sound.ITEM_LEAD_TIED, 1.0f, 1.0f)
    }
}