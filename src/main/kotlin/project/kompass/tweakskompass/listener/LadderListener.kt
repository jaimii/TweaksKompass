package project.kompass.tweakskompass.listener

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
import project.kompass.tweakskompass.TweaksKompass

class LadderListener(private val plugin: TweaksKompass) : Listener {

    private var isQuickClimbEnabled = true
    private var isChainPlaceEnabled = true
    private var isPreventBreakingEnabled = true

    init {
        reloadConfig()
    }

    fun reloadConfig() {
        val cfg = plugin.config
        isQuickClimbEnabled = cfg.getBoolean("better-ladders.quick-climb", true)
        isChainPlaceEnabled = cfg.getBoolean("better-ladders.chain-place", true)
        isPreventBreakingEnabled = cfg.getBoolean("better-ladders.prevent-breaking", true)
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onLadderInteract(event: PlayerInteractEvent) {
        if (!isQuickClimbEnabled) return
        if (event.action != Action.RIGHT_CLICK_BLOCK || event.hand != EquipmentSlot.HAND) return

        val clickedBlock = event.clickedBlock ?: return
        if (clickedBlock.type != Material.LADDER) return

        val player = event.player
        if (player.inventory.itemInMainHand.type == Material.AIR) {
            val goUp = player.location.pitch < 0
            val target = findLadderEnd(clickedBlock, goUp)

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

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onLadderChainPlace(event: PlayerInteractEvent) {
        if (!isChainPlaceEnabled) return
        if (event.action != Action.RIGHT_CLICK_BLOCK || event.hand != EquipmentSlot.HAND) return

        val clickedBlock = event.clickedBlock ?: return
        if (clickedBlock.type != Material.LADDER) return

        val itemInHand = event.item ?: return
        if (itemInHand.type != Material.LADDER) return

        val player = event.player
        if (!player.isSneaking) return

        var bottomLadder = clickedBlock
        while (true) {
            val next = bottomLadder.getRelative(BlockFace.DOWN)
            if (next.type != Material.LADDER) break
            bottomLadder = next
        }

        val targetBlock = bottomLadder.getRelative(BlockFace.DOWN)

        if (targetBlock.type.isAir || targetBlock.isReplaceable) {
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

    // High-frequency hot-path guard
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onLadderPhysics(event: BlockPhysicsEvent) {
        if (!isPreventBreakingEnabled) return

        // Fast-exit: only cancel when the block undergoing physics check is a ladder
        if (event.block.type == Material.LADDER) {
            event.isCancelled = true
        }
    }
}