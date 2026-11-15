package project.kompass.tweakskompass.entity

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.craftbukkit.CraftWorld
import org.bukkit.craftbukkit.entity.CraftLivingEntity
import org.bukkit.entity.*
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.entity.*
import org.bukkit.event.entity.EntityDamageEvent.DamageCause
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import project.kompass.tweakskompass.TweaksKompass
import java.util.EnumSet
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.cos
import kotlin.math.sin

class EntityTweaksListener(private val plugin: TweaksKompass) : Listener {

    companion object {
        private val SKELETON_SHIELD_TYPES: Set<EntityType> = EnumSet.noneOf(EntityType::class.java).apply {
            for (name in listOf("SKELETON", "STRAY", "BOGGED", "PARCHED")) {
                runCatching { EntityType.valueOf(name) }.getOrNull()?.let { add(it) }
            }
        }

        private val GOLEM_FACTION_TYPES: Set<EntityType> = EnumSet.noneOf(EntityType::class.java).apply {
            for (name in listOf("IRON_GOLEM", "SNOW_GOLEM", "COPPER_GOLEM")) {
                runCatching { EntityType.valueOf(name) }.getOrNull()?.let { add(it) }
            }
        }

        private val AXE_MATERIALS: Set<Material> = EnumSet.noneOf(Material::class.java).apply {
            for (mat in Material.entries) {
                if (!mat.name.startsWith("LEGACY_") && mat.name.endsWith("_AXE")) {
                    add(mat)
                }
            }
        }

        fun isGolemFaction(entity: Entity): Boolean = GOLEM_FACTION_TYPES.contains(entity.type)

        fun isRaiderOrWitch(entity: Entity): Boolean = entity is Raider || entity is Witch

        // Zero-allocation O(1) faction check shared with ThreatTableListener
        fun isSameMobFaction(a: Entity, b: Entity, preventIllagerSkeletonFriendlyFire: Boolean = true): Boolean {
            if (isGolemFaction(a) && isGolemFaction(b)) return true
            if (preventIllagerSkeletonFriendlyFire) {
                if (a is AbstractSkeleton && b is AbstractSkeleton) return true
                if (isRaiderOrWitch(a) && isRaiderOrWitch(b)) return true
            }
            return false
        }
    }

    private val skeletonShieldCooldowns = ConcurrentHashMap<UUID, Int>()

    private var isIllusionerDarknessEnabled = true
    private var isXpDropsEnabled = true
    private var isPreventFriendlyFireEnabled = true
    private var isArmorStandsHaveArmsEnabled = true
    private var isGolemIgnoreZombieVillagersEnabled = true
    private var isNoDaylightBurningEnabled = true
    private var isNoOverworldZombificationEnabled = true
    private var isPreventCropTrampleEnabled = true
    private var isProtectTurtleEggsEnabled = true
    private var isAxolotlBreedEnabled = true
    private var isAxolotlIgnorePassivesEnabled = true

    private var isEvokerTotemEnabled = true
    private var isSkeletonShieldEnabled = true
    private var isSkeletonActiveBlockEnabled = true
    private var skeletonBlockChance = 0.35
    private var skeletonAxeDisableTicks = 100
    private var isSilverfishVexLowHealthEnabled = true
    private var silverfishVexHealth = 2.0
    private var isCreeperSpeedEnabled = true
    private var creeperMovementSpeed = 0.22
    private var isIronGolemBuffEnabled = true
    private var ironGolemHealth = 150.0
    private var ironGolemMovementSpeed = 0.30

    init {
        reloadConfig()
    }

    fun reloadConfig() {
        val cfg = plugin.config
        isIllusionerDarknessEnabled = cfg.getBoolean("entity-tweaks.illusioner-darkness", true)
        isXpDropsEnabled = cfg.getBoolean("entity-tweaks.environmental-xp-drops", true)
        isPreventFriendlyFireEnabled = cfg.getBoolean("entity-tweaks.prevent-illager-friendly-fire", true)
        isArmorStandsHaveArmsEnabled = cfg.getBoolean("entity-tweaks.armor-stands-have-arms", true)
        isGolemIgnoreZombieVillagersEnabled = cfg.getBoolean("entity-tweaks.iron-golem-ignore-zombie-villagers", true)
        isNoDaylightBurningEnabled = cfg.getBoolean("entity-tweaks.no-daylight-burning", true)
        isNoOverworldZombificationEnabled = cfg.getBoolean("entity-tweaks.no-overworld-zombification", true)
        isPreventCropTrampleEnabled = cfg.getBoolean("entity-tweaks.prevent-crop-trample", true)
        isProtectTurtleEggsEnabled = cfg.getBoolean("entity-tweaks.protect-turtle-eggs", true)
        isAxolotlBreedEnabled = cfg.getBoolean("entity-tweaks.axolotl-breed-tropical-fish", true)
        isAxolotlIgnorePassivesEnabled = cfg.getBoolean("entity-tweaks.axolotl-ignore-passives", true)

        isEvokerTotemEnabled = cfg.getBoolean("entity-tweaks.evoker-offhand-totem", true)
        isSkeletonShieldEnabled = cfg.getBoolean("entity-tweaks.skeleton-offhand-shield", true)
        isSkeletonActiveBlockEnabled = cfg.getBoolean("entity-tweaks.skeleton-active-shield-block", true)
        skeletonBlockChance = cfg.getDouble("entity-tweaks.skeleton-shield-block-chance", 0.35).coerceIn(0.0, 1.0)
        skeletonAxeDisableTicks = cfg.getInt("entity-tweaks.skeleton-axe-disable-ticks", 100).coerceAtLeast(0)
        isSilverfishVexLowHealthEnabled = cfg.getBoolean("entity-tweaks.silverfish-vex-low-health", true)
        silverfishVexHealth = cfg.getDouble("entity-tweaks.silverfish-vex-health", 2.0).coerceAtLeast(1.0)
        isCreeperSpeedEnabled = cfg.getBoolean("entity-tweaks.creeper-movement-speed-enabled", true)
        creeperMovementSpeed = cfg.getDouble("entity-tweaks.creeper-movement-speed", 0.22).coerceIn(0.05, 1.0)
        isIronGolemBuffEnabled = cfg.getBoolean("entity-tweaks.iron-golem-buff-enabled", true)
        ironGolemHealth = cfg.getDouble("entity-tweaks.iron-golem-health", 150.0).coerceAtLeast(20.0)
        ironGolemMovementSpeed = cfg.getDouble("entity-tweaks.iron-golem-movement-speed", 0.30).coerceIn(0.05, 1.0)
    }

    // 1. Unified Spawn Equipment & Attribute Tweaks
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onEntitySpawnTweaks(event: EntitySpawnEvent) {
        val entity = event.entity

        if (entity is ArmorStand) {
            if (isArmorStandsHaveArmsEnabled) {
                entity.setArms(true)
            }
            return
        }

        if (entity !is LivingEntity) return

        when (entity.type) {
            EntityType.EVOKER -> {
                if (isEvokerTotemEnabled) {
                    entity.equipment?.let { eq ->
                        eq.setItemInOffHand(ItemStack(Material.TOTEM_OF_UNDYING))
                        eq.itemInOffHandDropChance = 0f
                    }
                }
            }

            EntityType.SILVERFISH, EntityType.VEX -> {
                if (isSilverfishVexLowHealthEnabled) {
                    entity.getAttribute(Attribute.MAX_HEALTH)?.baseValue = silverfishVexHealth
                    entity.health = silverfishVexHealth
                }
            }

            EntityType.CREEPER -> {
                if (isCreeperSpeedEnabled) {
                    entity.getAttribute(Attribute.MOVEMENT_SPEED)?.baseValue = creeperMovementSpeed
                }
            }

            EntityType.IRON_GOLEM -> {
                if (isIronGolemBuffEnabled && entity is IronGolem) {
                    Bukkit.getScheduler().runTask(plugin, Runnable {
                        if (!entity.isValid || entity.isDead) return@Runnable
                        if (isDreadnoughtGolem(entity)) return@Runnable

                        entity.getAttribute(Attribute.MAX_HEALTH)?.baseValue = ironGolemHealth
                        entity.health = ironGolemHealth
                        entity.getAttribute(Attribute.MOVEMENT_SPEED)?.baseValue = ironGolemMovementSpeed
                    })
                }
            }

            else -> {
                if (isSkeletonShieldEnabled && SKELETON_SHIELD_TYPES.contains(entity.type)) {
                    entity.equipment?.let { eq ->
                        if (eq.itemInOffHand.type.isAir) {
                            eq.setItemInOffHand(ItemStack(Material.SHIELD))
                            eq.itemInOffHandDropChance = 0f
                        }
                    }
                }
            }
        }
    }

    private fun isDreadnoughtGolem(golem: IronGolem): Boolean {
        val maxHp = golem.getAttribute(Attribute.MAX_HEALTH)?.baseValue ?: 100.0
        if (maxHp >= 500.0) return true

        val pdc = golem.persistentDataContainer
        if (!pdc.isEmpty) {
            for (key in pdc.keys) {
                if (key.key.contains("dreadnought", ignoreCase = true)) return true
            }
        }
        return false
    }

    // 2. Unified EntityDamageByEntityEvent Handler (Friendly Fire, Golem Protection, Skeleton Shield Blocking)
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onEntityDamageByEntity(event: EntityDamageByEntityEvent) {
        val rawDamager = event.damager
        val damaged = event.entity

        val attacker = if (rawDamager is Projectile) {
            (rawDamager.shooter as? Entity) ?: rawDamager
        } else {
            rawDamager
        }

        // A. Faction Friendly Fire (Golems, Skeletons, Raiders/Witches)
        if (isSameMobFaction(attacker, damaged, isPreventFriendlyFireEnabled)) {
            event.isCancelled = true
            return
        }

        // B. Iron Golem -> Zombie Villager Immunity
        if (isGolemIgnoreZombieVillagersEnabled && attacker is IronGolem && damaged is ZombieVillager) {
            event.isCancelled = true
            return
        }

        // C. Skeleton Active Shield Deflection & Axe Guard Break
        if (isSkeletonActiveBlockEnabled && damaged is LivingEntity && SKELETON_SHIELD_TYPES.contains(damaged.type)) {
            val offHand = damaged.equipment?.itemInOffHand
            if (offHand != null && offHand.type == Material.SHIELD) {
                val currentTick = Bukkit.getCurrentTick()
                val disabledUntil = skeletonShieldCooldowns[damaged.uniqueId] ?: 0

                if (currentTick >= disabledUntil && isFrontalHit(damaged, rawDamager)) {
                    if (isAxeAttack(rawDamager)) {
                        skeletonShieldCooldowns[damaged.uniqueId] = currentTick + skeletonAxeDisableTicks
                        damaged.world.playSound(damaged.location, Sound.ITEM_SHIELD_BREAK, 1.0f, 0.8f)
                        return
                    }

                    if (ThreadLocalRandom.current().nextDouble() < skeletonBlockChance) {
                        event.isCancelled = true
                        damaged.world.playSound(damaged.location, Sound.ITEM_SHIELD_BLOCK, 1.0f, 1.0f)

                        if (rawDamager is AbstractArrow) {
                            rawDamager.velocity = rawDamager.velocity.multiply(-0.2)
                        }
                    }
                }
            }
        }
    }

    private fun isFrontalHit(victim: LivingEntity, attacker: Entity): Boolean {
        val yawRad = Math.toRadians(victim.yaw.toDouble())
        val lookX = -sin(yawRad)
        val lookZ = cos(yawRad)
        val toAttackerX = attacker.x - victim.x
        val toAttackerZ = attacker.z - victim.z

        return (lookX * toAttackerX + lookZ * toAttackerZ) >= 0.0
    }

    private fun isAxeAttack(damager: Entity): Boolean {
        if (damager is LivingEntity) {
            val weapon = damager.equipment?.itemInMainHand ?: return false
            return AXE_MATERIALS.contains(weapon.type)
        }
        return false
    }

    // 3. Unified EntityTargetEvent Handler (Factions, Golems -> Zombie Villagers, Axolotls -> Passives)
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onEntityTarget(event: EntityTargetEvent) {
        val target = event.target ?: return
        val entity = event.entity

        // A. Faction Target Immunity (Golems, Skeletons, Raiders/Witches)
        if (isSameMobFaction(entity, target, isPreventFriendlyFireEnabled)) {
            event.isCancelled = true
            return
        }

        // B. Iron Golems Ignore Zombie Villagers
        if (isGolemIgnoreZombieVillagersEnabled && entity is IronGolem && target is ZombieVillager) {
            event.isCancelled = true
            return
        }

        // C. Axolotls Ignore Passive Aquatic Mobs
        if (isAxolotlIgnorePassivesEnabled && entity is Axolotl) {
            if (target is Fish || target is Squid || target is GlowSquid || target is Tadpole) {
                event.isCancelled = true
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onFriendlyProjectileHit(event: ProjectileHitEvent) {
        val hitEntity = event.hitEntity ?: return
        val shooter = event.entity.shooter as? Entity ?: return

        if (isSameMobFaction(shooter, hitEntity, isPreventFriendlyFireEnabled)) {
            event.isCancelled = true
        }
    }

    // 4. Illusioner Spell Nerf (3s Blindness -> 10s Darkness)
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onIllusionerSpellBlindness(event: EntityPotionEffectEvent) {
        if (!isIllusionerDarknessEnabled) return
        val player = event.entity as? Player ?: return

        if (event.modifiedType == PotionEffectType.BLINDNESS) {
            if (event.action == EntityPotionEffectEvent.Action.ADDED || event.action == EntityPotionEffectEvent.Action.CHANGED) {
                val newEffect = event.newEffect
                if (newEffect != null && newEffect.duration >= 380 && event.cause == EntityPotionEffectEvent.Cause.ATTACK) {
                    val nearbyIllusioner = player.getNearbyEntities(32.0, 32.0, 32.0).any { it is Illusioner }

                    if (nearbyIllusioner) {
                        event.isCancelled = true
                        player.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 60, 0, false, true, true))

                        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                            if (player.isOnline && !player.isDead) {
                                player.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 200, 0, false, true, true))
                            }
                        }, 60L)
                    }
                }
            }
        }
    }

    // 5. Environmental Mob XP Drops
    @EventHandler(priority = EventPriority.HIGHEST)
    fun onMobDeathXp(event: EntityDeathEvent) {
        if (!isXpDropsEnabled) return
        val entity = event.entity
        if (entity is Player || event.droppedExp > 0) return

        val damageEvent = entity.lastDamageCause ?: return
        val cause = damageEvent.cause

        val shouldDropXp = when (cause) {
            DamageCause.WITHER, DamageCause.HOT_FLOOR, DamageCause.CRAMMING,
            DamageCause.LAVA, DamageCause.FIRE_TICK, DamageCause.FALL -> true
            DamageCause.ENTITY_ATTACK, DamageCause.ENTITY_SWEEP_ATTACK -> {
                (damageEvent as? EntityDamageByEntityEvent)?.damager is IronGolem
            }
            DamageCause.CONTACT -> isPointedDripstone(damageEvent) || isStonecutter(damageEvent)
            DamageCause.FALLING_BLOCK -> isPointedDripstone(damageEvent)
            else -> false
        }

        if (shouldDropXp) {
            try {
                val nmsEntity = (entity as CraftLivingEntity).handle
                val nmsServerLevel = (entity.world as CraftWorld).handle
                event.droppedExp = nmsEntity.getExperienceReward(nmsServerLevel, null)
            } catch (ignored: Throwable) {
                event.droppedExp = when (entity) {
                    is AbstractCubeMob -> entity.size
                    else -> 5
                }
            }
        }
    }

    private fun isPointedDripstone(event: EntityDamageEvent): Boolean {
        return if (event is EntityDamageByBlockEvent) {
            event.damager?.type == Material.POINTED_DRIPSTONE
        } else if (event is EntityDamageByEntityEvent) {
            val damager = event.damager
            damager is FallingBlock && damager.blockData.material == Material.POINTED_DRIPSTONE
        } else false
    }

    private fun isStonecutter(event: EntityDamageEvent): Boolean {
        return event is EntityDamageByBlockEvent && event.damager?.type == Material.STONECUTTER
    }

    // 6. Daylight Burning & Overworld Zombification Prevention
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onDaylightCombust(event: EntityCombustEvent) {
        if (!isNoDaylightBurningEnabled) return
        val entity = event.entity
        if (event !is EntityCombustByBlockEvent && event !is EntityCombustByEntityEvent) {
            if (entity is Zombie || entity is AbstractSkeleton || entity is Phantom) {
                event.isCancelled = true
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onZombification(event: EntityTransformEvent) {
        if (!isNoOverworldZombificationEnabled) return
        if (event.transformReason == EntityTransformEvent.TransformReason.PIGLIN_ZOMBIFIED) {
            event.isCancelled = true
        }
    }

    // 7. Unified Farmland & Turtle Egg Protection
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onPlayerPhysicalInteract(event: PlayerInteractEvent) {
        if (event.action != Action.PHYSICAL) return
        val type = event.clickedBlock?.type ?: return

        if (isPreventCropTrampleEnabled && type == Material.FARMLAND) {
            event.isCancelled = true
        } else if (isProtectTurtleEggsEnabled && type == Material.TURTLE_EGG) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onEntityBlockInteract(event: EntityInteractEvent) {
        val type = event.block.type
        if (isPreventCropTrampleEnabled && type == Material.FARMLAND) {
            event.isCancelled = true
        } else if (isProtectTurtleEggsEnabled && type == Material.TURTLE_EGG) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onEntityChangeBlock(event: EntityChangeBlockEvent) {
        val type = event.block.type
        if (isPreventCropTrampleEnabled && type == Material.FARMLAND && event.to == Material.DIRT) {
            event.isCancelled = true
        } else if (isProtectTurtleEggsEnabled && type == Material.TURTLE_EGG) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onEggBreak(event: BlockBreakEvent) {
        if (!isProtectTurtleEggsEnabled) return
        if (event.player.gameMode == GameMode.CREATIVE) return
        if (event.block.type == Material.TURTLE_EGG) {
            event.isCancelled = true
        }
    }

    // 8. Axolotl Breeding with Raw Tropical Fish
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onAxolotlBreedWithFishItem(event: PlayerInteractEntityEvent) {
        if (!isAxolotlBreedEnabled) return
        val axolotl = event.rightClicked as? Axolotl ?: return
        if (!axolotl.isAdult || !axolotl.canBreed() || axolotl.isLoveMode) return

        val player = event.player
        val hand = event.hand
        val item = player.inventory.getItem(hand)

        if (item.type == Material.TROPICAL_FISH) {
            event.isCancelled = true

            axolotl.setLoveModeTicks(600)
            axolotl.world.spawnParticle(Particle.HEART, axolotl.location.add(0.0, 0.5, 0.0), 5, 0.2, 0.2, 0.2, 0.0)
            axolotl.world.playSound(axolotl.location, Sound.ENTITY_GENERIC_EAT, 1.0f, 1.0f)

            if (player.gameMode != GameMode.CREATIVE) {
                if (item.amount <= 1) player.inventory.setItem(hand, null) else item.amount -= 1
            }

            val adv = Bukkit.getAdvancement(NamespacedKey.minecraft("husbandry/breed_an_animal"))
            if (adv != null) {
                val progress = player.getAdvancementProgress(adv)
                if (!progress.isDone) {
                    progress.remainingCriteria.forEach { progress.awardCriteria(it) }
                }
            }
        }
    }

    // 9. Fast-Filtered Cleanup (Only touches map if map is non-empty and entity is a skeleton type)
    @EventHandler
    fun onEntityRemove(event: EntityRemoveFromWorldEvent) {
        if (skeletonShieldCooldowns.isNotEmpty() && SKELETON_SHIELD_TYPES.contains(event.entityType)) {
            skeletonShieldCooldowns.remove(event.entity.uniqueId)
        }
    }
}