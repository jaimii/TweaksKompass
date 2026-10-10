package project.kompass.tweakskompass.listener

import project.kompass.tweakskompass.TweaksKompass
import project.kompass.tweakskompass.util.TridentUtil
import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.*
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockIgniteEvent
import org.bukkit.event.entity.*
import org.bukkit.persistence.PersistentDataType
import java.util.concurrent.TimeUnit
import java.util.UUID
import java.util.HashMap

class TridentChannelingListener(private val plugin: TweaksKompass) : Listener {

    // Memory cache tracking ONLY specific channeling mob drops and experience orbs
    private val protectedEntitiesCache: Cache<UUID, Boolean> = CacheBuilder.newBuilder()
        .expireAfterWrite(10, TimeUnit.MINUTES)
        .build()

    private val recentChannelingDeaths = HashMap<Location, Long>()

    // Handles Channeling logic when a thrown trident hits a target
    @EventHandler
    fun onTridentHit(event: ProjectileHitEvent) {
        val trident = event.entity as? Trident ?: return
        val item = trident.itemStack

        if (item.containsEnchantment(Enchantment.CHANNELING)) {
            val hitEntity = event.hitEntity
            val hitBlock = event.hitBlock
            val targetLoc = hitEntity?.location?.clone() ?: hitBlock?.location?.clone() ?: trident.location.clone()
            val shooter = trident.shooter as? Player

            Bukkit.getScheduler().runTask(plugin, Runnable {
                val strike = trident.world.spawn(targetLoc, LightningStrike::class.java)
                strike.persistentDataContainer.set(TridentUtil.CHANNELING_LIGHTNING_KEY, PersistentDataType.BYTE, 1.toByte())
                shooter?.let { strike.causingPlayer = it }

                if (hitEntity is LivingEntity && hitEntity.isValid && !hitEntity.isDead) {
                    hitEntity.noDamageTicks = 0
                }
            })
        }
    }

    // Channeling on ANY item during melee attacks
    @EventHandler
    fun onMeleeHit(event: EntityDamageByEntityEvent) {
        if (event.cause == EntityDamageEvent.DamageCause.THORNS) return

        val player = event.damager as? Player ?: return
        val item = player.inventory.itemInMainHand

        if (item.containsEnchantment(Enchantment.CHANNELING)) {
            val victim = event.entity
            val targetLoc = victim.location.clone()

            Bukkit.getScheduler().runTask(plugin, Runnable {
                val strike = victim.world.spawn(targetLoc, LightningStrike::class.java)
                strike.persistentDataContainer.set(TridentUtil.CHANNELING_LIGHTNING_KEY, PersistentDataType.BYTE, 1.toByte())
                strike.causingPlayer = player

                if (victim is LivingEntity && victim.isValid && !victim.isDead) {
                    victim.noDamageTicks = 0
                }
            })
        }
    }

    // Handles lightning damage calculations & selective item/player protection
    @Suppress("DEPRECATION")
    @EventHandler
    fun onLightningDamage(event: EntityDamageByEntityEvent) {
        val lightning = event.damager as? LightningStrike ?: return
        val entity = event.entity

        val isChanneling = lightning.persistentDataContainer.has(
            TridentUtil.CHANNELING_LIGHTNING_KEY,
            PersistentDataType.BYTE
        )

        // 1. Item & XP Orb Damage Logic
        if (entity is Item || entity is ExperienceOrb) {
            // NATURAL LIGHTNING: Always destroys all items/orbs!
            if (!isChanneling) {
                event.damage = 5.0
                return
            }

            // CHANNELING LIGHTNING: Only protect drops from the killed mob (in cache).
            // Player-thrown items or regular items on the ground will NOT be in cache and get destroyed!
            if (protectedEntitiesCache.getIfPresent(entity.uniqueId) != null) {
                event.isCancelled = true
            } else {
                event.damage = 2.0 // Destroys non-protected items
            }
            return
        }

        // 2. Prevent player self-damage from their own channeling strike
        if (entity is Player && entity == lightning.causingPlayer) {
            event.isCancelled = true
            return
        }

        // 3. Set lightning damage values
        if (isChanneling) {
            event.damage = 2.0 // 1 heart
        } else {
            event.damage = 5.0 // 2.5 hearts (Natural)
        }

        if (event.isApplicable(EntityDamageEvent.DamageModifier.ARMOR)) {
            event.setDamage(EntityDamageEvent.DamageModifier.ARMOR, 0.0)
        }
    }

    // Track mob deaths caused by channeling
    @EventHandler
    fun onMobDeath(event: EntityDeathEvent) {
        val mob = event.entity as? Mob ?: return
        var killedByChanneling = false

        val damageEvent = mob.lastDamageCause as? EntityDamageByEntityEvent
        if (damageEvent != null) {
            val damager = damageEvent.damager
            if (damager is Trident) {
                if (damager.itemStack.containsEnchantment(Enchantment.CHANNELING)) {
                    killedByChanneling = true
                }
            } else if (damager is Player) {
                val hand = damager.inventory.itemInMainHand
                if (hand.containsEnchantment(Enchantment.CHANNELING)) {
                    killedByChanneling = true
                }
            } else if (damager is LightningStrike) {
                if (damager.causingPlayer != null) {
                    killedByChanneling = true
                }
            }
        }

        if (killedByChanneling) {
            recentChannelingDeaths[mob.location] = mob.world.fullTime
        }
    }

    // Protects ONLY Item drops and ExperienceOrbs produced by channeling deaths on spawn
    @EventHandler(priority = EventPriority.LOWEST)
    fun onEntitySpawn(event: EntitySpawnEvent) {
        if (recentChannelingDeaths.isEmpty()) return

        val entity = event.entity
        if (entity !is Item && entity !is ExperienceOrb) return

        val loc = entity.location
        val world = entity.world
        val currentTick = world.fullTime

        var isChannelingDrop = false
        val iterator = recentChannelingDeaths.entries.iterator()

        while (iterator.hasNext()) {
            val entry = iterator.next()
            val deathLoc = entry.key
            val deathTick = entry.value

            if (currentTick - deathTick > 10L) {
                iterator.remove()
                continue
            }

            if (deathLoc.world == world && deathLoc.distanceSquared(loc) <= 4.0) {
                isChannelingDrop = true
            }
        }

        if (isChannelingDrop) {
            protectedEntitiesCache.put(entity.uniqueId, true)
            entity.fireTicks = 0
        }
    }

    // Cancel environment fire/lava damage ONLY for protected mob drops and XP orbs
    @EventHandler
    fun onEntityDamage(event: EntityDamageEvent) {
        val entity = event.entity
        if (entity is Item || entity is ExperienceOrb) {
            if (protectedEntitiesCache.getIfPresent(entity.uniqueId) != null) {
                event.isCancelled = true
                entity.fireTicks = 0
            }
        }
    }

    // Propagate protection to merged item stacks
    @EventHandler
    fun onItemMerge(event: ItemMergeEvent) {
        val target = event.target
        val entity = event.entity

        if (protectedEntitiesCache.getIfPresent(entity.uniqueId) != null ||
            protectedEntitiesCache.getIfPresent(target.uniqueId) != null) {
            protectedEntitiesCache.put(target.uniqueId, true)
        }

        protectedEntitiesCache.invalidate(entity.uniqueId)
    }

    @EventHandler
    fun onItemPickup(event: EntityPickupItemEvent) {
        protectedEntitiesCache.invalidate(event.item.uniqueId)
    }

    @EventHandler
    fun onItemDespawn(event: ItemDespawnEvent) {
        protectedEntitiesCache.invalidate(event.entity.uniqueId)
    }

    // Prevents custom channeling lightning from igniting surrounding blocks (creating fire)
    @EventHandler
    fun onBlockIgnite(event: BlockIgniteEvent) {
        if (event.cause == BlockIgniteEvent.IgniteCause.LIGHTNING) {
            val igniter = event.ignitingEntity as? LightningStrike ?: return

            if (igniter.persistentDataContainer.has(TridentUtil.CHANNELING_LIGHTNING_KEY, PersistentDataType.BYTE)) {
                event.isCancelled = true
            }
        }
    }
}