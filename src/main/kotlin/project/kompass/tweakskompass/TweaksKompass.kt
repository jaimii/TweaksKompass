package project.kompass.tweakskompass

import com.github.retrooper.packetevents.PacketEvents
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.util.Vector
import project.kompass.tweakskompass.anvil.AnvilNoTooExpensiveListener
import project.kompass.tweakskompass.anvil.AnvilPacketListener
import project.kompass.tweakskompass.arrow.ArrowPacketListener
import project.kompass.tweakskompass.arrow.ArrowTrackingManager
import project.kompass.tweakskompass.crafting.RecipeManager
import project.kompass.tweakskompass.entity.*
import project.kompass.tweakskompass.food.CustomFoodListener
import project.kompass.tweakskompass.food.CustomFoodManager
import project.kompass.tweakskompass.invisibility.InvisibilityListener
import project.kompass.tweakskompass.invisibility.InvisibilityManager
import project.kompass.tweakskompass.invisibility.InvisibilityPacketListener
import project.kompass.tweakskompass.leash.*
import project.kompass.tweakskompass.listener.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class TweaksKompass : JavaPlugin() {

    // Elytra Handlers
    private var speedLimiter: ElytraSpeedLimiterListener? = null
    private var speedometer: ElytraSpeedometerListener? = null
    private var boostJumpHandler: ElytraBoostJumpListener? = null
    private var bombingHandler: ElytraBombingListener? = null

    // Chainmail Sieve
    private var sieveListener: ChainmailSieveListener? = null

    // Fast Leaf Decay
    private var leafDecayListener: FastLeafDecayListener? = null

    // Void Teleport
    private var voidTeleportListener: VoidTeleportListener? = null

    // Invisibility System
    lateinit var invisibilityManager: InvisibilityManager
        private set

    // Food System
    lateinit var customFoodManager: CustomFoodManager
        private set

    // Arrow System
    lateinit var arrowTrackingManager: ArrowTrackingManager
        private set
    val customArrowVelocities = ConcurrentHashMap<UUID, Vector>()
    val customArrowVelocitiesByEntityId = ConcurrentHashMap<Int, Vector>()
    val cachedArrowMultipliers = HashMap<String, Double>()

    var globalArrowMultiplier: Double = 1.0
        private set
    var isSkeletonPassthroughEnabled: Boolean = true
        private set
    var isIllagerPassthroughEnabled: Boolean = true
        private set

    // Leash System
    lateinit var leashRegistry: LeashRegistry
        private set
    lateinit var leashManager: LeashManager
        private set

    override fun onLoad() {
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this))
        PacketEvents.getAPI().settings.reEncodeByDefault(false)
        PacketEvents.getAPI().load()
    }

    override fun onEnable() {
        saveDefaultConfig()
        loadCachedArrowMultipliers()

        PacketEvents.getAPI().init()

        val pm = server.pluginManager

        // 1. Shield Combat
        if (config.getBoolean("shield-settings.enabled", true)) {
            ShieldListener.patchPaperGlobal(this)
            pm.registerEvents(ShieldListener(this), this)
        }

        // 2. Custom Food System
        if (config.getBoolean("custom-foods.enabled", true)) {
            customFoodManager = CustomFoodManager(this)
            pm.registerEvents(CustomFoodListener(this, customFoodManager), this)
        }

        // 3. Elytra Mechanics
        if (config.getBoolean("better-elytras.enabled", true)) {
            speedLimiter = ElytraSpeedLimiterListener(this).also { pm.registerEvents(it, this) }
            speedometer = ElytraSpeedometerListener(this).also { pm.registerEvents(it, this) }
            boostJumpHandler = ElytraBoostJumpListener(this).also { pm.registerEvents(it, this) }
            bombingHandler = ElytraBombingListener(this).also { pm.registerEvents(it, this) }
        }

        // 4. Arrow System
        if (config.getBoolean("arrow-settings.enabled", true)) {
            arrowTrackingManager = ArrowTrackingManager(this).apply { start() }
            ArrowPacketListener.register(this)
            pm.registerEvents(ArrowSpeedListener(this), this)
        }

        // 5. Anvil Overhaul
        if (config.getBoolean("anvil-settings.enabled", true)) {
            AnvilPacketListener.register()
            pm.registerEvents(AnvilNoTooExpensiveListener(this), this)
        }

        // 6. Lead Any Mob
        if (config.getBoolean("lead-any-mob.enabled", true)) {
            leashRegistry = LeashRegistry()
            leashManager = LeashManager(this)
            pm.registerEvents(LeashInteractListener(this, leashRegistry), this)
            pm.registerEvents(LeashSnapListener(this), this)
            pm.registerEvents(LeashTeleportListener(this), this)
            pm.registerEvents(LeashPortalListener(this), this)
            pm.registerEvents(LeashElytraBoostListener(this), this)
        }

        // 7. Trident Combat & Utility
        if (config.getBoolean("trident-settings.enabled", true)) {
            pm.registerEvents(TridentAttributeListener(), this)
            pm.registerEvents(TridentRiptideListener(this), this)
            pm.registerEvents(TridentChannelingListener(this), this)
            pm.registerEvents(TridentDamageListener(this), this)
            pm.registerEvents(TridentHandlingListener(this), this)
            pm.registerEvents(RangedLootingListener(), this)
            pm.registerEvents(TridentAnvilListener(), this)
        }

        // 8. Spear Combat
        if (config.getBoolean("spear-settings.enabled", true)) {
            pm.registerEvents(SpearListener(), this)
        }

        // 9. Armor Durability
        if (config.getBoolean("custom-armor-durability.enabled", true)) {
            pm.registerEvents(ArmorDurabilityListener(this), this)
        }

        // 10. Storage & Stacks Extensions
        if (config.getBoolean("food-and-storage.custom-stacks.enabled", true)) {
            pm.registerEvents(PotionSoupStackListener(this), this)
        }
        if (config.getBoolean("food-and-storage.bundle-capacity.enabled", true)) {
            pm.registerEvents(BundleCapacityListener(this), this)
        }
        if (config.getBoolean("food-and-storage.always-eat.enabled", true)) {
            pm.registerEvents(AlwaysEatListener(this), this)
        }

        // 11. Gameplay Enhancements
        if (config.getBoolean("enchantable-pet-armor.enabled", true)) {
            pm.registerEvents(EnchantablePetArmorListener(this), this)
        }
        if (config.getBoolean("wind-charge.enabled", true)) {
            pm.registerEvents(WindChargeListener(this), this)
        }
        if (config.getBoolean("turtle-brushing.enabled", true)) {
            pm.registerEvents(TurtleScuteBrushListener(this), this)
        }
        if (config.getBoolean("inventory-totem.enabled", true)) {
            pm.registerEvents(InventoryTotemListener(this), this)
        }
        if (config.getBoolean("better-ladders.enabled", true)) {
            pm.registerEvents(LadderListener(this), this)
        }
        if (config.getBoolean("better-boats.enabled", true)) {
            pm.registerEvents(BoatListener(this), this)
        }
        if (config.getBoolean("snow-physics.enabled", true)) {
            pm.registerEvents(FallingSnowListener(this), this)
        }
        if (config.getBoolean("villager-restock.enabled", true)) {
            pm.registerEvents(VillagerRestockListener(this), this)
        }
        if (config.getBoolean("copper-oxidation.enabled", true)) {
            pm.registerEvents(CopperOxidationListener(this), this)
        }

        // 12. Entity Tweaks & AI
        if (config.getBoolean("entity-tweaks.enabled", true)) {
            pm.registerEvents(EntityTweaksListener(this), this)
            if (config.getBoolean("entity-tweaks.custom-mob-ai", true)) {
                pm.registerEvents(MobSpawnNMSListener(this), this)
            }
            if (config.getBoolean("entity-tweaks.witch-heal-against-undead", true)) {
                pm.registerEvents(WitchAttackListener(this), this)
            }
            pm.registerEvents(SnowmanCreeperListener(this), this)

            if (config.getBoolean("entity-tweaks.golem-attack-system", true)) {
                GolemAttackTask(this).runTaskTimer(this, 20L, 10L)
            }
        }

        // 13. Armor Trims
        if (config.getBoolean("armor-trims.enabled", true)) {
            val trimListener = ArmorTrimListener(this).apply { startTask() }
            pm.registerEvents(trimListener, this)
        }

        // 14. Chainmail Sieve
        if (config.getBoolean("chainmail-sieve.enabled", true)) {
            sieveListener = ChainmailSieveListener(this).apply { startTask() }
            pm.registerEvents(sieveListener!!, this)
        }

        // 15. Pet Teleportation
        if (config.getBoolean("pet-teleportation.enabled", true)) {
            val petListener = PetTeleportListener(this).apply { startDistanceCheckTask() }
            pm.registerEvents(petListener, this)
        }

        // 16. Copper Armor Hazards
        if (config.getBoolean("copper-armor.enabled", true)) {
            val copperArmorListener = CopperArmorListener()
            pm.registerEvents(copperArmorListener, this)
            copperArmorListener.startArmorCheckTask(this)
            copperArmorListener.startParticleTask(this)
        }

        // 17. Magical Campfire
        if (config.getBoolean("magical-campfire.enabled", true)) {
            val campfireListener = MagicalCampfireListener(this)
            pm.registerEvents(campfireListener, this)
            campfireListener.startCampfireTask()
        }

        // 18. Fast Leaf Decay
        if (config.getBoolean("fast-leaf-decay.enabled", true)) {
            leafDecayListener = FastLeafDecayListener(this).also { pm.registerEvents(it, this) }
        }

        // 19. Shulker Dyeing
        if (config.getBoolean("shulker-colors.enabled", true)) {
            pm.registerEvents(ShulkerColorListener(this), this)
        }

        // 20. End Void Teleport
        if (config.getBoolean("void-teleport.enabled", true)) {
            voidTeleportListener = VoidTeleportListener(this).also { pm.registerEvents(it, this) }
        }

        // 21. Better Invisibility
        if (config.getBoolean("better-invisibility.enabled", true)) {
            invisibilityManager = InvisibilityManager(this)
            InvisibilityPacketListener(invisibilityManager).register()
            pm.registerEvents(InvisibilityListener(this, invisibilityManager), this)
        }

        // 22. Crafting, Smelting & Redyeing Recipes
        if (config.getBoolean("crafting-tweaks.enabled", true)) {
            RecipeManager.registerRecipes(this)
        }

        // 23. Painting Tweaks
        if (config.getBoolean("painting-tweaks.enabled", true)) {
            pm.registerEvents(PaintingTweaksListener(), this)
        }

        // 24. Shears Looting
        if (config.getBoolean("shears-looting.enabled", true)) {
            pm.registerEvents(LootingShearsListener(), this)
        }

        // 25. Tool & Mining Tweaks
        if (config.getBoolean("tool-block-tweaks.enabled", true)) {
            pm.registerEvents(ToolBlockTweaksListener(this), this)
        }

        logger.info("TweaksKompass v1.5 loaded.")
    }

    override fun onDisable() {
        if (::arrowTrackingManager.isInitialized) {
            arrowTrackingManager.stop()
        }
        if (::invisibilityManager.isInitialized) {
            invisibilityManager.clear()
        }
        leafDecayListener?.cleanup()
        customArrowVelocities.clear()
        customArrowVelocitiesByEntityId.clear()
        AnvilPacketListener.preparing.clear()
        PacketEvents.getAPI().terminate()
        logger.info("TweaksKompass disabled.")
    }

    fun loadCachedArrowMultipliers() {
        cachedArrowMultipliers.clear()
        isSkeletonPassthroughEnabled = config.getBoolean("arrow-settings.skeleton-passthrough", true)
        isIllagerPassthroughEnabled = config.getBoolean("arrow-settings.illager-passthrough", true)

        val section = config.getConfigurationSection("arrow-settings.multipliers")
        if (section != null) {
            for (key in section.getKeys(false)) {
                if (key.equals("GLOBAL", ignoreCase = true) || key.equals("DEFAULT", ignoreCase = true)) {
                    globalArrowMultiplier = section.getDouble(key, 1.0)
                } else {
                    cachedArrowMultipliers[key.uppercase()] = section.getDouble(key, 1.0)
                }
            }
        } else {
            globalArrowMultiplier = 1.0
        }
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (command.name.equals("tweakskompass", ignoreCase = true)) {
            if (args.isNotEmpty() && args[0].equals("reload", ignoreCase = true)) {
                if (!sender.isOp && !sender.hasPermission("tweakskompass.admin")) {
                    sender.sendRichMessage("<red>You must be a server operator to execute this command.</red>")
                    return true
                }
                reloadConfig()
                if (::customFoodManager.isInitialized) {
                    customFoodManager.reload()
                }
                loadCachedArrowMultipliers()
                speedLimiter?.updateValues()
                speedometer?.updateValues()
                boostJumpHandler?.updateValues()
                bombingHandler?.updateValues()
                sieveListener?.reloadConfig()
                leafDecayListener?.reloadConfig()
                voidTeleportListener?.reloadConfig()
                sender.sendRichMessage("<green>[TweaksKompass] Configuration, Foods, and Arrows reloaded successfully.</green>")
                return true
            }
        }
        return false
    }
}