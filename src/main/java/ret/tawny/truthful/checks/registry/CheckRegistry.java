package ret.tawny.truthful.checks.registry;

import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.checks.api.Check;
import ret.tawny.truthful.checks.impl.combat.aim.*;
import ret.tawny.truthful.checks.impl.combat.autoclicker.*;
import ret.tawny.truthful.checks.impl.combat.hitbox.*;
import ret.tawny.truthful.checks.impl.combat.killaura.*;
import ret.tawny.truthful.checks.impl.combat.reach.*;
import ret.tawny.truthful.checks.impl.movement.baritone.*;
import ret.tawny.truthful.checks.impl.movement.inventory.*;
import ret.tawny.truthful.checks.impl.movement.spoof.*;
import ret.tawny.truthful.checks.impl.movement.timer.*;
import ret.tawny.truthful.checks.impl.movement.simulation.*;
import ret.tawny.truthful.checks.impl.movement.velocity.*;
import ret.tawny.truthful.checks.impl.packet.order.PacketOrderA;
import ret.tawny.truthful.checks.impl.packet.order.PacketOrderB;
import ret.tawny.truthful.checks.impl.packet.order.PacketOrderC;
import ret.tawny.truthful.checks.impl.packet.order.PacketOrderD;
import ret.tawny.truthful.checks.impl.packet.order.PacketOrderE;
import ret.tawny.truthful.checks.impl.raycast.*;
import ret.tawny.truthful.checks.impl.world.fastbreak.*;
import ret.tawny.truthful.checks.impl.world.phase.*;
import ret.tawny.truthful.checks.impl.world.scaffold.*;
import ret.tawny.truthful.checks.impl.bedrock.*;
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
        // Aim
        register(AimA.class, new AimA());
        register(AimB.class, new AimB());
        register(AimC.class, new AimC());
        register(AimD.class, new AimD());
        register(AimE.class, new AimE());
        register(AimF.class, new AimF());
        register(AimG.class, new AimG());
        register(AimH.class, new AimH());
        register(AimI.class, new AimI());
        register(AimJ.class, new AimJ());
        register(AimK.class, new AimK());
        register(AimL.class, new AimL());

        // AutoClicker
        register(AutoClickerA.class, new AutoClickerA());
        register(AutoClickerB.class, new AutoClickerB());
        register(AutoClickerC.class, new AutoClickerC());
        register(AutoClickerD.class, new AutoClickerD());
        register(AutoClickerE.class, new AutoClickerE());

        // Hitbox & Reach
        register(HitboxA.class, new HitboxA());
        register(ReachA.class, new ReachA());

        // KillAura
        register(KillAuraA.class, new KillAuraA());
        register(KillAuraB.class, new KillAuraB());
        register(KillAuraC.class, new KillAuraC());
        register(KillAuraD.class, new KillAuraD());
        register(KillAuraE.class, new KillAuraE());
        register(ret.tawny.truthful.checks.impl.combat.killaura.KillAuraF.class, new ret.tawny.truthful.checks.impl.combat.killaura.KillAuraF());
        register(ret.tawny.truthful.checks.impl.combat.killaura.KillAuraG.class, new ret.tawny.truthful.checks.impl.combat.killaura.KillAuraG());
        register(ret.tawny.truthful.checks.impl.combat.killaura.KillAuraH.class, new ret.tawny.truthful.checks.impl.combat.killaura.KillAuraH());

        // Baritone
        register(BaritoneA.class, new BaritoneA());
        register(BaritoneB.class, new BaritoneB());
        register(BaritoneC.class, new BaritoneC());

        // Simulation
        register(SimulationA.class, new SimulationA());
        register(SimulationB.class, new SimulationB());
        register(SimulationC.class, new SimulationC());
        register(SimulationD.class, new SimulationD());

        // Velocity
        register(VelocityA.class, new VelocityA());
        register(VelocityB.class, new VelocityB());
        register(VelocityC.class, new VelocityC());
        register(VelocityD.class, new VelocityD());

        // Inventory
        register(InventoryA.class, new InventoryA());

        // Spoof
        register(GroundSpoofA.class, new GroundSpoofA());
        register(GroundSpoofB.class, new GroundSpoofB());
        register(GroundSpoofC.class, new GroundSpoofC());
        register(GroundSpoofD.class, new GroundSpoofD());
        register(GroundSpoofE.class, new GroundSpoofE());
        register(GroundSpoofF.class, new GroundSpoofF());
        register(GroundSpoofG.class, new GroundSpoofG());

        // Timer
        register(TimerA.class, new TimerA());

        // World
        register(FastBreakA.class, new FastBreakA());
        register(PhaseA.class, new PhaseA());

        // Scaffold
        register(ScaffoldA.class, new ScaffoldA());
        register(ScaffoldB.class, new ScaffoldB());
        register(ScaffoldC.class, new ScaffoldC());
        register(ScaffoldD.class, new ScaffoldD());
        register(ScaffoldE.class, new ScaffoldE());
        register(ScaffoldF.class, new ScaffoldF());
        register(ScaffoldG.class, new ScaffoldG());

        // Raycast
        register(RaycastA.class, new RaycastA());

        // Packet
        register(ret.tawny.truthful.checks.impl.packet.badpacket.BadPacketA.class, new ret.tawny.truthful.checks.impl.packet.badpacket.BadPacketA());
        register(ret.tawny.truthful.checks.impl.packet.badpacket.BadPacketB.class, new ret.tawny.truthful.checks.impl.packet.badpacket.BadPacketB());
        register(ret.tawny.truthful.checks.impl.packet.crasher.CrasherA.class, new ret.tawny.truthful.checks.impl.packet.crasher.CrasherA());
        register(ret.tawny.truthful.checks.impl.packet.invalid.InvalidA.class, new ret.tawny.truthful.checks.impl.packet.invalid.InvalidA());
        register(ret.tawny.truthful.checks.impl.packet.invalid.InvalidB.class, new ret.tawny.truthful.checks.impl.packet.invalid.InvalidB());

        // Packet Order
        register(PacketOrderA.class, new PacketOrderA());
        register(PacketOrderB.class, new PacketOrderB());
        register(PacketOrderC.class, new PacketOrderC());
        register(PacketOrderD.class, new PacketOrderD());
        register(PacketOrderE.class, new PacketOrderE());

        // Sprint & BadPacket Continued
        register(ret.tawny.truthful.checks.impl.packet.sprint.SprintA.class, new ret.tawny.truthful.checks.impl.packet.sprint.SprintA());
        register(ret.tawny.truthful.checks.impl.packet.sprint.SprintB.class, new ret.tawny.truthful.checks.impl.packet.sprint.SprintB());
        register(ret.tawny.truthful.checks.impl.packet.badpacket.BadPacketC.class, new ret.tawny.truthful.checks.impl.packet.badpacket.BadPacketC());
        register(ret.tawny.truthful.checks.impl.packet.badpacket.BadPacketD.class, new ret.tawny.truthful.checks.impl.packet.badpacket.BadPacketD());
        register(ret.tawny.truthful.checks.impl.packet.badpacket.BadPacketE.class, new ret.tawny.truthful.checks.impl.packet.badpacket.BadPacketE());
        register(ret.tawny.truthful.checks.impl.packet.badpacket.BadPacketF.class, new ret.tawny.truthful.checks.impl.packet.badpacket.BadPacketF());
        register(ret.tawny.truthful.checks.impl.packet.badpacket.BadPacketG.class, new ret.tawny.truthful.checks.impl.packet.badpacket.BadPacketG());
        register(ret.tawny.truthful.checks.impl.packet.badpacket.BadPacketH.class, new ret.tawny.truthful.checks.impl.packet.badpacket.BadPacketH());
        register(ret.tawny.truthful.checks.impl.packet.badpacket.BadPacketI.class, new ret.tawny.truthful.checks.impl.packet.badpacket.BadPacketI());
        register(ret.tawny.truthful.checks.impl.packet.badpacket.BadPacketJ.class, new ret.tawny.truthful.checks.impl.packet.badpacket.BadPacketJ());
        register(ret.tawny.truthful.checks.impl.packet.badpacket.BadPacketK.class, new ret.tawny.truthful.checks.impl.packet.badpacket.BadPacketK());

        // Aura
        register(ret.tawny.truthful.checks.impl.combat.crystal.CrystalAuraA.class, new ret.tawny.truthful.checks.impl.combat.crystal.CrystalAuraA());
        register(ret.tawny.truthful.checks.impl.combat.anchor.AnchorAuraA.class, new ret.tawny.truthful.checks.impl.combat.anchor.AnchorAuraA());

        // Bedrock
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

        // FIXED: 100% Obfuscator-Proof Routing.
        // We supply all checks to all lists. The base `Check` class has empty, no-op methods
        // for events it doesn't use, meaning the JVM completely ignores them with zero performance impact.
        // This ensures combat and interaction checks NEVER get lost due to reflection/renaming errors.
        movementChecks.addAll(getCollection());
        packetChecks.addAll(getCollection());
        sendPacketChecks.addAll(getCollection());
        attackChecks.addAll(getCollection());
        blockBreakChecks.addAll(getCollection());
        vehicleMoveChecks.addAll(getCollection());
        quitChecks.addAll(getCollection());
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