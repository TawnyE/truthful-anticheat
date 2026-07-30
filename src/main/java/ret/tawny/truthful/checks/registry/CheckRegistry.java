package ret.tawny.truthful.checks.registry;

import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.checks.api.Check;
import ret.tawny.truthful.checks.impl.bedrock.BFlyA;
import ret.tawny.truthful.checks.impl.bedrock.BReachA;
import ret.tawny.truthful.checks.impl.bedrock.BSpeedA;
import ret.tawny.truthful.checks.impl.combat.aim.*;
import ret.tawny.truthful.checks.impl.combat.anchor.AnchorAuraA;
import ret.tawny.truthful.checks.impl.combat.autoclicker.*;
import ret.tawny.truthful.checks.impl.combat.crystal.CrystalAuraA;
import ret.tawny.truthful.checks.impl.combat.hitbox.HitboxA;
import ret.tawny.truthful.checks.impl.combat.killaura.*;
import ret.tawny.truthful.checks.impl.combat.lag.LagA;
import ret.tawny.truthful.checks.impl.combat.lag.LagB;
import ret.tawny.truthful.checks.impl.combat.lag.LagC;
import ret.tawny.truthful.checks.impl.combat.reach.ReachA;
import ret.tawny.truthful.checks.impl.movement.baritone.BaritoneA;
import ret.tawny.truthful.checks.impl.movement.baritone.BaritoneB;
import ret.tawny.truthful.checks.impl.movement.baritone.BaritoneC;
import ret.tawny.truthful.checks.impl.movement.inventory.InventoryA;
import ret.tawny.truthful.checks.impl.movement.simulation.*;
import ret.tawny.truthful.checks.impl.movement.spoof.*;
import ret.tawny.truthful.checks.impl.movement.velocity.*;
import ret.tawny.truthful.checks.impl.packet.badpacket.*;
import ret.tawny.truthful.checks.impl.packet.crasher.CrasherA;
import ret.tawny.truthful.checks.impl.packet.invalid.InvalidA;
import ret.tawny.truthful.checks.impl.packet.order.*;
import ret.tawny.truthful.checks.impl.packet.sprint.SprintA;
import ret.tawny.truthful.checks.impl.packet.sprint.SprintB;
import ret.tawny.truthful.checks.impl.packet.timer.TimerA;
import ret.tawny.truthful.checks.impl.raycast.RaycastA;
import ret.tawny.truthful.checks.impl.world.fastbreak.FastBreakA;
import ret.tawny.truthful.checks.impl.world.phase.PhaseA;
import ret.tawny.truthful.checks.impl.world.scaffold.*;
import ret.tawny.truthful.utils.reflection.Manager;

import java.util.ArrayList;
import java.util.List;

public final class CheckRegistry extends Manager<Class<? extends Check>, Check> {

    private final List<Check> movementChecks = new ArrayList<>();
    private final List<Check> packetChecks = new ArrayList<>();
    private final List<Check> sendPacketChecks = new ArrayList<>();
    private final List<Check> attackChecks = new ArrayList<>();
    private final List<Check> blockBreakChecks = new ArrayList<>();
    private final List<Check> vehicleMoveChecks = new ArrayList<>();
    private final List<Check> quitChecks = new ArrayList<>();

    public CheckRegistry() {
        // Aim Checks
        register(AimA.class, new AimA());
        register(AimB.class, new AimB());
        register(AimD.class, new AimD());
        register(AimE.class, new AimE());
        register(AimF.class, new AimF());
        register(AimG.class, new AimG());
        register(AimH.class, new AimH());
        register(AimI.class, new AimI());
        register(AimJ.class, new AimJ());
        register(AimK.class, new AimK());
        register(AimL.class, new AimL());
        register(AimX.class, new AimX());

        // AutoClicker Checks
        register(AutoClickerA.class, new AutoClickerA());
        register(AutoClickerB.class, new AutoClickerB());
        register(AutoClickerC.class, new AutoClickerC());
        register(AutoClickerD.class, new AutoClickerD());
        register(AutoClickerE.class, new AutoClickerE());

        // Lag & Combat
        register(LagA.class, new LagA());
        register(LagB.class, new LagB());
        register(LagC.class, new LagC());
        register(HitboxA.class, new HitboxA());
        register(ReachA.class, new ReachA());

        // KillAura Checks
        register(KillAuraB.class, new KillAuraB());
        register(KillAuraC.class, new KillAuraC());
        register(KillAuraD.class, new KillAuraD());
        register(KillAuraE.class, new KillAuraE());
        register(KillAuraF.class, new KillAuraF());
        register(KillAuraG.class, new KillAuraG());
        register(KillAuraH.class, new KillAuraH());

        // Baritone Checks
        register(BaritoneA.class, new BaritoneA());
        register(BaritoneB.class, new BaritoneB());
        register(BaritoneC.class, new BaritoneC());

        // Simulation Checks
        register(SimulationA.class, new SimulationA());
        register(SimulationB.class, new SimulationB());
        register(SimulationC.class, new SimulationC());
        register(SimulationD.class, new SimulationD());
        register(SimulationE.class, new SimulationE());
        register(SimulationF.class, new SimulationF());

        // Velocity Checks
        register(VelocityA.class, new VelocityA());
        register(VelocityB.class, new VelocityB());
        register(VelocityC.class, new VelocityC());
        register(VelocityD.class, new VelocityD());

        // Inventory
        register(InventoryA.class, new InventoryA());

        // Ground Spoof Checks
        register(GroundSpoofB.class, new GroundSpoofB());
        register(GroundSpoofC.class, new GroundSpoofC());
        register(GroundSpoofD.class, new GroundSpoofD());
        register(GroundSpoofE.class, new GroundSpoofE());
        register(GroundSpoofF.class, new GroundSpoofF());
        register(GroundSpoofG.class, new GroundSpoofG());

        // Timer Check
        register(TimerA.class, new TimerA());

        // World & FastBreak
        register(FastBreakA.class, new FastBreakA());
        register(PhaseA.class, new PhaseA());

        // Scaffold Checks
        register(ScaffoldA.class, new ScaffoldA());
        register(ScaffoldB.class, new ScaffoldB());
        register(ScaffoldC.class, new ScaffoldC());
        register(ScaffoldD.class, new ScaffoldD());
        register(ScaffoldE.class, new ScaffoldE());
        register(ScaffoldF.class, new ScaffoldF());
        register(ScaffoldG.class, new ScaffoldG());
        register(ScaffoldH.class, new ScaffoldH());

        // Raycast
        register(RaycastA.class, new RaycastA());

        // Packet Integrity
        register(BadPacketA.class, new BadPacketA());
        register(CrasherA.class, new CrasherA());
        register(InvalidA.class, new InvalidA());

        // Packet Order
        register(PacketOrderA.class, new PacketOrderA());
        register(PacketOrderB.class, new PacketOrderB());
        register(PacketOrderC.class, new PacketOrderC());
        register(PacketOrderD.class, new PacketOrderD());
        register(PacketOrderE.class, new PacketOrderE());

        // Sprint & BadPacket Continued
        register(SprintA.class, new SprintA());
        register(SprintB.class, new SprintB());
        register(BadPacketC.class, new BadPacketC());
        register(BadPacketD.class, new BadPacketD());
        register(BadPacketE.class, new BadPacketE());
        register(BadPacketG.class, new BadPacketG());
        register(BadPacketH.class, new BadPacketH());
        register(BadPacketI.class, new BadPacketI());
        register(BadPacketJ.class, new BadPacketJ());
        register(BadPacketK.class, new BadPacketK());

        // Aura
        register(CrystalAuraA.class, new CrystalAuraA());
        register(AnchorAuraA.class, new AnchorAuraA());

        // Bedrock Platform
        register(BSpeedA.class, new BSpeedA());
        register(BFlyA.class, new BFlyA());
        register(BReachA.class, new BReachA());

        categorize();
        Truthful.getInstance().getConfiguration().cleanupOrphanedChecks(this.getCollection());
    }

    private void categorize() {
        movementChecks.clear();
        packetChecks.clear();
        sendPacketChecks.clear();
        attackChecks.clear();
        blockBreakChecks.clear();
        vehicleMoveChecks.clear();
        quitChecks.clear();

        for (Check check : getCollection()) {
            Class<?> clazz = check.getClass();
            try {
                if (clazz.getMethod("handleRelMove", ret.tawny.truthful.wrapper.impl.client.position.RelMovePacketWrapper.class).getDeclaringClass() != Check.class) {
                    movementChecks.add(check);
                }
                if (clazz.getMethod("handlePacketPlayerReceive", com.github.retrooper.packetevents.event.PacketReceiveEvent.class).getDeclaringClass() != Check.class) {
                    packetChecks.add(check);
                }
                if (clazz.getMethod("handlePacketPlaySend", com.github.retrooper.packetevents.event.PacketSendEvent.class).getDeclaringClass() != Check.class) {
                    sendPacketChecks.add(check);
                }
                if (clazz.getMethod("onAttack", org.bukkit.event.entity.EntityDamageByEntityEvent.class).getDeclaringClass() != Check.class) {
                    attackChecks.add(check);
                }
                if (clazz.getMethod("onBlockBreak", org.bukkit.event.block.BlockBreakEvent.class).getDeclaringClass() != Check.class) {
                    blockBreakChecks.add(check);
                }
                if (clazz.getMethod("onVehicleMove", org.bukkit.event.vehicle.VehicleMoveEvent.class).getDeclaringClass() != Check.class) {
                    vehicleMoveChecks.add(check);
                }
                if (clazz.getMethod("onQuit", org.bukkit.event.player.PlayerQuitEvent.class).getDeclaringClass() != Check.class) {
                    quitChecks.add(check);
                }
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            }
        }
    }

    public void init() {
        this.getCollection().forEach(Check::init);
        long enabledChecks = this.getCollection().stream().filter(Check::isEnabled).count();
        Truthful.getInstance().getPlugin().getLogger().info("Successfully loaded and registered " + enabledChecks + " checks.");
    }

    public List<Check> getMovementChecks() { return movementChecks; }
    public List<Check> getPacketChecks() { return packetChecks; }
    public List<Check> getSendPacketChecks() { return sendPacketChecks; }
    public List<Check> getAttackChecks() { return attackChecks; }
    public List<Check> getBlockBreakChecks() { return blockBreakChecks; }
    public List<Check> getVehicleMoveChecks() { return vehicleMoveChecks; }
    public List<Check> getQuitChecks() { return quitChecks; }

    public void resetAllViolations() {
        this.getCollection().forEach(Check::clearViolations);
    }
}