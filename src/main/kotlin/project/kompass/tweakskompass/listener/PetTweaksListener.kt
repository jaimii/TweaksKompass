package project.kompass.tweakskompass.listener

import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.block.BlockFace
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.*
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.EntityTargetLivingEntityEvent
import org.bukkit.event.inventory.PrepareAnvilEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.event.world.ChunkUnloadEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.Damageable
import org.bukkit.inventory.meta.EnchantmentStorageMeta
import org.bukkit.scheduler.BukkitRunnable
import project.kompass.tweakskompass.TweaksKompass
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom

class PetTweaksListener(private val plugin: TweaksKompass) : Listener {

    private val lastAlertTime = ConcurrentHashMap<UUID, Long>()

    private var retreatEnabled = true
    private var healthThreshold = 0.30
    private var fleeToOwner = true
    private var playRetreatSound = true
    private var alertOwner = true

    companion object {
        private val COMPANION_PET_TYPES: Set<EntityType> = EnumSet.of(
            EntityType.WOLF,
            EntityType.CAT,
            EntityType.PARROT
        )

        private val PET_ARMOR_ENTITY_TYPES: Set<EntityType> = EnumSet.of(
            EntityType.HORSE, EntityType.DONKEY, EntityType.MULE,
            EntityType.ZOMBIE_HORSE, EntityType.SKELETON_HORSE,
            EntityType.LLAMA, EntityType.TRADER_LLAMA, EntityType.WOLF
        )

        private val ALLOWED_PET_ENCHANTS: Set<Enchantment> = setOf(
            Enchantment.PROTECTION, Enchantment.FIRE_PROTECTION,
            Enchantment.BLAST_PROTECTION, Enchantment.PROJECTILE_PROTECTION,
            Enchantment.THORNS, Enchantment.UNBREAKING, Enchantment.MENDING
        )
    }

    init {
        reloadConfig()
    }

    fun reloadConfig() {
        val cfg = plugin.config
        retreatEnabled = cfg.getBoolean("pet-tweaks.retreat.enabled", true)
        healthThreshold = cfg.getDouble("pet-tweaks.retreat.health-threshold", 0.30)
        fleeToOwner = cfg.getBoolean("pet-tweaks.retreat.flee-to-owner", true)
        playRetreatSound = cfg.getBoolean("pet-tweaks.retreat.play-sound", true)
        alertOwner = cfg.getBoolean("pet-tweaks.retreat.alert-owner", true)
    }

    private fun isPetArmor(item: ItemStack?): Boolean {
        if (item == null || item.type == Material.AIR) return false
        val name = item.type.name
        return name == "WOLF_ARMOR" || name.contains("HORSE_ARMOR") || name.contains("NAUTILUS")
    }

    private fun isLowHealth(pet: LivingEntity): Boolean {
        val maxHealth = pet.getAttribute(Attribute.MAX_HEALTH)?.value ?: 20.0
        return (pet.health / maxHealth) <= healthThreshold
    }

    // 1. Low-Health Retreat & Survival Instinct
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onPetTakeDamage(event: EntityDamageEvent) {
        if (!retreatEnabled) return
        val pet = event.entity as? LivingEntity ?: return
        if (pet.type !in COMPANION_PET_TYPES) return
        if (pet !is Tameable || !pet.isTamed) return

        val maxHealth = pet.getAttribute(Attribute.MAX_HEALTH)?.value ?: 20.0
        val remainingHealth = pet.health - event.finalDamage

        if (remainingHealth > 0 && (remainingHealth / maxHealth) <= healthThreshold) {
            triggerRetreat(pet)
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPetAcquireTarget(event: EntityTargetLivingEntityEvent) {
        if (!retreatEnabled) return
        val pet = event.entity as? LivingEntity ?: return
        if (pet.type !in COMPANION_PET_TYPES) return
        if (pet !is Tameable || !pet.isTamed) return

        if (isLowHealth(pet)) {
            event.isCancelled = true
            triggerRetreat(pet)
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPetAttack(event: EntityDamageByEntityEvent) {
        if (!retreatEnabled) return
        val pet = event.damager as? LivingEntity ?: return
        if (pet.type !in COMPANION_PET_TYPES) return
        if (pet !is Tameable || !pet.isTamed) return

        if (isLowHealth(pet)) {
            event.isCancelled = true
            triggerRetreat(pet)
        }
    }

    private fun triggerRetreat(pet: LivingEntity) {
        if (pet is Mob) {
            pet.target = null
        }

        val owner = (pet as? Tameable)?.owner as? Player ?: return
        if (!owner.isOnline || (pet is Sittable && pet.isSitting)) return

        if (fleeToOwner && pet is Mob) {
            pet.pathfinder.moveTo(owner.location, 1.35)
        }

        if (playRetreatSound) {
            pet.world.playSound(pet.location, Sound.ENTITY_WOLF_WHINE, 1.0f, 1.0f)
        }

        if (alertOwner) {
            val now = System.currentTimeMillis()
            val last = lastAlertTime[pet.uniqueId] ?: 0L
            if (now - last > 10_000L) {
                lastAlertTime[pet.uniqueId] = now
                owner.sendRichMessage("<gold><bold>Pet Alert:</bold> <yellow>Your pet is badly hurt and retreating!</yellow></gold>")
            }
        }
    }

    @EventHandler
    fun onPetDeath(event: EntityDeathEvent) {
        lastAlertTime.remove(event.entity.uniqueId)
    }

    // 2. Enchantable Pet Armor
    @EventHandler
    fun onAnvilUse(event: PrepareAnvilEvent) {
        if (!plugin.config.getBoolean("pet-tweaks.armor.enabled", true)) return

        val first = event.inventory.getItem(0) ?: return
        val second = event.inventory.getItem(1) ?: return
        if (!isPetArmor(first)) return

        val result = first.clone()
        val incoming = if (second.type == Material.ENCHANTED_BOOK) {
            (second.itemMeta as? EnchantmentStorageMeta)?.storedEnchants ?: emptyMap()
        } else {
            second.enchantments
        }

        var added = 0
        for ((enchant, incomingLvl) in incoming) {
            if (!ALLOWED_PET_ENCHANTS.contains(enchant)) continue

            val curLvl = result.getEnchantmentLevel(enchant)
            var nextLvl = if (curLvl == incomingLvl) curLvl + 1 else maxOf(curLvl, incomingLvl)
            nextLvl = minOf(nextLvl, enchant.maxLevel)

            if (nextLvl > curLvl) {
                result.addUnsafeEnchantment(enchant, nextLvl)
                added++
            }
        }

        if (added > 0) {
            val meta = result.itemMeta
            val renameText = event.view.renameText
            if (!renameText.isNullOrEmpty()) {
                meta?.displayName(net.kyori.adventure.text.Component.text(renameText))
            }
            result.itemMeta = meta
            event.result = result
            event.view.repairCost = minOf(added, 5)
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onPetDamageReduction(event: EntityDamageEvent) {
        if (!plugin.config.getBoolean("pet-tweaks.armor.enabled", true)) return
        val entity = event.entity as? LivingEntity ?: return
        if (!PET_ARMOR_ENTITY_TYPES.contains(entity.type)) return

        val armor = getEquippedPetArmor(entity) ?: return
        var damage = event.damage

        if (armor.containsEnchantment(Enchantment.PROTECTION)) {
            val level = armor.getEnchantmentLevel(Enchantment.PROTECTION)
            damage *= (1.0 - minOf(0.8, level * 0.04))
        }

        val cause = event.cause
        if (cause == EntityDamageEvent.DamageCause.FIRE || cause == EntityDamageEvent.DamageCause.FIRE_TICK ||
            cause == EntityDamageEvent.DamageCause.LAVA || cause == EntityDamageEvent.DamageCause.MELTING
        ) {
            if (armor.containsEnchantment(Enchantment.FIRE_PROTECTION)) {
                val level = armor.getEnchantmentLevel(Enchantment.FIRE_PROTECTION)
                damage *= (1.0 - minOf(0.8, level * 0.08))
            }
        }

        if (cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION || cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
            if (armor.containsEnchantment(Enchantment.BLAST_PROTECTION)) {
                val level = armor.getEnchantmentLevel(Enchantment.BLAST_PROTECTION)
                damage *= (1.0 - minOf(0.8, level * 0.08))
            }
        }

        if (event is EntityDamageByEntityEvent && event.damager is Projectile) {
            if (armor.containsEnchantment(Enchantment.PROJECTILE_PROTECTION)) {
                val level = armor.getEnchantmentLevel(Enchantment.PROJECTILE_PROTECTION)
                damage *= (1.0 - minOf(0.8, level * 0.08))
            }
        }

        event.damage = damage
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onPetThornsDamage(event: EntityDamageByEntityEvent) {
        if (!plugin.config.getBoolean("pet-tweaks.armor.enabled", true)) return
        val entity = event.entity as? LivingEntity ?: return
        if (!PET_ARMOR_ENTITY_TYPES.contains(entity.type)) return

        val attacker = event.damager as? LivingEntity ?: return
        val armor = getEquippedPetArmor(entity) ?: return

        if (armor.containsEnchantment(Enchantment.THORNS)) {
            val level = armor.getEnchantmentLevel(Enchantment.THORNS)
            if (ThreadLocalRandom.current().nextDouble() < level * 0.15) {
                val thornsDamage = 1.0 + ThreadLocalRandom.current().nextInt(4)
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
        if (armor.type.maxDurability <= 0) return

        val meta = armor.itemMeta as? Damageable ?: return
        if (armor.containsEnchantment(Enchantment.UNBREAKING)) {
            val level = armor.getEnchantmentLevel(Enchantment.UNBREAKING)
            if (ThreadLocalRandom.current().nextDouble() >= (1.0 / (level + 1))) return
        }

        val newDamage = meta.damage + amount
        if (newDamage >= armor.type.maxDurability) {
            entity.world.playSound(entity.location, Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f)
            val equipment = entity.equipment
            if (equipment != null) {
                val slot = if (equipment.getItem(EquipmentSlot.BODY) != null) EquipmentSlot.BODY else EquipmentSlot.CHEST
                equipment.setItem(slot, null)
            }
        } else {
            meta.damage = newDamage
            armor.itemMeta = meta
        }
    }

    // 3. Companion Pet Teleportation & Rescue (Dogs, Cats, Parrots Only)
    fun startTasks() {
        val interval = plugin.config.getInt("pet-tweaks.teleportation.distance-teleport.check-interval", 60).toLong().coerceAtLeast(20L)

        object : BukkitRunnable() {
            override fun run() {
                if (!plugin.config.getBoolean("pet-tweaks.teleportation.enabled", true)) return
                if (!plugin.config.getBoolean("pet-tweaks.teleportation.distance-teleport.enabled", true)) return

                val maxDist = plugin.config.getDouble("pet-tweaks.teleportation.distance-teleport.max-distance", 24.0)
                val maxDistSq = maxDist * maxDist
                val searchRadius = (maxDist + 12.0).coerceIn(24.0, 40.0)

                for (player in Bukkit.getOnlinePlayers()) {
                    if (!player.isValid || player.isDead || player.gameMode == GameMode.SPECTATOR) continue

                    val pLoc = player.location
                    val pUid = player.uniqueId

                    val nearbyPets = player.world.getNearbyEntities(pLoc, searchRadius, searchRadius, searchRadius) { entity ->
                        entity.type in COMPANION_PET_TYPES &&
                                entity is Tameable && entity.isTamed &&
                                entity.owner?.uniqueId == pUid &&
                                (entity !is Sittable || !entity.isSitting)
                    }

                    for (nearby in nearbyPets) {
                        if (nearby.isValid && !nearby.isDead && nearby.location.distanceSquared(pLoc) >= maxDistSq) {
                            teleportPetSafely(nearby, pLoc)
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, interval, interval)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerTeleport(event: PlayerTeleportEvent) {
        if (!plugin.config.getBoolean("pet-tweaks.teleportation.enabled", true)) return
        if (!plugin.config.getBoolean("pet-tweaks.teleportation.teleport-on-player-teleport", true)) return

        val player = event.player
        val from = event.from
        val to = event.to // Non-nullable in Paper 26.3 (removed redundant ?: return)

        val keepSeconds = plugin.config.getInt("pet-tweaks.teleportation.keep-chunk-loaded-seconds", 0)
        if (keepSeconds > 0) {
            val fromChunk = from.chunk
            if (!fromChunk.isForceLoaded) {
                fromChunk.addPluginChunkTicket(plugin)
                plugin.server.scheduler.runTaskLater(plugin, Runnable { fromChunk.removePluginChunkTicket(plugin) }, keepSeconds * 20L)
            }
        }

        val crossWorld = plugin.config.getBoolean("pet-tweaks.teleportation.cross-world", true)
        if (!crossWorld && from.world != to.world) return

        val fromWorld = from.world ?: return
        val pUid = player.uniqueId
        val petsToTeleport = fromWorld.getNearbyEntities(from, 36.0, 36.0, 36.0) { entity ->
            entity.type in COMPANION_PET_TYPES &&
                    entity is Tameable && entity.isTamed &&
                    entity.owner?.uniqueId == pUid &&
                    (entity !is Sittable || !entity.isSitting)
        }

        if (petsToTeleport.isEmpty()) return

        plugin.server.scheduler.runTask(plugin, Runnable {
            if (!player.isOnline || player.isDead) return@Runnable
            for (pet in petsToTeleport) {
                if (pet.isValid && !pet.isDead) {
                    teleportPetSafely(pet, to)
                }
            }
        })
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onChunkUnload(event: ChunkUnloadEvent) {
        if (!plugin.config.getBoolean("pet-tweaks.teleportation.enabled", true)) return
        if (!plugin.config.getBoolean("pet-tweaks.teleportation.teleport-on-chunk-unload", true)) return

        val chunk = event.chunk
        val crossWorld = plugin.config.getBoolean("pet-tweaks.teleportation.cross-world", true)

        for (entity in chunk.entities) {
            if (entity.type !in COMPANION_PET_TYPES) continue
            if (entity !is Tameable || !entity.isTamed) continue
            if (entity is Sittable && entity.isSitting) continue

            val owner = entity.owner as? Player ?: continue
            if (!owner.isOnline || owner.isDead || owner.gameMode == GameMode.SPECTATOR) continue
            if (!crossWorld && entity.world != owner.world) continue

            teleportPetSafely(entity, owner.location)
        }
    }

    private fun teleportPetSafely(pet: Entity, destination: Location) {
        val targetLoc = findSafeLocation(destination) ?: destination
        pet.teleport(targetLoc)
        pet.fallDistance = 0f
    }

    private fun findSafeLocation(center: Location): Location? {
        val baseBlock = center.block
        val offsets = arrayOf(
            intArrayOf(1, 0), intArrayOf(-1, 0), intArrayOf(0, 1),
            intArrayOf(0, -1), intArrayOf(1, 1), intArrayOf(-1, -1)
        )
        for (offset in offsets) {
            val candidate = baseBlock.getRelative(offset[0], 0, offset[1])
            val below = candidate.getRelative(BlockFace.DOWN)
            val above = candidate.getRelative(BlockFace.UP)
            if (below.type.isSolid && !candidate.type.isSolid && !above.type.isSolid) {
                return candidate.location.add(0.5, 0.0, 0.5)
            }
        }
        return center
    }
}