package component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javafx.beans.Observable;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

import gearth.extensions.parsers.HStuff;
import gearth.protocol.HMessage;
import gearth.protocol.HPacket;
import gearth.protocol.connection.HClient;

import extension.GBuildTools;

import javafx.util.Pair;
import room.FloorState;

public class IllusionAssistComponent {
    private final Object lock = new Object();

    private final GBuildTools extension;
    private final FloorState floorState;

    private final BooleanProperty flattenFloorEnabled = new SimpleBooleanProperty();
    private final BooleanProperty translateEnabled = new SimpleBooleanProperty();

    private boolean flattenFloorActive = true;

    private int roomWidth, roomLength;
    private short[][] heightmap;
    private int[][] floorplan;

    private final Map<Integer, EntityLocation> entityMap = new HashMap<>();
    private final Map<Integer, EntityLocation> itemMap = new HashMap<>();

    public IllusionAssistComponent(GBuildTools extension, FloorState floorState) {
        this.extension = extension;
        this.floorState = floorState;

        translateEnabled.addListener(this::onTranslateEnabledChanged);

        extension.onConnect(this::onConnect);
    }

    public BooleanProperty flattenFloorEnabledProperty() { return flattenFloorEnabled; }
    public BooleanProperty translateEnabledProperty() { return translateEnabled; }

    private void onTranslateEnabledChanged(Observable observable) {
        if (!flattenFloorActive) return;

        boolean translate = translateEnabled.get();

        Map<Pair<Integer, Integer>, List<EntityLocation>> map = new HashMap<>();
        synchronized (lock) {
            for (EntityLocation loc : itemMap.values()) {
                Pair<Integer, Integer> xy = new Pair<>(loc.x, loc.y);
                List<EntityLocation> ls;
                if (map.containsKey(xy))
                    ls = map.get(xy);
                else
                    map.put(xy, ls = new ArrayList<>());
                ls.add(loc);
            }
        }

        for (int y = 0; y < roomLength; y++) {
            for (int x = 0; x < roomWidth; x++) {
                int h = getFloorHeight(x, y);
                if (h <= 0) continue;

                Pair<Integer, Integer> xy = new Pair<>(x, y);
                if (!map.containsKey(xy)) continue;

                List<EntityLocation> items = map.get(xy);

                HPacket packet = new HPacket("SlideObjectBundle", HMessage.Direction.TOCLIENT);
                packet.appendInt(x); packet.appendInt(y);
                packet.appendInt(x); packet.appendInt(y);
                packet.appendInt(items.size());
                for (EntityLocation item : items) {
                    packet.appendInt(item.identifier);

                    float fromZ = item.z;
                    float toZ = item.z - h;

                    if (!translate) {
                        float tmp = fromZ;
                        fromZ = toZ;
                        toZ = tmp;
                    }

                    packet.appendString(Double.toString(fromZ));
                    packet.appendString(Double.toString(toZ));
                }
                packet.appendInt(-1);
                extension.sendToClient(packet);
            }
        }

        synchronized (lock) {
            for (EntityLocation e : entityMap.values()) {
                int h = getFloorHeight(e.x, e.y);
                if (h <= 0) continue;
                HPacket packet = new HPacket("SlideObjectBundle", HMessage.Direction.TOCLIENT);
                packet.appendInt(e.x); packet.appendInt(e.y);
                packet.appendInt(e.x); packet.appendInt(e.y);
                packet.appendInt(0);
                packet.appendInt(0);
                packet.appendInt(2);
                packet.appendInt(e.identifier);
                float fromZ = e.z;
                float toZ = e.z - h;
                if (!translate) {
                    float tmp = fromZ;
                    fromZ = toZ;
                    toZ = tmp;
                }
                packet.appendString(Double.toString(fromZ));
                packet.appendString(Double.toString(toZ));
                extension.sendToClient(packet);
            }
        }

        // adjust heightmap
        List<HeightmapDiff> diffs = new ArrayList<>();
        for (int y = 0; y < roomLength; y++) {
            for (int x = 0; x < roomWidth; x++) {
                int h = getFloorHeight(x, y);
                if (h <= 0) continue;
                short value = getHeightmapValue(x, y);
                if (translate)
                    value = translateHeightmapValue(x, y, value);
                diffs.add(new HeightmapDiff(x, y, value));
            }
        }

        int chunkSize = Byte.MAX_VALUE;
        int chunks = (int)Math.ceil(diffs.size() / (double)chunkSize);
        for (int i = 0; i < chunks; i++) {
            int size = Math.min(chunkSize, diffs.size() - i * chunkSize);
            HPacket packet = new HPacket("HeightMapUpdate", HMessage.Direction.TOCLIENT);
            packet.appendByte((byte) size);
            for (int j = 0; j < size; j++) {
                HeightmapDiff diff = diffs.get(i * chunkSize + j);
                packet.appendByte((byte)diff.x);
                packet.appendByte((byte)diff.y);
                packet.appendShort(diff.value);
            }
            extension.sendToClient(packet);
        }
    }

    private void onConnect(String s, int i, String s1, String s2, HClient hClient) {
        extension.intercept(HMessage.Direction.TOCLIENT, "HeightMap", this::onHeightMap);
        extension.intercept(HMessage.Direction.TOCLIENT, "FloorHeightMap", this::onFloorHeightMap);
        extension.intercept(HMessage.Direction.TOCLIENT, "HeightMapUpdate", this::onHeightMapUpdate);
        extension.intercept(HMessage.Direction.TOCLIENT, "Objects", this::onObjects);
        extension.intercept(HMessage.Direction.TOCLIENT, "ObjectAdd", this::onFloorItemAddOrUpdate);
        extension.intercept(HMessage.Direction.TOCLIENT, "ObjectUpdate", this::onFloorItemAddOrUpdate);
        extension.intercept(HMessage.Direction.TOCLIENT, "SlideObjectBundle", this::onSlideObjectBundle);
        extension.intercept(HMessage.Direction.TOCLIENT, "Users", this::onUsers);
        extension.intercept(HMessage.Direction.TOCLIENT, "UserUpdate", this::onUserUpdate);
    }

    private int getFloorHeight(int x, int y) {
        synchronized (lock) {
            if (floorplan == null ||
                    x < 0 || y < 0 ||
                    x >= roomWidth || y >= roomLength) {
                return 0;
            }
            return Math.max(0, floorplan[y][x]);
        }
    }

    private short getHeightmapValue(int x, int y) {
        synchronized (lock) {
            if (heightmap == null ||
                    x < 0 || y < 0 ||
                    x >= roomWidth || x >= roomLength) {
                return 0x4000;
            }
            return heightmap[y][x];
        }
    }

    public float getTranslatedHeightmapHeight(int x, int y) {
        synchronized (lock) {
            float height = (getHeightmapValue(x, y) & 0x3FFF) / 256.0f;
            if (flattenFloorActive && translateEnabled.get())
                height -= getFloorHeight(x, y);
            return height;
        }
    }

    private float translateZ(int x, int y, float z) { return (z - getFloorHeight(x, y)); }

    private float replaceZ(HPacket packet, int x, int y) {
        int index = packet.getReadIndex();
        float z = Float.parseFloat(packet.readString());
        float translatedZ = translateZ(x, y, z);
        packet.replaceString(index, Float.toString(translatedZ));
        packet.setReadIndex(index);
        packet.readString();
        return z;
    }

    private short translateHeightmapValue(int x, int y, short value) {
        float height = (value & 0x3FFF) / 256.0f;
        height = translateZ(x, y, height);
        value &= 0xC000;
        value |= ((short)(height * 256.0f) & 0x3FFF);
        return value;
    }

    private void onHeightMap(HMessage hMessage) {
        flattenFloorActive = extension.buildToolsEnabled() && flattenFloorEnabled.get();

        hMessage.setBlocked(flattenFloorActive);
        HPacket packet = hMessage.getPacket();
        roomWidth = packet.readInteger();
        int tiles = packet.readInteger();
        roomLength = tiles / roomWidth;

        synchronized (lock) {
            entityMap.clear();
            itemMap.clear();

            heightmap = new short[roomLength][roomWidth];
            for (int y = 0; y < roomLength; y++) {
                for (int x = 0; x < roomWidth; x++) {
                    heightmap[y][x] = packet.readShort();
                }
            }
        }
    }

    private void onFloorHeightMap(HMessage hMessage) {
        hMessage.setBlocked(flattenFloorActive);
        HPacket packet = hMessage.getPacket();

        boolean legacyWallScaling = packet.readBoolean();
        int wallHeight = packet.readInteger(); // wall height
        String map = packet.readString();
        String[] rows = map.split("\r");

        synchronized (lock) {
            floorplan = new int[rows.length][rows[0].length()];
            for (int y = 0; y < roomLength; y++) {
                for (int x = 0; x < roomWidth; x++) {
                    char c = Character.toLowerCase(rows[y].charAt(x));
                    int height = -1;
                    if ('0' <= c && c <= '9') {
                        height = c - '0';
                    } else if ('a' <= c && c <= 'z' && c != x) {
                        height = 10 + (c - 'a');
                    }
                    floorplan[y][x] = height;
                }
            }
        }

        if (!flattenFloorActive) return;

        HPacket translatedHeightmap = new HPacket("HeightMap", HMessage.Direction.TOCLIENT);
        translatedHeightmap.appendInt(roomWidth);
        translatedHeightmap.appendInt(roomWidth * roomLength);
        for (int y = 0; y < roomLength; y++) {
            for (int x = 0; x < roomWidth; x++) {
                short value = translateHeightmapValue(x, y, heightmap[y][x]);
                translatedHeightmap.appendShort(value);
            }
        }

        HPacket translatedFloorplan = new HPacket("FloorHeightMap", HMessage.Direction.TOCLIENT);
        translatedFloorplan.appendBoolean(legacyWallScaling);
        translatedFloorplan.appendInt(wallHeight);
        translatedFloorplan.appendString(map.replaceAll("(?i)[1-9a-wy-z]", "0"));

        extension.sendToClient(translatedHeightmap);
        extension.sendToClient(translatedFloorplan);
    }

    private void onHeightMapUpdate(HMessage hMessage) {
        boolean translate = flattenFloorActive && translateEnabled.get();

        HPacket packet = hMessage.getPacket();

        int n = packet.readByte();
        for (int i = 0; i < n; i++) {
            int x = packet.readByte();
            int y = packet.readByte();
            short value = packet.readShort();
            heightmap[y][x] = value;
            if (translate) {
                value = translateHeightmapValue(x, y, value);
                packet.replaceShort(packet.getReadIndex() - 2, value);
            }
        }
    }

    private void onObjects(HMessage hMessage) {
        boolean translate = flattenFloorActive && translateEnabled.get();

        HPacket packet = hMessage.getPacket();

        int n = packet.readInteger();
        for (int i = 0; i < n; i++)
            packet.skip("is");

        n = packet.readInteger();
        for (int i = 0; i < n; i++) {
            int id = packet.readInteger();
            packet.readInteger();
            int x = packet.readInteger();
            int y = packet.readInteger();
            packet.readInteger();
            float z;
            if (translate) {
                z = replaceZ(packet, x, y);
            } else {
                z = Float.parseFloat(packet.readString());
            }
            packet.skip("si");
            int stuffDataType = packet.readInteger();
            HStuff.readData(packet, stuffDataType);
            packet.skip("iii");

            synchronized (lock) {
                EntityLocation loc = new EntityLocation(id, x, y, z);
                itemMap.put(id, loc);
            }
        }
    }

    private void onFloorItemAddOrUpdate(HMessage hMessage) {
        boolean translate = flattenFloorActive && translateEnabled.get();

        HPacket packet = hMessage.getPacket();
        int id = packet.readInteger();
        packet.readInteger();
        int x = packet.readInteger();
        int y = packet.readInteger();
        packet.readInteger();
        float z;
        if (translate) {
            z = replaceZ(packet, x, y);
        } else {
            z = Float.parseFloat(packet.readString());
        }

        synchronized (lock) {
            itemMap.put(id, new EntityLocation(id, x, y, z));
        }
    }

    private void onSlideObjectBundle(HMessage hMessage) {
        boolean translate = flattenFloorActive && translateEnabled.get();

        HPacket packet = hMessage.getPacket();

        int fromX = packet.readInteger();
        int fromY = packet.readInteger();
        int toX = packet.readInteger();
        int toY = packet.readInteger();

        int updates = packet.readInteger();
        for (int i = 0; i < updates; i++) {
            int id = packet.readInteger();
            /*
                using the offset at the toX/Y tile to translate fromZ gets rid of
                visual glitching when floor items slide up & down stairs in the floor plan,
                as the item jumps to the toX/Y tile height at the start of the slide.
             */
            float z;
            if (translate) {
                replaceZ(packet, toX, toY);
                z = replaceZ(packet, toX, toY);
            } else {
                packet.readString();
                z = Float.parseFloat(packet.readString());
            }
            synchronized (lock) {
                itemMap.put(id, new EntityLocation(id, toX, toY, z));
            }
        }

        int updateType = packet.readInteger();
        if (updateType == 1 || updateType == 2) {
            int index = packet.readInteger();
            float z;
            if (translate) {
                replaceZ(packet, toX, toY);
                z = replaceZ(packet, toX, toY);
            } else {
                packet.readString();
                z = Float.parseFloat(packet.readString());
            }
            synchronized (lock) {
                entityMap.put(index, new EntityLocation(index, toX, toY, z));
            }
        }
    }

    private void onUsers(HMessage hMessage) {
        boolean translate = flattenFloorActive && translateEnabled.get();

        HPacket packet = hMessage.getPacket();

        int n = packet.readInteger();
        for (int i = 0; i < n; i++) {
            packet.skip("isss");
            int index = packet.readInteger();
            int x = packet.readInteger();
            int y = packet.readInteger();
            float z;
            if (translate) {
                z = replaceZ(packet, x, y);
            } else {
                z = Float.parseFloat(packet.readString());
            }
            synchronized (lock) {
                entityMap.put(index, new EntityLocation(index, x, y, z));
            }
            packet.readInteger();
            int type = packet.readInteger();
            switch (type) {
                case 1: packet.skip("siissib"); break;
                case 2: packet.skip("iisibbbbbbis"); break;
                case 4:
                    packet.skip("sis");
                    int count = packet.readInteger();
                    for (int j = 0; j < count; j++)
                        packet.readShort();
                    break;
                default: break;
            }
        }
    }

    private void onUserUpdate(HMessage hMessage) {
        boolean translate = flattenFloorActive && translateEnabled.get();

        HPacket packet = hMessage.getPacket();

        int n = packet.readInteger();
        for (int i = 0; i < n; i++) {
            int index = packet.readInteger();
            int x = packet.readInteger();
            int y = packet.readInteger();
            float z;
            if (translate) {
                z = replaceZ(packet, x, y);
            } else {
                z = Float.parseFloat(packet.readString());
            }
            synchronized (lock) {
                entityMap.put(index, new EntityLocation(index, x, y, z));
            }
            packet.skip("ii");

            int readIndex = packet.getReadIndex();
            String status = packet.readString();
            if (!translate) continue;

            String[] parts = status.split("/");
            for (int j = 0; j < parts.length; j++) {
                String[] split = parts[j].split("\\s+");
                if (split[0].equals("mv") && split.length > 1) {
                    String[] tileSplit = split[1].split(",");
                    int mvX = Integer.parseInt(tileSplit[0]);
                    int mvY = Integer.parseInt(tileSplit[1]);
                    float mvZ = Float.parseFloat(tileSplit[2]);
                    tileSplit[2] = Float.toString(translateZ(mvX, mvY, mvZ));
                    split[1] = String.join(",", tileSplit);
                }
                parts[j] = String.join(" ", split);
            }
            status = String.join("/", parts);
            packet.replaceString(readIndex, status);
            packet.setReadIndex(readIndex);
            packet.readString();
        }
    }

    static class EntityLocation {
        public int identifier;
        public int x, y;
        public float z;
        public EntityLocation(int identifier, int x, int y, float z) {
            this.identifier = identifier;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    static class HeightmapDiff {
        public int x, y;
        public short value;
        public HeightmapDiff(int x, int y, short value) {
            this.x = x;
            this.y = y;
            this.value = value;
        }
    }
}
