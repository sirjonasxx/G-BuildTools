package furnidata;

import javafx.beans.InvalidationListener;
import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class FurniDataTools {

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
        });

        JSONArray wallJson = object.getJSONObject("wallitemtypes").getJSONArray("furnitype");
        wallJson.forEach(o -> {
            JSONObject item = (JSONObject)o;
            nameToTypeidWall.put(item.getString("classname"), item.getInt("id"));
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
}
