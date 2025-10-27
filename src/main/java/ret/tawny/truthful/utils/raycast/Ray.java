package ret.tawny.truthful.utils.raycast;

import org.bukkit.util.Vector;

public final class Ray {
    private final Vector home;

    private final double length;

    public Ray(final Vector home, final double length) {
        this.home = home;
        this.length = length;
    }

    public static boolean intersects(int[] A, int[] B, double[] P) {
        if (A[1] > B[1])
            return intersects(B, A, P);

        if (P[1] == A[1] || P[1] == B[1])
            P[1] += 0.0001;

        if (P[1] > B[1] || P[1] < A[1] || P[0] >= Math.max(A[0], B[0]))
            return false;

        if (P[0] < Math.min(A[0], B[0]))
            return true;

        double red = (P[1] - A[1]) / (double) (P[0] - A[0]);
        double blue = (B[1] - A[1]) / (double) (B[0] - A[0]);
        return red >= blue;
    }

    static boolean contains(int[][] points, double[] pnt) {
        boolean inside = false;
        int len = points.length;
        for (int i = 0; i < len; i++) {
            if (intersects(points[i], points[(i + 1) % len], pnt))
                inside = !inside;
        }
        return inside;
    }
}
