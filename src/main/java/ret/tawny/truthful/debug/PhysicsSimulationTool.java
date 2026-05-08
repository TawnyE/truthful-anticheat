package ret.tawny.truthful.debug;

/**
 * PhysicsSimulationTool
 * 
 * Used to verify Speed/Fly thresholds by simulating vanilla and cheat
 * movements.
 */
public class PhysicsSimulationTool {

    public static void main(String[] args) {
        System.out.println("=== Truthful Anti-Cheat Physics Simulator ===");

        testVanillaSprint();
        testCheatLongJump();
        testVanillaJump();
    }

    private static void testVanillaSprint() {
        System.out.println("\n--- Testing Vanilla Sprint (Flat Ground) ---");
        double xz = 0.0;
        float friction = 0.6f * 0.91f; // Standard block friction
        float walkSpeed = 0.1f * 1.3f; // Sprint
        double acceleration = walkSpeed * (0.16277136 / (friction * friction * friction));

        for (int i = 1; i <= 20; i++) {
            xz *= friction;
            xz += acceleration;
            System.out.printf("Tick %d: Speed = %.4f\n", i, xz);
        }
    }

    private static void testVanillaJump() {
        System.out.println("\n--- Testing Vanilla Jump (Vertical) ---");
        double y = 0.42; // Jump impulse
        double gravity = 0.08;
        double drag = 0.98;

        for (int i = 1; i <= 10; i++) {
            System.out.printf("Tick %d: DeltaY = %.4f\n", i, y);
            y -= gravity;
            y *= drag;
            if (y < -3.92)
                y = -3.92; // Terminal velocity
        }
    }

    private static void testCheatLongJump() {
        System.out.println("\n--- Testing Cheat Long Jump (0.5 blocks/tick constant) ---");
        double simulatedXZ = 0.0;
        double speedCheat = 0.5;
        float friction = 0.91f; // Air drag
        double accelerationNormal = 0.026; // Air sprint acceleration

        for (int i = 1; i <= 10; i++) {
            simulatedXZ *= friction;
            simulatedXZ += accelerationNormal;

            double diff = speedCheat - (simulatedXZ + 0.02); // 0.02 is tolerance
            System.out.printf("Tick %d: Cheat = %.3f, SimMax = %.3f, Diff = %.3f %s\n",
                    i, speedCheat, simulatedXZ + 0.02, diff, (diff > 0 ? "[FLAG]" : "[OK]"));
        }
    }
}
