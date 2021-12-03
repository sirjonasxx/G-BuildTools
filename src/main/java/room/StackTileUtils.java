package room;

import gearth.extensions.parsers.HPoint;

import java.util.ArrayList;
import java.util.List;

public class StackTileUtils {

    public static StackTileInfo findBestDropLocation(int x, int y, List<StackTileInfo> potentialStacktiles, FloorState floorState) {
        if (!floorState.inRoom()) return null;

        potentialStacktiles.sort((o1, o2) -> -Integer.compare(o1.getDimension(), o2.getDimension()));

        for (StackTileInfo info : potentialStacktiles) {
            int dimension = info.getDimension();
            if (dimension != -1) {
                HPoint dropLocation = findBestDropLocation(x, y, dimension, floorState);

                if (dropLocation != null) {
                    return new StackTileInfo(info.getFurniId(), dropLocation, 0, dimension, info.getTypeId());
                }
            }
            else {
                // is 1x2 tile
                char rootChar = floorState.floorHeight(x, y);
                if (rootChar == 'x') continue;

                HPoint location = null;
                int rotation = -1;

                if (floorState.floorHeight(x+1, y) == rootChar) {
                    location = new HPoint(x, y);
                    rotation = 0;
                }
                else if (floorState.floorHeight(x, y+1) == rootChar) {
                    location = new HPoint(x, y);
                    rotation = 2;
                }
                else if (floorState.floorHeight(x-1, y) == rootChar) {
                    location = new HPoint(x-1, y);
                    rotation = 0;
                }
                else if (floorState.floorHeight(x, y-1) == rootChar) {
                    location = new HPoint(x, y-1);
                    rotation = 2;
                }

                if (location != null) {
                    return new StackTileInfo(info.getFurniId(), location, rotation, -1, info.getTypeId());
                }
            }
        }

        return null;
    }

    public static HPoint findBestDropLocation(int x, int y, int stackDimensions, FloorState floorState) {
        List<HPoint> possibleLocations = new ArrayList<>();

        for (int x2 = x; x2 > Math.max(x - stackDimensions, 0); x2--) {
            for (int y2 = y; y2 > Math.max(y - stackDimensions, 0); y2--) {
                possibleLocations.add(new HPoint(x2, y2));
            }
        }

        possibleLocations.sort((o1, o2) -> {
            int score1 = (x-o1.getX())*(x-o1.getX()) + (y-o1.getY())*(y-o1.getY());
            int score2 = (x-o2.getX())*(x-o2.getX()) + (y-o2.getY())*(y-o2.getY());

            return score1 - score2;
        });

        outerloop:
        for (HPoint p : possibleLocations) {
            int x2 = p.getX();
            int y2 = p.getY();
            char referenceChar = floorState.floorHeight(x2, y2);
            if (referenceChar == 'x') continue;

            for (int x3 = 0; x3 < stackDimensions; x3++) {
                for (int y3 = 0; y3 < stackDimensions; y3++) {
                    if (floorState.floorHeight(x2 + x3, y2 + y3) != referenceChar) continue outerloop;
                }
            }

            return new HPoint(x2, y2);
        }

        return null;
    }

}
