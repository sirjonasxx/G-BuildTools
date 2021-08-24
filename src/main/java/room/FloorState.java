package room;

import furnidata.FurniDataTools;
import gearth.extensions.ExtensionBase;
import gearth.extensions.IExtension;
import gearth.extensions.parsers.HFloorItem;
import gearth.extensions.parsers.HPoint;
import gearth.protocol.HMessage;
import gearth.protocol.HPacket;
import javafx.beans.InvalidationListener;

import java.util.*;

public class FloorState {

    private final Object lock = new Object();

    private long latestRequestTimestamp = -1;
    private InvalidationListener onFurnisChange;
    private volatile Set<Integer> tweakedItems = new HashSet<>();

    private volatile Map<Integer, Integer> typeIdMapper = new HashMap<>();
    private volatile int[][] heightmap = null; // 256 * 256
    private volatile Map<Integer, HFloorItem> furniIdToItem = null;
    private volatile Map<Integer, Set<HFloorItem>> typeIdToItems = null;
    private volatile List<List<Map<Integer, HFloorItem>>> furnimap = null;
    private volatile char[][] floorplan = null;


    public FloorState(IExtension extension, InvalidationListener onFurnisChange) {
        this.onFurnisChange = onFurnisChange;

        extension.intercept(HMessage.Direction.TOCLIENT, "Objects", this::parseFloorItems);

        extension.intercept(HMessage.Direction.TOCLIENT, "ObjectAdd", this::onObjectAdd);
        extension.intercept(HMessage.Direction.TOCLIENT, "ObjectRemove", this::onObjectRemove);
        extension.intercept(HMessage.Direction.TOCLIENT, "ObjectUpdate", this::onObjectUpdate);
        extension.intercept(HMessage.Direction.TOCLIENT, "SlideObjectBundle", this::onObjectMove);

        extension.intercept(HMessage.Direction.TOCLIENT, "HeightMap", this::parseHeightmap);
        extension.intercept(HMessage.Direction.TOCLIENT, "HeightMapUpdate", this::heightmapUpdate);

        extension.intercept(HMessage.Direction.TOCLIENT, "FloorHeightMap", this::parseFloorPlan);

        extension.intercept(HMessage.Direction.TOCLIENT, "RoomEntryInfo", this::roomEntryInfo);

    }

    private void parseFloorPlan(HMessage hMessage) {
        synchronized (lock) {
            HPacket packet = hMessage.getPacket();
            packet.readByte();
            packet.readInteger();
            String raw = packet.readString();
            String[] split = raw.split("\r");
            floorplan = new char[split[0].length()][];
            for (int x = 0; x < split[0].length(); x++) {
                floorplan[x] = new char[split.length];
                for (int y = 0; y < split.length; y++) {
                    floorplan[x][y] = split[y].charAt(x);
                }
            }
        }
    }

    private void roomEntryInfo(HMessage hMessage) {
        if (latestRequestTimestamp > System.currentTimeMillis() - 400) {
            hMessage.setBlocked(true); // request wasnt made by user
            latestRequestTimestamp = -1;
        }
    }

    public boolean inRoom() {
        return furnimap != null && heightmap != null;
    }
    public void reset() {
        synchronized (lock) {
            heightmap = null;
            furniIdToItem = null;
            furnimap = null;
            floorplan = null;
            typeIdToItems = null;
            tweakedItems.clear();
        }

        onFurnisChange.invalidated(null);
    }

    private void parseHeightmap(HMessage hMessage) {
        HPacket packet = hMessage.getPacket();

        int columns = packet.readInteger();
        int tiles = packet.readInteger();
        int rows = tiles/columns;

        int[][] heightmap = new int[columns][];
        for (int col = 0; col < columns; col++) {
            heightmap[col] = new int[rows];
        }

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < columns; col++) {
                heightmap[col][row] = packet.readUshort();
            }
        }

        synchronized (lock) {
            this.heightmap = heightmap;
        }
    }
    private void heightmapUpdate(HMessage hMessage) {
        if (heightmap != null) {
            HPacket packet = hMessage.getPacket();
            int updates = packet.readByte();

            for (int i = 0; i < updates; i++) {
                int x = packet.readByte();
                int y = packet.readByte();
                int height = packet.readUshort();
                heightmap[x][y] = height;
            }
        }
    }

    private void parseFloorItems(HMessage hMessage) {
        boolean mustReplace = false;

        HFloorItem[] floorItems = HFloorItem.parse(hMessage.getPacket());

        synchronized (lock) {
            if (furnimap == null) {
                furniIdToItem = new HashMap<>();
                furnimap = new ArrayList<>();
                typeIdToItems = new HashMap<>();

                for (int i = 0; i < 130; i++) {
                    furnimap.add(new ArrayList<>());
                    for (int j = 0; j < 130; j++) {
                        furnimap.get(i).add(new HashMap<>());
                    }
                }
            }

            for (HFloorItem item : floorItems) {
                if (typeIdMapper.containsKey(item.getTypeId())) {
                    item.setTypeId(typeIdMapper.get(item.getTypeId()));
                    tweakedItems.add(item.getId());
                    mustReplace = true;
                }

                furnimap.get(item.getTile().getX()).get(item.getTile().getY()).put(item.getId(), item);
                furniIdToItem.put(item.getId(), item);
                if (!typeIdToItems.containsKey(item.getTypeId())) {
                    typeIdToItems.put(item.getTypeId(), new HashSet<>());
                }
                typeIdToItems.get(item.getTypeId()).add(item);
            }

            if (mustReplace) {
                HPacket packet = HFloorItem.constructPacket(floorItems, hMessage.getPacket().headerId());
                hMessage.getPacket().setBytes(packet.toBytes());
            }
        }

        onFurnisChange.invalidated(null);
    }

    private void onObjectRemove(HMessage hMessage) {
        if (inRoom()) {
            HPacket packet = hMessage.getPacket();
            int furniid = Integer.parseInt(packet.readString());
            removeObject(furniid);
            onFurnisChange.invalidated(null);
        }
    }
    private void removeObject(int furniId) {
        synchronized (lock) {
            HFloorItem item = furniIdToItem.remove(furniId);
            if (item != null) {
                furnimap.get(item.getTile().getX()).get(item.getTile().getY()).remove(item.getId());
                typeIdToItems.get(item.getTypeId()).remove(item);
            }
            tweakedItems.remove(furniId);
        }
    }
    private void onObjectAdd(HMessage hMessage) {
        if (inRoom()) {
            addObject(hMessage.getPacket(), null);
            onFurnisChange.invalidated(null);
        }

    }
    private void addObject(HPacket packet, String ownerName) {
        synchronized (lock) {
            HFloorItem item = new HFloorItem(packet);
            if (ownerName == null) {
                ownerName = packet.readString();
            }
            item.setOwnerName(ownerName);

            if (typeIdMapper.containsKey(item.getTypeId())) {
                item.setTypeId(typeIdMapper.get(item.getTypeId()));
                packet.replaceInt(10, item.getTypeId());
                tweakedItems.add(item.getId());
            }

            furnimap.get(item.getTile().getX()).get(item.getTile().getY()).put(item.getId(), item);
            furniIdToItem.put(item.getId(), item);
            if (!typeIdToItems.containsKey(item.getTypeId())) {
                typeIdToItems.put(item.getTypeId(), new HashSet<>());
            }
            typeIdToItems.get(item.getTypeId()).add(item);
        }
    }
    private void onObjectUpdate(HMessage hMessage) {
        if (inRoom()) {
            HFloorItem newItem = new HFloorItem(hMessage.getPacket());

            HFloorItem old = furniIdToItem.get(newItem.getId());
            String owner = "";
            if (old != null) {
                owner = old.getOwnerName();
            }

            removeObject(newItem.getId());
            hMessage.getPacket().resetReadIndex();
            addObject(hMessage.getPacket(), owner);
        }
    }
    private void onObjectMove(HMessage hMessage) {
        if (inRoom()) {
            HPacket packet = hMessage.getPacket();
            int oldx = packet.readInteger();
            int oldy = packet.readInteger();
            int newx = packet.readInteger();
            int newy = packet.readInteger();

            int amount = packet.readInteger();

            synchronized (lock) {
                for (int i = 0; i < amount; i++) {
                    int furniId = packet.readInteger();
                    String oldz = packet.readString();
                    String newz = packet.readString();

                    HFloorItem item = furniIdToItem.get(furniId);
                    if (item != null) {
                        furnimap.get(item.getTile().getX()).get(item.getTile().getY()).remove(item.getId());
                        item.setTile(new HPoint(newx, newy, Double.parseDouble(newz)));
                        furnimap.get(newx).get(newy).put(item.getId(), item);
                    }
                }
            }

//            int roller = packet.readInteger();
        }
    }

    public void heavyReload(ExtensionBase ext) {
        if (inRoom()) {
            List<HPacket> packets = new ArrayList<>();
            synchronized (lock) {
                for (HFloorItem floorItem : furniIdToItem.values()) {
                    if (typeIdMapper.containsKey(floorItem.getTypeId()) || tweakedItems.contains(floorItem.getId())) {
                        packets.add(new HPacket("ObjectRemove", HMessage.Direction.TOCLIENT, floorItem.getId()+"", false, 0, 0));
                    }
                }
                tweakedItems.clear();
            }

            for (HPacket packet : packets) {
                ext.sendToClient(packet);
            }

            requestRoom(ext);
        }
    }
    public void requestRoom(ExtensionBase ext) {
        latestRequestTimestamp = System.currentTimeMillis();
        ext.sendToServer(new HPacket("GetHeightMap", HMessage.Direction.TOSERVER));
    }

    public HFloorItem furniFromId(int id) {
        synchronized (lock) {
            return furniIdToItem.get(id);
        }
    }

    public List<HFloorItem> getFurniOnTile(int x, int y) {
        synchronized (lock) {
            if (inRoom()) {
                return new ArrayList<>(furnimap.get(x).get(y).values());
            }
        }
        return new ArrayList<>();
    }

    public double getTileHeight(int x, int y) {
        synchronized (lock) {
            return ((double)heightmap[x][y]) / 256;
        }
    }

    public List<HFloorItem> getItemsFromType(FurniDataTools furniDataTools, String furniName) {
        if (furniDataTools.isReady()) {
            int typeId = furniDataTools.getFloorTypeId(furniName);
            return getItemsFromType(typeId);
        }
        return new ArrayList<>();
    }

    public List<HFloorItem> getItemsFromType(int typeId) {
        synchronized (lock) {
            if (inRoom()) {
                Set<HFloorItem> result = typeIdToItems.get(typeId);
                return result == null ? new ArrayList<>() : new ArrayList<>(result);
            }
        }
        return new ArrayList<>();
    }



    public void addTypeIdMapper(FurniDataTools furniDataTools, String furniNameOld, String furniNameNew) {
        if (furniDataTools.isReady()) {
            int typeIdOld = furniDataTools.getFloorTypeId(furniNameOld);
            int typeIdNew = furniDataTools.getFloorTypeId(furniNameNew);
            addTypeIdMapper(typeIdOld, typeIdNew);
        }
    }

    public void addTypeIdMapper(int typeIdOld, int typeIdNew) {
        synchronized (lock) {
            typeIdMapper.put(typeIdOld, typeIdNew);
        }
    }

    public void removeTypeIdMapper(FurniDataTools furniDataTools, String furniName) {
        if (furniDataTools.isReady()) {
            int typeId = furniDataTools.getFloorTypeId(furniName);
            removeTypeIdMapper(typeId);
        }
    }

    public void removeTypeIdMapper(int typeId) {
        synchronized (lock) {
            typeIdMapper.remove(typeId);
        }
    }

    public boolean hasMappings() {
        synchronized (lock) {
            return typeIdMapper.size() > 0;
        }
    }

    public char floorHeight(int x, int y) {
        char result;
        synchronized (lock) {
            result = (floorplan != null && x >= 0 && y >= 0 &&
                    x < floorplan.length && y < floorplan[x].length) ? floorplan[x][y] : 'x';
        }
        return result;
    }
}
