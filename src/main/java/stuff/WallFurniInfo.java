package stuff;

import gearth.extensions.parsers.HPoint;

public class WallFurniInfo {

    private long furniId;

    private int x;
    private int y;
    private int xOffset;
    private int yOffset;

    private boolean isLeft;

    public WallFurniInfo(String info) {
        this(Long.parseLong(info.split(" ")[0]), info.split(" ", 2)[1]);
    }

    public WallFurniInfo(long furniId, String info) {
        this.furniId = furniId;

        String[] infoSplit = info.split(" ");

        String[] pointInfo = infoSplit[0].substring(3).split(",");
        x = Integer.parseInt(pointInfo[0]);
        y = Integer.parseInt(pointInfo[1]);

        String[] offsetInfo = infoSplit[1].substring(2).split(",");
        xOffset = Integer.parseInt(offsetInfo[0]);
        yOffset = Integer.parseInt(offsetInfo[1]);

        isLeft = infoSplit[2].equals("l");
    }

    public WallFurniInfo(long furniId, int x, int y, int xOffset, int yOffset, boolean isLeft) {
        this.furniId = furniId;
        this.x = x;
        this.y = y;
        this.xOffset = xOffset;
        this.yOffset = yOffset;
        this.isLeft = isLeft;
    }

    public WallFurniInfo(WallFurniInfo wallFurniInfo) {
        this.furniId = wallFurniInfo.getFurniId();
        this.x = wallFurniInfo.getX();
        this.y = wallFurniInfo.getY();
        this.xOffset = wallFurniInfo.getxOffset();
        this.yOffset = wallFurniInfo.getyOffset();
        this.isLeft = wallFurniInfo.isLeft;
    }

    public String moveString() {
        return String.format(":w=%d,%d l=%d,%d %c", x, y, xOffset, yOffset, isLeft ? 'l' : 'r');
    }

    public String placeString() {
        return String.format("%d %s", furniId, moveString());
    }


    public long getFurniId() {
        return furniId;
    }

    public void setFurniId(long furniId) {
        this.furniId = furniId;
    }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }

    public int getxOffset() {
        return xOffset;
    }

    public void setxOffset(int xOffset) {
        this.xOffset = xOffset;
    }

    public int getyOffset() {
        return yOffset;
    }

    public void setyOffset(int yOffset) {
        this.yOffset = yOffset;
    }

    public boolean isLeft() {
        return isLeft;
    }

    public void setLeft(boolean left) {
        isLeft = left;
    }
}
