package stuff;

public class WallFurniDropInfo extends WallFurniInfo implements DropInfo {

    private long dropTimeStamp = -1;
    private final long tempFurniId;

    public WallFurniDropInfo(String info, long tempFurniId) {
        super(info);
        this.tempFurniId = tempFurniId;
    }

    public long getDropTimeStamp() {
        return dropTimeStamp;
    }

    public void setDropTimeStamp(long dropTimeStamp) {
        this.dropTimeStamp = dropTimeStamp;
    }

    public long getTempFurniId() {
        return tempFurniId;
    }
}
