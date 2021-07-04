public class FloorFurniDropInfo {

    private final long furniId;
    private final long tempFurniId;
    private final int rotation;

    private final int x;
    private final int y;

    private long dropTimeStamp = -1;

    public FloorFurniDropInfo(long furniId, long tempFurniId, int rotation, int x, int y) {
        this.furniId = furniId;
        this.tempFurniId = tempFurniId;
        this.rotation = rotation;
        this.x = x;
        this.y = y;
    }

    public long getFurniId() {
        return furniId;
    }

    public long getTempFurniId() {
        return tempFurniId;
    }

    public int getRotation() {
        return rotation;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public long getDropTimeStamp() {
        return dropTimeStamp;
    }

    public void setDropTimeStamp(long dropTimeStamp) {
        this.dropTimeStamp = dropTimeStamp;
    }
}
