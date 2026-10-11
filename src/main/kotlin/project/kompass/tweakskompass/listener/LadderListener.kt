package project.kompass.tweakskompass.listener

import project.kompass.tweakskompass.TweaksKompass
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockPhysicsEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot

class LadderListener(private val plugin: TweaksKompass) : Listener {

    // 1. TELEPORT TO TOP OR BOTTOM
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onLadderInteract(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_BLOCK || event.hand != EquipmentSlot.HAND) return
        if (!plugin.config.getBoolean("better-ladders.quick-climb", true)) return

        val clickedBlock = event.clickedBlock ?: return
        if (clickedBlock.type != Material.LADDER) return

        val player = event.player

        // Only trigger if holding nothing in main hand
        if (player.inventory.itemInMainHand.type == Material.AIR) {
            val goUp = player.location.pitch < 0
            val target = findLadderEnd(clickedBlock, goUp)

            // Teleport to the center of the target ladder block, preserving look direction
            val targetLoc = target.location.add(0.5, 0.1, 0.5)
            targetLoc.direction = player.location.direction
            player.teleport(targetLoc)

            event.isCancelled = true
        }
    }

    private fun findLadderEnd(start: Block, up: Boolean): Block {
        val face = if (up) BlockFace.UP else BlockFace.DOWN
        var current = start

        while (true) {
            val next = current.getRelative(face)
            if (next.type != Material.LADDER) break
            current = next
        }
        return current
    }

    // 2. CHAIN PLACING LADDERS DOWNWARDS
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onLadderChainPlace(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_BLOCK || event.hand != EquipmentSlot.HAND) return
        if (!plugin.config.getBoolean("better-ladders.chain-place", true)) return

        val clickedBlock = event.clickedBlock ?: return
        if (clickedBlock.type != Material.LADDER) return

        val itemInHand = event.item ?: return
        if (itemInHand.type != Material.LADDER) return

        val player = event.player
        if (!player.isSneaking) return

        // Scan downwards to the lowest attached ladder
        var bottomLadder = clickedBlock
        while (true) {
            val next = bottomLadder.getRelative(BlockFace.DOWN)
            if (next.type != Material.LADDER) break
            bottomLadder = next
        }

        val targetBlock = bottomLadder.getRelative(BlockFace.DOWN)

        if (targetBlock.type.isAir || targetBlock.isReplaceable) {
            // Apply without physics to prevent cascading block updates
            targetBlock.setType(Material.LADDER, false)
            targetBlock.setBlockData(clickedBlock.blockData, false)

            player.swingMainHand()
            targetBlock.world.playSound(targetBlock.location, Sound.BLOCK_LADDER_PLACE, 1.0f, 1.0f)

            if (player.gameMode != GameMode.CREATIVE) {
                itemInHand.amount = itemInHand.amount - 1
            }
            event.isCancelled = true
        }
    }

    // 3. PREVENT FLOATING LADDERS FROM BREAKING
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onLadderPhysics(event: BlockPhysicsEvent) {
        if (!plugin.config.getBoolean("better-ladders.prevent-breaking", true)) return

        if (event.block.type == Material.LADDER) {
            event.isCancelled = true
        }
    }
}