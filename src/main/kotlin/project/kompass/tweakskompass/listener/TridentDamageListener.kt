package project.kompass.tweakskompass.listener

import org.bukkit.Tag
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.entity.Trident
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import project.kompass.tweakskompass.TweaksKompass
import project.kompass.tweakskompass.util.TridentUtil
import project.kompass.tweakskompass.util.isTrident
import java.util.Random

class TridentDamageListener(private val plugin: TweaksKompass) : Listener {

    companion object {
        private const val CRITICAL_HIT_THRESHOLD = 0.1
        private const val TRIDENT_BASE_DAMAGE = 10.0
    }

    private val random = Random()

    @EventHandler(priority = EventPriority.NORMAL)
    fun onTridentDamage(event: EntityDamageByEntityEvent) {
        if (!plugin.config.getBoolean("trident-settings.enabled", true)) return
        if (event.cause == EntityDamageEvent.DamageCause.THORNS) return

        val victim = event.entity as? LivingEntity ?: return
        val damager = event.damager

        if (damager is Player) {
            val item = damager.inventory.itemInMainHand
            if (!item.isTrident()) return

            var damage = maxOf(event.damage, TRIDENT_BASE_DAMAGE)

            // Sharpness scaling (+0.5 per level + 0.5)
            if (item.containsEnchantment(Enchantment.SHARPNESS)) {
                damage += (0.5 * item.getEnchantmentLevel(Enchantment.SHARPNESS) + 0.5)
            }

            // Smite scaling (+2.5 per level vs Undead)
            if (item.containsEnchantment(Enchantment.SMITE)) {
                if (Tag.ENTITY_TYPES_SENSITIVE_TO_SMITE.isTagged(victim.type)) {
                    damage += (2.5 * item.getEnchantmentLevel(Enchantment.SMITE))
                }
            }

            // Bane of Arthropods scaling (+2.5 per level + Slowness)
            if (item.containsEnchantment(Enchantment.BANE_OF_ARTHROPODS)) {
                if (Tag.ENTITY_TYPES_SENSITIVE_TO_BANE_OF_ARTHROPODS.isTagged(victim.type)) {
                    val level = item.getEnchantmentLevel(Enchantment.BANE_OF_ARTHROPODS)
                    damage += (2.5 * level)
                    val duration = 20 + random.nextInt(10 * level + 1)
                    victim.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, duration, 3))
                }
            }

            // Impaling scaling (+2.5 per level if wet or aquatic)
            if (item.containsEnchantment(Enchantment.IMPALING)) {
                if (TridentUtil.isVictimWet(victim) || Tag.ENTITY_TYPES_SENSITIVE_TO_IMPALING.isTagged(victim.type)) {
                    damage += (2.5 * item.getEnchantmentLevel(Enchantment.IMPALING))
                }
            }

            // Critical hit calculation
            if (damager.fallDistance > CRITICAL_HIT_THRESHOLD && !damager.isSwimming && !(damager as org.bukkit.entity.Entity).isOnGround) {
                damage *= 1.5
            }

            event.damage = damage

        } else if (damager is Trident) {
            val item = damager.itemStack
            var extraDamage = 0.0

            if (item.containsEnchantment(Enchantment.SHARPNESS)) {
                extraDamage += (0.5 * item.getEnchantmentLevel(Enchantment.SHARPNESS) + 0.5)
            }

            if (item.containsEnchantment(Enchantment.SMITE)) {
                if (Tag.ENTITY_TYPES_SENSITIVE_TO_SMITE.isTagged(victim.type)) {
                    extraDamage += (2.5 * item.getEnchantmentLevel(Enchantment.SMITE))
                }
            }

            if (item.containsEnchantment(Enchantment.BANE_OF_ARTHROPODS)) {
                if (Tag.ENTITY_TYPES_SENSITIVE_TO_BANE_OF_ARTHROPODS.isTagged(victim.type)) {
                    val level = item.getEnchantmentLevel(Enchantment.BANE_OF_ARTHROPODS)
                    extraDamage += (2.5 * level)
                    val duration = 20 + random.nextInt(10 * level + 1)
                    victim.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, duration, 3))
                }
            }

            if (item.containsEnchantment(Enchantment.IMPALING)) {
                if (TridentUtil.isVictimWet(victim) || Tag.ENTITY_TYPES_SENSITIVE_TO_IMPALING.isTagged(victim.type)) {
                    extraDamage += (2.5 * item.getEnchantmentLevel(Enchantment.IMPALING))
                }
            }

            event.damage = event.damage + extraDamage

            if (item.containsEnchantment(Enchantment.FIRE_ASPECT)) {
                event.entity.fireTicks = item.getEnchantmentLevel(Enchantment.FIRE_ASPECT) * 80
            }
        }
    }
}