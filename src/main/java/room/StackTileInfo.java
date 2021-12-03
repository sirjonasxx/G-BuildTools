package room;

import gearth.extensions.parsers.HPoint;

public class StackTileInfo {

    private int furniId;

    private HPoint location;
    private int rotation;

    private int dimension;  // -1 means 1x2
    private int typeId;

    public StackTileInfo(int furniId, HPoint location, int rotation, int dimension, int typeId) {
        this.furniId = furniId;
        this.location = location;
        this.rotation = rotation;
        this.dimension = dimension;
        this.typeId = typeId;
    }

    public int getFurniId() {
        return furniId;
    }

    public HPoint getLocation() {
        return location;
    }

    public int getRotation() {
        return rotation;
    }

    public int getDimension() {
        return dimension;
    }

    public int getTypeId() {
        return typeId;
    }
}
