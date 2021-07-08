package furnidata;

import javafx.beans.InvalidationListener;
import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class FurniDataTools {

    private static final Set<String> UNSTACKABLE_FURNI = new HashSet<>(Arrays.asList("fball_gate", "es_tile", "bb_patch1",
            "es_skating_ice", "val11_floor", "easter11_grasspatch", "queue_tile1*0", "queue_tile1*1", "queue_tile1*2",
            "queue_tile1*3", "queue_tile1*4", "queue_tile1*5", "queue_tile1*6", "queue_tile1*7", "queue_tile1*8",
            "queue_tile1*9", "hc_rllr", "sf_roller", "room_info15_roller", "hblooza14_track", "hblooza14_track_crr",
            "hblooza14_track_crl", "easter_c20_rapids", "hween12_track_crl", "hween12_track_crl", "hween12_track",
            "bw_water_2", "bw_water_1", "hween_c15_sdwater", "val15_water", "jungle_c16_watertile", "jungle_c16_watertrap",
            "thai_c21_crystalwater", "bw_nt_water_2", "hween10_pond", "sunsetcafe_c20_shallow", "val13_water",
            "hole", "hole2", "hole1x1", "hole3", "hole4", "hole1x1test", "pet_breeding_bear", "pet_breeding_terrier",
            "pet_breeding_dog", "pet_breeding_pig", "pet_breeding_cat"));
//    hween14_rare2 ??
//    pirate_sandtrap


    private static Map<String, String> codeToDomainMap = new HashMap<>();
    static {
        codeToDomainMap.put("br", ".com.br");
        codeToDomainMap.put("de", ".de");
        codeToDomainMap.put("es", ".es");
        codeToDomainMap.put("fi", ".fi");
        codeToDomainMap.put("fr", ".fr");
        codeToDomainMap.put("it", ".it");
        codeToDomainMap.put("nl", ".nl");
        codeToDomainMap.put("tr", ".com.tr");
        codeToDomainMap.put("us", ".com");
    }

    private String countryCode;
    private volatile boolean isReady = false;

    private Map<String, Integer> nameToTypeidFloor = new HashMap<>();
    private Map<String, Integer> nameToTypeidWall = new HashMap<>();
    private Map<Integer, String> typeIdToNameFloor = new HashMap<>();
    private Map<Integer, String> typeIdToNameWall = new HashMap<>();

    public FurniDataTools(String host, InvalidationListener onLoadListener) {
        countryCode = host.substring(5, 7);

        new Thread(() -> {
            try {
                fetch(onLoadListener);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();

    }

    private void fetch(InvalidationListener onLoad) throws IOException {
        JSONObject object = new JSONObject(IOUtils.toString(
                new URL(furnidataUrl()).openStream(), StandardCharsets.UTF_8));


        JSONArray floorJson = object.getJSONObject("roomitemtypes").getJSONArray("furnitype");
        floorJson.forEach(o -> {
            JSONObject item = (JSONObject)o;
            nameToTypeidFloor.put(item.getString("classname"), item.getInt("id"));
            typeIdToNameFloor.put(item.getInt("id"), item.getString("classname"));
        });

        JSONArray wallJson = object.getJSONObject("wallitemtypes").getJSONArray("furnitype");
        wallJson.forEach(o -> {
            JSONObject item = (JSONObject)o;
            nameToTypeidWall.put(item.getString("classname"), item.getInt("id"));
            typeIdToNameWall.put(item.getInt("id"), item.getString("classname"));
        });

        isReady = true;
        onLoad.invalidated(null);
    }

    private String furnidataUrl() {
        if (countryCode.equals("s2")) {
            return "https://sandbox.habbo.com/gamedata/furnidata_json/1";
        }

        return String.format("https://www.habbo%s/gamedata/furnidata_json/1", codeToDomainMap.get(countryCode));
    }

    public boolean isReady() {
        return isReady;
    }

    public Integer getFloorTypeId(String furniName) {
        return nameToTypeidFloor.get(furniName);
    }
    public Integer getWallTypeId(String furniName) {
        return nameToTypeidWall.get(furniName);
    }
    public String getFloorItemName(int typeId) { return typeIdToNameFloor.get(typeId); }
    public String getWallItemName(int typeId) { return typeIdToNameWall.get(typeId); }

    public boolean isStackable(String furniName) {
        return !UNSTACKABLE_FURNI.contains(furniName);
    }
}
