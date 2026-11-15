package project.kompass.tweakskompass.listener

import io.papermc.paper.event.player.PlayerArmSwingEvent
import org.bukkit.Bukkit
import org.bukkit.enchantments.Enchantment
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin
import project.kompass.tweakskompass.TweaksKompass
import project.kompass.tweakskompass.util.isSpear

class SpearListener : Listener {

    @EventHandler
    fun onPlayerArmSwing(event: PlayerArmSwingEvent) {
        val player = event.player
        val item = player.inventory.itemInMainHand

        if (item.isSpear() && item.containsEnchantment(Enchantment.LUNGE)) {
            if (player.hasCooldown(item.type)) {
                Bukkit.getScheduler().runTask(JavaPlugin.getPlugin(TweaksKompass::class.java), Runnable {
                    if (player.isOnline) {
                        player.setCooldown(item.type, 0)
                    }
                })
            }
        }
    }
}