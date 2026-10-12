package project.kompass.tweakskompass.listener

import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.enchantments.Enchantment
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import project.kompass.tweakskompass.TweaksKompass
import project.kompass.tweakskompass.util.TridentUtil
import project.kompass.tweakskompass.util.isTrident

class TridentRiptideListener(private val plugin: TweaksKompass) : Listener {

    companion object {
        private const val TRIDENT_BASE_DAMAGE = 10.0
    }

    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        if (!plugin.config.getBoolean("trident-settings.enabled", true)) return
        if (!plugin.config.getBoolean("trident-settings.all-weather-riptide", true)) return

        if (event.action != Action.RIGHT_CLICK_AIR && event.action != Action.RIGHT_CLICK_BLOCK) return
        if (!event.hasItem()) return

        val item = event.item ?: return
        if (!item.isTrident() || !item.containsEnchantment(Enchantment.RIPTIDE)) return

        val player = event.player
        val isWet = TridentUtil.isPlayerWet(player)

        // All-Weather Riptide: Allows dry-land riptide with a 5-second (100-tick) cooldown
        if (!isWet && !player.hasCooldown(Material.TRIDENT)) {
            event.isCancelled = true
            val level = item.getEnchantmentLevel(Enchantment.RIPTIDE)
            player.velocity = player.location.direction.normalize().multiply(1.8 + (level * 0.5))

            player.startRiptideAttack(20, TRIDENT_BASE_DAMAGE.toFloat(), item)

            player.world.playSound(player.location, Sound.ITEM_TRIDENT_RIPTIDE_1, 1.0f, 1.0f)
            player.setCooldown(Material.TRIDENT, 100)
        }
    }
}