package room;

import java.util.Arrays;

public enum StackTileSetting {

    Small("tile_stackmagic", 1),
    Medium("tile_stackmagic1", -1),
    Large("tile_stackmagic2", 2),
    XL("tile_stackmagic4x4", 4),
    XXL("tile_stackmagic6x6", 6),
    XXXL("tile_stackmagic8x8", 8);

    private String className;
    private int dimension;

    StackTileSetting(String className, int dimension) {
        this.className = className;
        this.dimension = dimension;
    }

    public String getClassName() {
        return className;
    }

    public int getDimension() {
        return dimension;
    }

    public static StackTileSetting fromString(String s) {
        switch (s) {
            case "1x1":
                return Small;
            case "1x2":
                return Medium;
            case "2x2":
                return Large;
            case "4x4":
                return XL;
            case "6x6":
                return XXL;
            case "8x8":
                return XXXL;
        }
        return null;
    }

    public static StackTileSetting fromClassName(String className) {
        return Arrays.stream(values()).filter(v -> v.getClassName().equals(className)).findFirst().orElse(null);
    }

    @Override
    public String toString() {
        return String.format("%dx%d", dimension, dimension);
    }
}
