package project.kompass.tweakskompass.listener

import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.EntityType
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Projectile
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.inventory.PrepareAnvilEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.Damageable
import org.bukkit.inventory.meta.EnchantmentStorageMeta
import java.util.EnumSet
import java.util.Random

class EnchantablePetArmorListener : Listener {

    private val random = Random()

    companion object {
        private val PET_ENTITY_TYPES: Set<EntityType> = EnumSet.of(
            EntityType.HORSE,
            EntityType.DONKEY,
            EntityType.MULE,
            EntityType.ZOMBIE_HORSE,
            EntityType.SKELETON_HORSE,
            EntityType.LLAMA,
            EntityType.TRADER_LLAMA,
            EntityType.WOLF
        )
    }

    private fun isPetArmor(item: ItemStack?): Boolean {
        if (item == null || item.type == Material.AIR) return false
        val name = item.type.name
        return name == "WOLF_ARMOR" ||
                name.contains("HORSE_ARMOR") ||
                name.contains("NAUTILUS")
    }

    private fun getEnchantLevel(item: ItemStack, enchant: Enchantment): Int {
        return if (item.type == Material.ENCHANTED_BOOK) {
            val meta = item.itemMeta as? EnchantmentStorageMeta
            meta?.getStoredEnchantLevel(enchant) ?: 0
        } else {
            item.getEnchantmentLevel(enchant)
        }
    }

    private fun addEnchant(item: ItemStack, enchant: Enchantment, level: Int) {
        if (item.type == Material.ENCHANTED_BOOK) {
            val meta = item.itemMeta as? EnchantmentStorageMeta ?: return
            meta.addStoredEnchant(enchant, level, true)
            item.itemMeta = meta
        } else {
            item.addUnsafeEnchantment(enchant, level)
        }
    }

    @EventHandler
    fun onAnvilUse(event: PrepareAnvilEvent) {
        val first = event.inventory.getItem(0)
        val second = event.inventory.getItem(1)
        if (first == null || second == null) return

        if (!isPetArmor(first)) return

        val result = first.clone()
        val incoming = if (second.type == Material.ENCHANTED_BOOK) {
            (second.itemMeta as EnchantmentStorageMeta).storedEnchants
        } else {
            second.enchantments
        }

        var added = 0

        for ((enchant, incomingLvl) in incoming) {
            val cur = getEnchantLevel(result, enchant)
            var next = if (cur == incomingLvl) cur + 1 else Math.max(cur, incomingLvl)

            // Capped at vanilla max level limit
            val maxLevel = enchant.maxLevel
            next = Math.min(next, maxLevel)

            if (next > cur) {
                addEnchant(result, enchant, next)
                added++
            }
        }

        if (added > 0) {
            val meta = result.itemMeta ?: return
            val renameText = event.view.renameText

            if (!renameText.isNullOrEmpty()) {
                meta.displayName(net.kyori.adventure.text.Component.text(renameText))
            }

            result.itemMeta = meta
            event.result = result
            event.view.repairCost = Math.min(added, 5)
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onPetDamage(event: EntityDamageEvent) {
        val entity = event.entity as? LivingEntity ?: return
        if (!PET_ENTITY_TYPES.contains(entity.type)) return

        val armor = getEquippedPetArmor(entity) ?: return
        var damage = event.damage

        if (armor.containsEnchantment(Enchantment.PROTECTION)) {
            val level = armor.getEnchantmentLevel(Enchantment.PROTECTION)
            val reduction = Math.min(0.8, level * 0.04)
            damage *= (1.0 - reduction)
        }

        val cause = event.cause
        if (cause == EntityDamageEvent.DamageCause.FIRE ||
            cause == EntityDamageEvent.DamageCause.FIRE_TICK ||
            cause == EntityDamageEvent.DamageCause.LAVA ||
            cause == EntityDamageEvent.DamageCause.MELTING) {

            if (armor.containsEnchantment(Enchantment.FIRE_PROTECTION)) {
                val level = armor.getEnchantmentLevel(Enchantment.FIRE_PROTECTION)
                val reduction = Math.min(0.8, level * 0.08)
                damage *= (1.0 - reduction)
            }
        }

        if (cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION ||
            cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {

            if (armor.containsEnchantment(Enchantment.BLAST_PROTECTION)) {
                val level = armor.getEnchantmentLevel(Enchantment.BLAST_PROTECTION)
                val reduction = Math.min(0.8, level * 0.08)
                damage *= (1.0 - reduction)
            }
        }

        if (event is EntityDamageByEntityEvent) {
            if (event.damager is Projectile) {
                if (armor.containsEnchantment(Enchantment.PROJECTILE_PROTECTION)) {
                    val level = armor.getEnchantmentLevel(Enchantment.PROJECTILE_PROTECTION)
                    val reduction = Math.min(0.8, level * 0.08)
                    damage *= (1.0 - reduction)
                }
            }
        }

        event.damage = damage
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onPetThornsDamage(event: EntityDamageByEntityEvent) {
        val entity = event.entity as? LivingEntity ?: return
        if (!PET_ENTITY_TYPES.contains(entity.type)) return

        val attacker = event.damager as? LivingEntity ?: return
        val armor = getEquippedPetArmor(entity) ?: return

        if (armor.containsEnchantment(Enchantment.THORNS)) {
            val level = armor.getEnchantmentLevel(Enchantment.THORNS)
            val chance = level * 0.15

            if (random.nextDouble() < chance) {
                val thornsDamage = 1.0 + random.nextInt(4)
                attacker.damage(thornsDamage, entity)

                entity.world.playSound(entity.location, Sound.ENCHANT_THORNS_HIT, 1.0f, 1.0f)
                damagePetArmorDurability(entity, armor, 2)
            }
        }
    }

    private fun getEquippedPetArmor(entity: LivingEntity): ItemStack? {
        val equipment = entity.equipment ?: return null
        var armor = equipment.getItem(EquipmentSlot.BODY)
        if (armor == null || armor.type == Material.AIR) {
            armor = equipment.getItem(EquipmentSlot.CHEST)
        }
        return if (isPetArmor(armor)) armor else null
    }

    private fun damagePetArmorDurability(entity: LivingEntity, armor: ItemStack, amount: Int) {
        val meta = armor.itemMeta ?: return
        if (meta is Damageable) {
            if (armor.containsEnchantment(Enchantment.UNBREAKING)) {
                val level = armor.getEnchantmentLevel(Enchantment.UNBREAKING)
                val chance = 1.0 / (level + 1)
                if (random.nextDouble() >= chance) {
                    return
                }
            }

            val newDamage = meta.damage + amount
            if (newDamage >= armor.type.maxDurability) {
                entity.world.playSound(entity.location, Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f)
                val equipment = entity.equipment
                if (equipment != null) {
                    var slot = EquipmentSlot.BODY
                    val currentBody = equipment.getItem(EquipmentSlot.BODY)
                    if (currentBody == null || currentBody.type == Material.AIR) {
                        slot = EquipmentSlot.CHEST
                    }
                    equipment.setItem(slot, null)
                }
            } else {
                meta.damage = newDamage
                armor.itemMeta = meta
            }
        }
    }
}