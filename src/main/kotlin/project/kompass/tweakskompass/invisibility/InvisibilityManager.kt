package project.kompass.tweakskompass.invisibility

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.protocol.player.Equipment
import com.github.retrooper.packetevents.protocol.player.EquipmentSlot
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEquipment
import io.github.retrooper.packetevents.util.SpigotConversionUtil
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import project.kompass.tweakskompass.TweaksKompass
import java.util.concurrent.ConcurrentHashMap

class InvisibilityManager(private val plugin: TweaksKompass) {

    private val invisibleEntityIds = ConcurrentHashMap.newKeySet<Int>()

    companion object {
        val AIR_ITEM: com.github.retrooper.packetevents.protocol.item.ItemStack =
            SpigotConversionUtil.fromBukkitItemStack(ItemStack(Material.AIR))
    }

    fun isTracking(entityId: Int): Boolean = invisibleEntityIds.contains(entityId)

    fun hidePlayer(player: Player) {
        invisibleEntityIds.add(player.entityId)
        syncEquipment(player, hide = true)
    }

    fun showPlayer(player: Player) {
        if (invisibleEntityIds.remove(player.entityId)) {
            syncEquipment(player, hide = false)
        }
    }

    fun untrack(entityId: Int) {
        invisibleEntityIds.remove(entityId)
    }

    fun clear() {
        invisibleEntityIds.clear()
    }

    fun maskPacket(packet: WrapperPlayServerEntityEquipment) {
        val original = packet.equipment ?: return
        if (original.isEmpty()) return

        val hideArmor = plugin.config.getBoolean("better-invisibility.hide-armor", true)
        val hideHeld = plugin.config.getBoolean("better-invisibility.hide-held-items", false)

        val masked = ArrayList<Equipment>(original.size)

        for (eq in original) {
            val shouldHide = when (eq.slot) {
                EquipmentSlot.HELMET, EquipmentSlot.CHEST_PLATE,
                EquipmentSlot.LEGGINGS, EquipmentSlot.BOOTS -> hideArmor
                EquipmentSlot.MAIN_HAND, EquipmentSlot.OFF_HAND -> hideHeld
                else -> false
            }

            if (shouldHide) {
                masked.add(Equipment(eq.slot, AIR_ITEM))
            } else {
                masked.add(eq)
            }
        }

        packet.equipment = masked
    }

    private fun syncEquipment(player: Player, hide: Boolean) {
        val hideArmor = plugin.config.getBoolean("better-invisibility.hide-armor", true)
        val hideHeld = plugin.config.getBoolean("better-invisibility.hide-held-items", false)

        val equipmentList = mutableListOf<Equipment>()
        val inv = player.inventory

        if (hideArmor) {
            equipmentList.add(Equipment(EquipmentSlot.BOOTS, if (hide) AIR_ITEM else toPacketItem(inv.boots)))
            equipmentList.add(Equipment(EquipmentSlot.LEGGINGS, if (hide) AIR_ITEM else toPacketItem(inv.leggings)))
            equipmentList.add(Equipment(EquipmentSlot.CHEST_PLATE, if (hide) AIR_ITEM else toPacketItem(inv.chestplate)))
            equipmentList.add(Equipment(EquipmentSlot.HELMET, if (hide) AIR_ITEM else toPacketItem(inv.helmet)))
        }

        if (hideHeld) {
            equipmentList.add(Equipment(EquipmentSlot.MAIN_HAND, if (hide) AIR_ITEM else toPacketItem(inv.itemInMainHand)))
            equipmentList.add(Equipment(EquipmentSlot.OFF_HAND, if (hide) AIR_ITEM else toPacketItem(inv.itemInOffHand)))
        }

        if (equipmentList.isEmpty()) return

        val packet = WrapperPlayServerEntityEquipment(player.entityId, equipmentList)

        for (viewer in player.trackedBy) {
            if (viewer.entityId == player.entityId) continue
            PacketEvents.getAPI().playerManager.sendPacket(viewer, packet)
        }
    }

    private fun toPacketItem(item: ItemStack?): com.github.retrooper.packetevents.protocol.item.ItemStack {
        if (item == null || item.type.isAir) return AIR_ITEM
        return SpigotConversionUtil.fromBukkitItemStack(item)
    }
}