package stuff;

import gearth.extensions.extra.tools.PacketInfoSupport;

public class FloorFurniMovement {

    private final int furniId;

    private final int oldX;
    private final int oldY;
    private final int oldRot;
    private final int newX;
    private final int newY;
    private final int newRot;

    private final int oldZ;
    private final int newZ;

    private final boolean useStacktile;

    public FloorFurniMovement(int furniId, int oldX, int oldY, int oldRot, int newX, int newY, int newRot, int oldZ, int newZ, boolean useStacktile) {
        this.furniId = furniId;
        this.oldX = oldX;
        this.oldY = oldY;
        this.oldRot = oldRot;
        this.newX = newX;
        this.newY = newY;
        this.newRot = newRot;
        this.oldZ = oldZ;
        this.newZ = newZ;
        this.useStacktile = useStacktile;
    }

    public void perform(PacketInfoSupport packetInfoSupport) {
        packetInfoSupport.sendToServer("MoveObject", furniId, newX, newY, newRot);
    }

    public void undo(PacketInfoSupport packetInfoSupport) {
        packetInfoSupport.sendToServer("MoveObject", furniId, oldX, oldY, oldRot);
    }

    public int getFurniId() {
        return furniId;
    }

    public int getOldX() {
        return oldX;
    }

    public int getOldY() {
        return oldY;
    }

    public int getOldRot() {
        return oldRot;
    }

    public int getNewX() {
        return newX;
    }

    public int getNewY() {
        return newY;
    }

    public int getNewRot() {
        return newRot;
    }

    public boolean useStacktile() {
        return useStacktile;
    }

    public int getOldZ() {
        return oldZ;
    }

    public int getNewZ() {
        return newZ;
    }
}
