package project.kompass.tweakskompass.entity

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.entity.*
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityTargetEvent
import org.bukkit.event.entity.EntityTargetLivingEntityEvent
import project.kompass.tweakskompass.TweaksKompass
import java.util.EnumSet
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class ThreatTableListener(private val plugin: TweaksKompass) : Listener {

    private class ThreatTable {
        val entries = ConcurrentHashMap<UUID, Double>()
        var lastCombatTime = System.currentTimeMillis()

        fun addThreat(attackerId: UUID, amount: Double) {
            entries.merge(attackerId, amount) { old, added -> old + added }
            lastCombatTime = System.currentTimeMillis()
        }

        fun isInCombat(timeoutMs: Long): Boolean {
            if (entries.isEmpty()) return false
            if (System.currentTimeMillis() - lastCombatTime > timeoutMs) {
                entries.clear()
                return false
            }
            return true
        }

        fun getTopThreatTarget(mob: Mob, maxRangeSq: Double): LivingEntity? {
            val iterator = entries.entries.iterator()
            var topTarget: LivingEntity? = null
            var highestThreat = -1.0
            val mx = mob.x
            val my = mob.y
            val mz = mob.z
            val mobWorld = mob.world

            while (iterator.hasNext()) {
                val (targetId, threat) = iterator.next()
                val entity = Bukkit.getEntity(targetId) as? LivingEntity

                if (entity == null || !entity.isValid || entity.isDead || entity.world != mobWorld) {
                    iterator.remove()
                    continue
                }
                if (entity is Player && (entity.gameMode == GameMode.CREATIVE || entity.gameMode == GameMode.SPECTATOR)) {
                    iterator.remove()
                    continue
                }

                val dx = mx - entity.x
                val dy = my - entity.y
                val dz = mz - entity.z
                if (dx * dx + dy * dy + dz * dz > maxRangeSq) continue

                if (threat > highestThreat) {
                    highestThreat = threat
                    topTarget = entity
                }
            }
            return topTarget
        }
    }

    private val threatTables = ConcurrentHashMap<UUID, ThreatTable>()
    private val blacklistedMobs: EnumSet<EntityType> = EnumSet.noneOf(EntityType::class.java)

    private var isEnabled = true
    private var combatTimeoutMs = 15_000L
    private var maxThreatRangeSq = 40.0 * 40.0
    private var switchThresholdRatio = 1.10

    init {
        reloadConfig()
    }

    fun reloadConfig() {
        val cfg = plugin.config
        isEnabled = cfg.getBoolean("threat-tables.enabled", true)
        combatTimeoutMs = cfg.getLong("threat-tables.combat-timeout-seconds", 15L) * 1000L
        val range = cfg.getDouble("threat-tables.max-range", 40.0)
        maxThreatRangeSq = range * range
        switchThresholdRatio = cfg.getDouble("threat-tables.aggro-switch-ratio", 1.10).coerceAtLeast(1.0)

        blacklistedMobs.clear()
        for (raw in cfg.getStringList("threat-tables.blacklisted-mobs")) {
            val name = raw.trim().uppercase()
            if (name.isEmpty() || name == "NONE") continue
            runCatching { EntityType.valueOf(name) }.getOrNull()?.let { blacklistedMobs.add(it) }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onMobDamaged(event: EntityDamageByEntityEvent) {
        if (!isEnabled) return
        val mob = event.entity as? Mob ?: return
        if (blacklistedMobs.contains(mob.type)) return
        if (mob is Tameable && mob.isTamed) return

        val attacker = when (val damager = event.damager) {
            is LivingEntity -> damager
            is Projectile -> damager.shooter as? LivingEntity
            else -> null
        } ?: return

        if (attacker.uniqueId == mob.uniqueId) return
        if (attacker is Player && (attacker.gameMode == GameMode.CREATIVE || attacker.gameMode == GameMode.SPECTATOR)) return

        // Never accumulate threat between members of the same built-in faction (e.g. Golems)
        if (EntityTweaksListener.isSameMobFaction(mob, attacker)) return

        val table = threatTables.computeIfAbsent(mob.uniqueId) { ThreatTable() }
        val threatAdded = event.finalDamage.coerceAtLeast(1.0)
        table.addThreat(attacker.uniqueId, threatAdded)

        val currentTarget = mob.target
        if (currentTarget == null || !currentTarget.isValid || currentTarget.isDead) {
            mob.target = attacker
            return
        }

        if (currentTarget.uniqueId != attacker.uniqueId) {
            val currentThreat = table.entries[currentTarget.uniqueId] ?: 0.0
            val attackerThreat = table.entries[attacker.uniqueId] ?: 0.0

            if (attackerThreat >= currentThreat * switchThresholdRatio) {
                mob.target = attacker
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onMobTargetLiving(event: EntityTargetLivingEntityEvent) {
        if (!isEnabled) return
        val entity = event.entity
        if (blacklistedMobs.contains(entity.type)) return
        val proposedTarget = event.target ?: return

        if (entity is Tameable && entity.isTamed && entity.ownerUniqueId == proposedTarget.uniqueId) {
            event.isCancelled = true
            return
        }

        // O(1) Built-In Faction Check (Golems, Skeletons, Raiders)
        if (EntityTweaksListener.isSameMobFaction(entity, proposedTarget)) {
            event.isCancelled = true
            return
        }

        val scoreboard = Bukkit.getScoreboardManager().mainScoreboard
        if (scoreboard.teams.isNotEmpty()) {
            val entityTeam = scoreboard.getEntityTeam(entity)
            if (entityTeam != null && !entityTeam.allowFriendlyFire()) {
                val targetTeam = scoreboard.getEntityTeam(proposedTarget)
                if (targetTeam != null && entityTeam.name == targetTeam.name) {
                    event.isCancelled = true
                    return
                }
            }
        }

        val mob = entity as? Mob ?: return
        val table = threatTables[mob.uniqueId] ?: return
        if (!table.isInCombat(combatTimeoutMs)) return

        table.entries.putIfAbsent(proposedTarget.uniqueId, 1.0)

        val topHolder = table.getTopThreatTarget(mob, maxThreatRangeSq) ?: return
        if (topHolder.uniqueId != proposedTarget.uniqueId) {
            val proposedThreat = table.entries[proposedTarget.uniqueId] ?: 0.0
            val topThreat = table.entries[topHolder.uniqueId] ?: 0.0

            if (topThreat > proposedThreat) {
                event.target = topHolder
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onMobTargetGeneric(event: EntityTargetEvent) {
        if (!isEnabled || event is EntityTargetLivingEntityEvent) return
        val mob = event.entity as? Mob ?: return
        if (blacklistedMobs.contains(mob.type)) return
        val proposedTarget = event.target as? LivingEntity ?: return

        if (EntityTweaksListener.isSameMobFaction(mob, proposedTarget)) {
            event.isCancelled = true
            return
        }

        val table = threatTables[mob.uniqueId] ?: return
        if (!table.isInCombat(combatTimeoutMs)) return

        val topHolder = table.getTopThreatTarget(mob, maxThreatRangeSq) ?: return
        if (topHolder.uniqueId != proposedTarget.uniqueId) {
            event.target = topHolder
        }
    }

    @EventHandler
    fun onEntityRemove(event: EntityRemoveFromWorldEvent) {
        val entity = event.entity
        if (entity !is LivingEntity) return

        val removedId = entity.uniqueId
        threatTables.remove(removedId)

        if (entity is Player && threatTables.isNotEmpty()) {
            for (table in threatTables.values) {
                table.entries.remove(removedId)
            }
        }
    }
}