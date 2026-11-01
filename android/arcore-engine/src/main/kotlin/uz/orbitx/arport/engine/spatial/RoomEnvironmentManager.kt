package uz.orbitx.arport.engine.spatial

/**
 * 3D Spatial Room Environment Manager.
 * Analyzes and maintains a virtual 3D bounding mesh of the physical room:
 * floor, table/desk surfaces, ceiling, walls, and corners.
 * Remains invisible during normal app usage, while providing collision
 * boundaries, surface snapping, and spawn anchors for 3D games and spatial physics.
 */
class RoomEnvironmentManager {

    companion object {
        private const val TAG = "ARPort_RoomEnv"
    }

    // Physical Room Dimensions (Meters)
    var roomWidthMeters: Float = 5.0f // X: -2.5m to +2.5m
    var roomDepthMeters: Float = 5.5f // Z: -3.2m (front wall) to +2.3m (back wall)
    var roomHeightMeters: Float = 2.8f // Y: -1.2m (floor) to +1.6m (ceiling)

    // Detected / Estimated Key Surfaces
    var floorY: Float = -1.2f
        private set
    var ceilingY: Float = 1.6f
        private set

    // Primary detected table / workspace surface
    var tableSurfaceX: Float = 0.0f
    var tableSurfaceY: Float = -0.42f // ~42cm below headset (standard desk height)
    var tableSurfaceZ: Float = -1.4f // ~1.4m directly in front of user
    var tableWidthMeters: Float = 1.5f
    var tableDepthMeters: Float = 0.9f

    // Walls
    var wallFrontZ: Float = -3.2f
    var wallBackZ: Float = 2.3f
    var wallLeftX: Float = -2.5f
    var wallRightX: Float = 2.5f

    // 4 Room Corners: [Front-Left, Front-Right, Back-Left, Back-Right]
    data class Corner3D(val x: Float, val y: Float, val z: Float)
    fun getRoomCorners(): List<Corner3D> {
        return listOf(
            Corner3D(wallLeftX, floorY, wallFrontZ),
            Corner3D(wallRightX, floorY, wallFrontZ),
            Corner3D(wallLeftX, floorY, wallBackZ),
            Corner3D(wallRightX, floorY, wallBackZ)
        )
    }

    /**
     * Calibrates / updates room model based on device camera height and AR tracking.
     */
    fun updateFromPose(cameraY: Float) {
        if (cameraY > 0.5f) {
            floorY = -cameraY
            ceilingY = 2.6f - cameraY
            tableSurfaceY = floorY + 0.75f // Standard desk 75cm above floor
        }
    }

    /**
     * Snaps a 3D game object to the nearest surface (table or floor).
     */
    fun snapToNearestSurface(x: Float, y: Float, z: Float): FloatArray {
        val isOverTable = (x in (tableSurfaceX - tableWidthMeters * 0.6f)..(tableSurfaceX + tableWidthMeters * 0.6f)) &&
                          (z in (tableSurfaceZ - tableDepthMeters * 0.6f)..(tableSurfaceZ + tableDepthMeters * 0.6f))

        val snappedY = if (isOverTable) tableSurfaceY else floorY
        return floatArrayOf(x, snappedY, z)
    }

    /**
     * Returns 3D spawn positions along room walls and corners for 3D Target Shooter and spatial games.
     */
    fun getShooterSpawnAnchors(): List<FloatArray> {
        return listOf(
            floatArrayOf(wallLeftX * 0.8f, tableSurfaceY + 0.5f, wallFrontZ * 0.85f),
            floatArrayOf(0.0f, tableSurfaceY + 0.7f, wallFrontZ * 0.9f),
            floatArrayOf(wallRightX * 0.8f, tableSurfaceY + 0.5f, wallFrontZ * 0.85f),
            floatArrayOf(wallLeftX * 0.9f, 0.0f, -1.8f),
            floatArrayOf(wallRightX * 0.9f, 0.0f, -1.8f)
        )
    }

    /**
     * Checks if a point is within safe room boundaries.
     */
    fun isInsideRoom(x: Float, y: Float, z: Float, padding: Float = 0.3f): Boolean {
        return x in (wallLeftX + padding)..(wallRightX - padding) &&
               y in (floorY + padding)..(ceilingY - padding) &&
               z in (wallFrontZ + padding)..(wallBackZ - padding)
    }
}

