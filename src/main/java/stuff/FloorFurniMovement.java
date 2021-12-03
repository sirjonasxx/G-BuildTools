package stuff;


import gearth.extensions.IExtension;
import gearth.protocol.HMessage;
import gearth.protocol.HPacket;
import room.StackTileInfo;

public class FloorFurniMovement {

    private final int typeId;
    private int furniId;

    private final int oldX;
    private final int oldY;
    private final int oldRot;
    private final int newX;
    private final int newY;
    private final int newRot;

    private final int oldZ;
    private final int newZ;

    private StackTileInfo stackTileInfo;
    private StackTileInfo undoStackInfo;

    public FloorFurniMovement(int typeId, int furniId, int oldX, int oldY, int oldRot, int newX, int newY, int newRot, int oldZ, int newZ, StackTileInfo stackTileInfo, StackTileInfo undoStackInfo) {
        this.typeId = typeId;
        this.furniId = furniId;
        this.oldX = oldX;
        this.oldY = oldY;
        this.oldRot = oldRot;
        this.newX = newX;
        this.newY = newY;
        this.newRot = newRot;
        this.oldZ = oldZ;
        this.newZ = newZ;
        this.stackTileInfo = stackTileInfo;
        this.undoStackInfo = undoStackInfo;
    }

    public void perform(IExtension extension) {
        extension.sendToServer(new HPacket("MoveObject", HMessage.Direction.TOSERVER, furniId, newX, newY, newRot));
    }

    public void undo(IExtension extension) {
        extension.sendToServer(new HPacket("MoveObject", HMessage.Direction.TOSERVER, furniId, oldX, oldY, oldRot));
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

    public StackTileInfo getStackTileInfo() {
        return stackTileInfo;
    }

    public int getOldZ() {
        return oldZ;
    }

    public int getNewZ() {
        return newZ;
    }

    public int getTypeId() {
        return typeId;
    }

    public void setFurniId(int furniId) {
        this.furniId = furniId;
    }

    public StackTileInfo getUndoStackInfo() {
        return undoStackInfo;
    }
}
