package project.kompass.tweakskompass

import project.kompass.tweakskompass.arrow.ArrowPacketListener
import project.kompass.tweakskompass.arrow.ArrowTrackingManager
import project.kompass.tweakskompass.leash.*
import project.kompass.tweakskompass.listener.*
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.util.Vector
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class TweaksKompass : JavaPlugin() {

    private lateinit var speedLimiter: ElytraSpeedLimiterListener
    private lateinit var speedometer: ElytraSpeedometerListener
    private lateinit var boostJumpHandler: ElytraBoostJumpListener
    private lateinit var bombingHandler: ElytraBombingListener
    private lateinit var sieveListener: ChainmailSieveListener

    // Arrow
    lateinit var arrowTrackingManager: ArrowTrackingManager
        private set
    val customArrowVelocities = ConcurrentHashMap<UUID, Vector>()
    val cachedArrowMultipliers = HashMap<String, Double>()

    var globalArrowMultiplier: Double = 1.0
        private set
    var isSkeletonPassthroughEnabled: Boolean = true
        private set
    var isIllagerPassthroughEnabled: Boolean = true
        private set

    // Leash
    lateinit var leashRegistry: LeashRegistry
        private set
    lateinit var leashManager: LeashManager
        private set

    override fun onEnable() {
        saveDefaultConfig()
        loadCachedArrowMultipliers()

        ShieldListener.patchPaperGlobal(this)

        val pm = server.pluginManager

        // Inst Elytras
        speedLimiter = ElytraSpeedLimiterListener(this)
        speedometer = ElytraSpeedometerListener(this)
        boostJumpHandler = ElytraBoostJumpListener(this)
        bombingHandler = ElytraBombingListener(this)

        // Int Arrow
        arrowTrackingManager = ArrowTrackingManager(this).apply { start() }
        ArrowPacketListener.register(this)

        // Int Leash
        leashRegistry = LeashRegistry()
        leashManager = LeashManager(this)

        // Register Core Listeners
        pm.registerEvents(TridentAttributeListener(), this)
        pm.registerEvents(TridentRiptideListener(), this)
        pm.registerEvents(TridentChannelingListener(this), this)
        pm.registerEvents(TridentDamageListener(), this)
        pm.registerEvents(RangedLootingListener(), this)
        pm.registerEvents(TridentAnvilListener(), this)
        pm.registerEvents(SpearListener(), this)
        pm.registerEvents(ArmorDurabilityListener(this), this)
        pm.registerEvents(PotionSoupStackListener(this), this)
        pm.registerEvents(BundleCapacityListener(this), this)
        pm.registerEvents(AlwaysEatListener(this), this)
        pm.registerEvents(EnchantablePetArmorListener(), this)
        pm.registerEvents(WindChargeListener(this), this)
        pm.registerEvents(TurtleScuteBrushListener(this), this)
        pm.registerEvents(InventoryTotemListener(this), this)
        pm.registerEvents(LadderListener(this), this)
        pm.registerEvents(BoatListener(this), this)
        pm.registerEvents(ShieldListener(this), this)

        // Register Elytra listeners
        pm.registerEvents(speedLimiter, this)
        pm.registerEvents(speedometer, this)
        pm.registerEvents(boostJumpHandler, this)
        pm.registerEvents(bombingHandler, this)

        // Register Arrow listener
        pm.registerEvents(ArrowSpeedListener(this), this)

        // Register Leash listeners
        pm.registerEvents(LeashInteractListener(this, leashRegistry), this)
        pm.registerEvents(LeashSnapListener(this), this)
        pm.registerEvents(LeashTeleportListener(this), this)
        pm.registerEvents(LeashPortalListener(this), this)
        pm.registerEvents(LeashElytraBoostListener(this), this)

        // Register Chainmail Sieve listener
        sieveListener = ChainmailSieveListener(this).apply { startTask() }
        pm.registerEvents(sieveListener, this)

        // Background Tasks
        val copperArmorListener = CopperArmorListener()
        pm.registerEvents(copperArmorListener, this)
        copperArmorListener.startArmorCheckTask(this)
        copperArmorListener.startParticleTask(this)

        val campfireListener = MagicalCampfireListener(this)
        pm.registerEvents(campfireListener, this)
        campfireListener.startCampfireTask()

        logger.info("TweaksKompass v1.5 successfully initialized with modular configuration.")
    }

    override fun onDisable() {
        arrowTrackingManager.stop()
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
                if (!sender.isOp) {
                    sender.sendMessage("§cYou must be a server operator to execute this command.")
                    return true
                }
                reloadConfig()
                loadCachedArrowMultipliers()
                speedLimiter.updateValues()
                speedometer.updateValues()
                boostJumpHandler.updateValues()
                bombingHandler.updateValues()
                sieveListener.reloadConfig()
                sender.sendMessage("§a[TweaksKompass] Configuration reloaded successfully.")
                return true
            }
        }
        return false
    }
}