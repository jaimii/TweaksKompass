package project.kompass.tweakskompass.listener

import project.kompass.tweakskompass.util.TridentUtil
import project.kompass.tweakskompass.util.isTrident
import project.kompass.tweakskompass.util.isSpear
import org.bukkit.NamespacedKey
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.*
import org.bukkit.inventory.EquipmentSlotGroup
import org.bukkit.inventory.ItemStack

class TridentAttributeListener : Listener {

    companion object {
        private const val TRIDENT_REACH = 4.5
        private const val LUNGE_COOLDOWN_TICKS = 6.0
        private const val LUNGE_ATTACK_SPEED = 20.0 / LUNGE_COOLDOWN_TICKS
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        applyHeldAttributes(event.player)
    }

    @EventHandler
    fun onPlayerChangedWorld(event: PlayerChangedWorldEvent) {
        resetAttributes(event.player)
        applyHeldAttributes(event.player)
    }

    @EventHandler
    fun onItemHold(event: PlayerItemHeldEvent) {
        val player = event.player
        val newItem = player.inventory.getItem(event.newSlot)

        resetAttributes(player)
        if (newItem.isTrident()) {
            applyTridentAttributes(player, newItem)
        } else if (newItem.isSpear()) {
            applySpearAttributes(player, newItem)
        }
    }

    @EventHandler
    fun onPlayerDropItem(event: PlayerDropItemEvent) {
        val item = event.itemDrop.itemStack
        if (item.isTrident() || item.isSpear()) {
            resetAttributes(event.player)
        }
    }

    private fun applyHeldAttributes(player: Player) {
        val held = player.inventory.itemInMainHand
        if (held.isTrident()) {
            applyTridentAttributes(player, held)
        } else if (held.isSpear()) {
            applySpearAttributes(player, held)
        }
    }

    private fun applyTridentAttributes(player: Player, trident: ItemStack?) {
        applyRangeModifier(player, Attribute.ENTITY_INTERACTION_RANGE, TRIDENT_REACH, TridentUtil.RANGE_KEY)
        applyRangeModifier(player, Attribute.BLOCK_INTERACTION_RANGE, TRIDENT_REACH, TridentUtil.BLOCK_RANGE_KEY)

        val speedInstance = player.getAttribute(Attribute.ATTACK_SPEED)
        if (speedInstance != null && speedInstance.getModifier(TridentUtil.SPEED_KEY) == null) {
            speedInstance.addModifier(AttributeModifier(
                TridentUtil.SPEED_KEY,
                0.2,
                AttributeModifier.Operation.ADD_NUMBER,
                EquipmentSlotGroup.MAINHAND
            ))
        }
    }

    private fun applySpearAttributes(player: Player, spear: ItemStack?) {
        if (spear != null && spear.containsEnchantment(org.bukkit.enchantments.Enchantment.LUNGE)) {
            val speedInstance = player.getAttribute(Attribute.ATTACK_SPEED)
            if (speedInstance != null && speedInstance.getModifier(TridentUtil.SPEED_KEY) == null) {
                val modifierAmount = LUNGE_ATTACK_SPEED - 1.2
                speedInstance.addModifier(AttributeModifier(
                    TridentUtil.SPEED_KEY,
                    modifierAmount,
                    AttributeModifier.Operation.ADD_NUMBER,
                    EquipmentSlotGroup.MAINHAND
                ))
            }
        }
    }

    private fun applyRangeModifier(player: Player, attribute: Attribute, targetValue: Double, key: NamespacedKey) {
        val instance = player.getAttribute(attribute) ?: return
        instance.removeModifier(key)
        instance.addModifier(AttributeModifier(
            key,
            targetValue - instance.baseValue,
            AttributeModifier.Operation.ADD_NUMBER,
            EquipmentSlotGroup.MAINHAND
        ))
    }

    private fun resetAttributes(player: Player) {
        player.getAttribute(Attribute.ENTITY_INTERACTION_RANGE)?.removeModifier(TridentUtil.RANGE_KEY)
        player.getAttribute(Attribute.BLOCK_INTERACTION_RANGE)?.removeModifier(TridentUtil.BLOCK_RANGE_KEY)
        player.getAttribute(Attribute.ATTACK_SPEED)?.removeModifier(TridentUtil.SPEED_KEY)
    }
}