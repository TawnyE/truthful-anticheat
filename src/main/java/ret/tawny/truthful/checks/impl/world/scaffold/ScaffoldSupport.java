package ret.tawny.truthful.checks.impl.world.scaffold;

import com.github.retrooper.packetevents.util.Vector3i;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;
import ret.tawny.truthful.data.PlayerData;
import ret.tawny.truthful.wrapper.impl.client.action.PlayerBlockPlacePacketWrapper;

final class ScaffoldSupport {
    private ScaffoldSupport() {}

    static PlacementContext context(final PlayerData data) {
        if (data == null) return null;

        PlayerBlockPlacePacketWrapper place = data.getCurrentBlockPlacement();
        if (place == null || place.getBlockPosition() == null) return null;

        Vector3i clicked = place.getBlockPosition();
        BlockFace face = place.getBlockFace();
        if (face == null) return null;

        int placedX = clicked.getX() + face.getModX();
        int placedY = clicked.getY() + face.getModY();
        int placedZ = clicked.getZ() + face.getModZ();

        Vector hit = place.getHitVec();
        double hitX = clicked.getX() + clamp01(hit.getX());
        double hitY = clicked.getY() + clamp01(hit.getY());
        double hitZ = clicked.getZ() + clamp01(hit.getZ());

        double eyeY = data.getY() + data.getEyeHeight(false, data.isSneaking(), data.isSwimming());
        double dx = hitX - data.getX();
        double dy = hitY - eyeY;
        double dz = hitZ - data.getZ();
        double horizontal = Math.hypot(dx, dz);
        double reach = Math.sqrt(dx * dx + dy * dy + dz * dz);

        float requiredYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float requiredPitch = (float) Math.toDegrees(-Math.atan2(dy, horizontal));

        double placedCenterDx = placedX + 0.5D - data.getX();
        double placedCenterDz = placedZ + 0.5D - data.getZ();
        double placedHorizontal = Math.hypot(placedCenterDx, placedCenterDz);

        boolean belowFeet = placedY <= Math.floor(data.getY()) - 1;
        boolean nearFeet = placedY <= data.getY() + 0.25D && placedHorizontal <= 2.15D;
        boolean scaffoldLike = nearFeet && data.getDeltaXZ() > 0.045D;

        return new PlacementContext(
                clicked.getX(), clicked.getY(), clicked.getZ(),
                placedX, placedY, placedZ,
                requiredYaw, requiredPitch,
                yawDistance(data.getYaw(), requiredYaw),
                Math.abs(data.getPitch() - requiredPitch),
                reach,
                placedHorizontal,
                belowFeet,
                scaffoldLike,
                face
        );
    }

    static boolean shouldLookDown(final PlacementContext ctx) {
        return ctx != null && ctx.placedY <= ctx.clickedY && ctx.requiredPitch >= 45.0F;
    }

    static boolean isLookingAtPlacement(final PlayerData data, final PlacementContext ctx, final double maxPitchError, final double maxYawError) {
        if (data == null || ctx == null) return false;
        return ctx.pitchError <= maxPitchError && ctx.yawError <= maxYawError;
    }

    static float yawDistance(final float a, final float b) {
        float diff = Math.abs(a - b) % 360.0F;
        return diff > 180.0F ? 360.0F - diff : diff;
    }

    private static double clamp01(final double value) {
        if (value < 0.0D) return 0.0D;
        if (value > 1.0D) return 1.0D;
        return value;
    }

    static final class PlacementContext {
        final int clickedX;
        final int clickedY;
        final int clickedZ;
        final int placedX;
        final int placedY;
        final int placedZ;
        final float requiredYaw;
        final float requiredPitch;
        final float yawError;
        final float pitchError;
        final double reach;
        final double placedHorizontal;
        final boolean belowFeet;
        final boolean scaffoldLike;
        final BlockFace face;

        PlacementContext(final int clickedX, final int clickedY, final int clickedZ,
                         final int placedX, final int placedY, final int placedZ,
                         final float requiredYaw, final float requiredPitch,
                         final float yawError, final float pitchError,
                         final double reach, final double placedHorizontal,
                         final boolean belowFeet, final boolean scaffoldLike,
                         final BlockFace face) {
            this.clickedX = clickedX;
            this.clickedY = clickedY;
            this.clickedZ = clickedZ;
            this.placedX = placedX;
            this.placedY = placedY;
            this.placedZ = placedZ;
            this.requiredYaw = requiredYaw;
            this.requiredPitch = requiredPitch;
            this.yawError = yawError;
            this.pitchError = pitchError;
            this.reach = reach;
            this.placedHorizontal = placedHorizontal;
            this.belowFeet = belowFeet;
            this.scaffoldLike = scaffoldLike;
            this.face = face;
        }
    }
}
