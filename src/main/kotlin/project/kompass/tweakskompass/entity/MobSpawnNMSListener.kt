package project.kompass.tweakskompass.entity

import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal
import net.minecraft.world.entity.ai.goal.FleeSunGoal
import net.minecraft.world.entity.ai.goal.FloatGoal
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal
import net.minecraft.world.entity.ai.goal.RangedAttackGoal
import net.minecraft.world.entity.ai.goal.RestrictSunGoal
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.animal.golem.IronGolem as NmsIronGolem
import net.minecraft.world.entity.monster.Creeper as NmsCreeper
import net.minecraft.world.entity.monster.Giant as NmsGiant
import net.minecraft.world.entity.monster.Witch as NmsWitch
import net.minecraft.world.entity.monster.piglin.Piglin as NmsPiglin
import net.minecraft.world.entity.monster.piglin.PiglinBrute as NmsPiglinBrute
import net.minecraft.world.entity.monster.warden.Warden
import net.minecraft.world.entity.monster.zombie.Zombie as NmsZombie
import net.minecraft.world.entity.player.Player as NmsPlayer
import net.minecraft.world.entity.raid.Raider as NmsRaider
import org.bukkit.craftbukkit.entity.*
import org.bukkit.entity.*
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntitySpawnEvent
import project.kompass.tweakskompass.TweaksKompass

class MobSpawnNMSListener(private val plugin: TweaksKompass) : Listener {

    @EventHandler
    fun onMobSpawn(event: EntitySpawnEvent) {
        if (!plugin.config.getBoolean("entity-tweaks.custom-mob-ai", true)) return
        val entity = event.entity

        // 1. ALL RAIDERS (Illagers)
        if (entity is Raider) {
            val nms = (entity as CraftRaider).handle

            // Fear goals: Avoid Giants and Wardens
            nms.goalSelector.addGoal(1, AvoidEntityGoal(nms, NmsGiant::class.java, 24.0f, 1.0, 1.2))
            nms.goalSelector.addGoal(1, AvoidEntityGoal(nms, Warden::class.java, 16.0f, 1.0, 1.2))

            // Attack goals: Target Zombies and Piglins
            nms.targetSelector.addGoal(2, NearestAttackableTargetGoal(nms, NmsZombie::class.java, true))
            nms.targetSelector.addGoal(2, NearestAttackableTargetGoal(nms, NmsPiglin::class.java, true))
            nms.targetSelector.addGoal(2, NearestAttackableTargetGoal(nms, NmsPiglinBrute::class.java, true))
        }

        // 2. WITCHES
        else if (entity is Witch) {
            val nms = (entity as CraftWitch).handle
            nms.goalSelector.removeAllGoals { true }
            nms.targetSelector.removeAllGoals { true }

            nms.goalSelector.addGoal(0, FloatGoal(nms))
            nms.goalSelector.addGoal(2, RangedAttackGoal(nms, 1.0, 15, 12.0f))
            nms.goalSelector.addGoal(3, WaterAvoidingRandomStrollGoal(nms, 1.0))

            // Retain Player aggression & retaliation while adding undead/piglin targets
            nms.targetSelector.addGoal(1, HurtByTargetGoal(nms))
            nms.targetSelector.addGoal(2, NearestAttackableTargetGoal(nms, NmsPlayer::class.java, true))
            nms.targetSelector.addGoal(3, NearestAttackableTargetGoal(nms, NmsZombie::class.java, true))
            nms.targetSelector.addGoal(4, NearestAttackableTargetGoal(nms, NmsPiglin::class.java, true))
        }

        // 3. ZOMBIES
        else if (entity is Zombie) {
            val nms = (entity as CraftZombie).handle
            nms.targetSelector.addGoal(2, NearestAttackableTargetGoal(nms, NmsRaider::class.java, true))
            nms.targetSelector.addGoal(3, NearestAttackableTargetGoal(nms, NmsWitch::class.java, true))
        }

        // 4. SKELETONS, STRAYS & BOGGED (Sun avoidance purge)
        else if (entity is AbstractSkeleton) {
            val nms = (entity as CraftAbstractSkeleton).handle
            nms.goalSelector.removeAllGoals { it is FleeSunGoal || it is RestrictSunGoal }
        }

        // 5. GIANTS (Functional AI restored)
        else if (entity is Giant) {
            val nms = (entity as CraftGiant).handle
            nms.goalSelector.addGoal(0, MeleeAttackGoal(nms, 1.0, true))
            nms.targetSelector.addGoal(1, NearestAttackableTargetGoal(nms, NmsPlayer::class.java, true))
            nms.targetSelector.addGoal(2, NearestAttackableTargetGoal(nms, NmsRaider::class.java, true))
            nms.targetSelector.addGoal(3, NearestAttackableTargetGoal(nms, NmsWitch::class.java, true))
            nms.targetSelector.addGoal(4, NearestAttackableTargetGoal(nms, NmsPiglin::class.java, true))

            nms.getAttribute(Attributes.FOLLOW_RANGE)?.baseValue = 48.0
        }

        // 6. PIGLINS
        else if (entity is Piglin) {
            entity.isImmuneToZombification = true
            val nms = (entity as CraftPiglin).handle
            nms.brain.eraseMemory(MemoryModuleType.ATTACK_TARGET)
        }

        // 7. PIGLIN BRUTES
        else if (entity is PiglinBrute) {
            entity.isImmuneToZombification = true
        }

        // 8. HOGLINS
        else if (entity is Hoglin) {
            entity.isImmuneToZombification = true
        }

        // 9. IRON GOLEMS (Clear vanilla targets so GolemAttackTask controls targeting without conflict)
        else if (entity is IronGolem) {
            val nms = (entity as CraftIronGolem).handle
            nms.targetSelector.removeAllGoals { it is NearestAttackableTargetGoal<*> }
        }

        // 10. CREEPERS (Flee from Iron Golems)
        else if (entity is Creeper) {
            val nms = (entity as CraftCreeper).handle
            nms.goalSelector.addGoal(1, AvoidEntityGoal(nms, NmsIronGolem::class.java, 16.0f, 1.0, 1.2))
        }
    }
}