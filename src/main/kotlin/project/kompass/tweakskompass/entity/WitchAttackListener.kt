package project.kompass.tweakskompass.entity

import org.bukkit.Material
import org.bukkit.entity.ThrownPotion
import org.bukkit.entity.Witch
import org.bukkit.entity.Zombie
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.ProjectileLaunchEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.PotionMeta
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.potion.PotionType
import project.kompass.tweakskompass.TweaksKompass

class WitchAttackListener(private val plugin: TweaksKompass) : Listener {

    @EventHandler
    fun onWitchThrow(event: ProjectileLaunchEvent) {
        if (!plugin.config.getBoolean("entity-tweaks.witch-heal-against-undead", true)) return
        val projectile = event.entity
        if (projectile !is ThrownPotion) return

        val witch = projectile.shooter as? Witch ?: return
        val target = witch.target ?: return

        // Invert potion mechanics: Healing deals massive instant damage to all Zombie variants
        if (target is Zombie) {
            val healthPotion = ItemStack(Material.SPLASH_POTION)
            val meta = healthPotion.itemMeta as? PotionMeta ?: return

            meta.basePotionType = PotionType.STRONG_HEALING
            meta.addCustomEffect(PotionEffect(PotionEffectType.INSTANT_HEALTH, 1, 1), true)
            healthPotion.itemMeta = meta

            projectile.item = healthPotion
        }
    }
}