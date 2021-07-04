import furnidata.FurniDataTools;
import gearth.Main;
import gearth.extensions.ExtensionForm;
import gearth.extensions.ExtensionFormLauncher;
import gearth.extensions.ExtensionInfo;
import gearth.extensions.extra.tools.PacketInfoSupport;
import gearth.extensions.parsers.HFloorItem;
import gearth.protocol.HMessage;
import gearth.protocol.HPacket;
import gearth.ui.GEarthController;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.paint.Paint;
import javafx.stage.Stage;
import stuff.DropInfo;
import stuff.FloorFurniDropInfo;
import stuff.WallFurniDropInfo;
import stuff.WallFurniInfo;
import utils.Utils;
import utils.Wrapper;

import java.util.*;

// todo heightmap
// todo req heihtmap on ext onconnect if flash
// todo override rotation
// todo wall furni


@ExtensionInfo(
        Title =  "G-BuildTools",
        Description =  "For all your building needs",
        Version =  "0.1",
        Author =  "sirjonasxx"
)
public class GBuildTools extends ExtensionForm {

    public CheckBox always_on_top_cbx;

    // G-BuildTools general elements
    public CheckBox enable_gbuildtools;
    public Label room_found_lbl;
    public Label furnidata_lbl;
    public Label stack_tile_lbl;

    // quickdrop furni
    public CheckBox qd_floor_cbx;
    public CheckBox qd_wall_cbx;
    public CheckBox override_rotation_cbx;
    public Spinner<Integer> override_rotation_spinner;

    // wired duplicator
    public ToggleGroup wired_tgl;
    public RadioButton rd_wired_cond;
    public RadioButton rd_wired_effect;
    public RadioButton rd_wired_trig;
    public RadioButton rd_wired_none;


    private final static int RATELIMIT = 530;
    private PacketInfoSupport packetInfoSupport = null;
    private FurniDataTools furniDataTools = null;

    private volatile int[][] heightmap = null; // 256 * 256
    private volatile List<List<List<HFloorItem>>> furnimap = null;
    private volatile boolean inRoom = false;


    // quickdrop furni
    private final LinkedList<FloorFurniDropInfo> delayedFloorFurniDrop = new LinkedList<>();
    private final LinkedList<WallFurniDropInfo> delayedWallFurniDrop = new LinkedList<>();
    private final Map<Long, DropInfo> awaitDropConfirmation = new HashMap<>();


    // wired duplicator
    private final Wrapper<HPacket> last_condition = new Wrapper<>();
    private final Wrapper<HPacket> last_effect = new Wrapper<>();
    private final Wrapper<HPacket> last_trigger = new Wrapper<>();
    private final LinkedList<Long> delayedConditionSave = new LinkedList<>();
    private final LinkedList<Long> delayedEffectSave = new LinkedList<>();
    private final LinkedList<Long> delayedTriggerSave = new LinkedList<>();
    private final Object wiredLock = new Object();



    public static void main(String[] args) {
        ExtensionFormLauncher.trigger(GBuildTools.class, args);
    }

    @Override
    public ExtensionForm launchForm(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("gbuildtools.fxml"));
        Parent root = loader.load();

        stage.setTitle("G-BuildTools");
        stage.setScene(new Scene(root));
        stage.getScene().getStylesheets().add(GEarthController.class.getResource("/gearth/ui/bootstrap3.css").toExternalForm());
        stage.getIcons().add(new Image(Main.class.getResourceAsStream("G-EarthLogoSmaller.png")));

        stage.setResizable(false);

        return loader.getController();
    }


    private boolean buildToolsEnabled() {
        return enable_gbuildtools.isSelected();
    }

    private boolean roomAvailable() {
        return heightmap != null && furnimap != null && inRoom;
    }

    private boolean furniDataReady() {
        return furniDataTools != null && furniDataTools.isReady();
    }


    private void updateUI() {
        Platform.runLater(() -> {

            room_found_lbl.setText(roomAvailable() ? "Room found" : "No room found");
            room_found_lbl.setTextFill(roomAvailable() ? Paint.valueOf("Green") : Paint.valueOf("Red"));
            furnidata_lbl.setText(furniDataReady() ? "Furnidata loaded" : "Furnidata not loaded");
            furnidata_lbl.setTextFill(furniDataReady() ? Paint.valueOf("Green") : Paint.valueOf("Red"));

            // quickdrop furni
            override_rotation_spinner.setDisable(!override_rotation_cbx.isSelected());


            // wired duplicator
            boolean is_busy;
            synchronized (wiredLock) {
                is_busy = delayedConditionSave.size() != 0 || delayedEffectSave.size() != 0 || delayedTriggerSave.size() != 0;
            }
            rd_wired_cond.setDisable(is_busy || !last_condition.isPresent());
            rd_wired_effect.setDisable(is_busy || !last_effect.isPresent());
            rd_wired_trig.setDisable(is_busy || !last_trigger.isPresent());
            rd_wired_none.setDisable(is_busy);

        });
    }

    @Override
    protected void initExtension() {
        packetInfoSupport = new PacketInfoSupport(this);

        packetInfoSupport.intercept(HMessage.Direction.TOCLIENT, "HeightMap", this::parseHeightmap);
        packetInfoSupport.intercept(HMessage.Direction.TOCLIENT, "HeightMapUpdate", this::heightmapUpdate);
        packetInfoSupport.intercept(HMessage.Direction.TOCLIENT, "Objects", this::parseFloorItems);

        packetInfoSupport.intercept(HMessage.Direction.TOCLIENT, "CloseConnection", m -> reset());
        packetInfoSupport.intercept(HMessage.Direction.TOSERVER, "Quit", m -> reset());
        packetInfoSupport.intercept(HMessage.Direction.TOCLIENT, "RoomReady", m -> reset());


        // quickdrop furni
        new Thread(this::dropFurniLoop).start();
        packetInfoSupport.intercept(HMessage.Direction.TOSERVER, "PlaceObject", this::onFurniPlace);
        packetInfoSupport.intercept(HMessage.Direction.TOCLIENT, "FurniListRemove", this::inventoryFurniRemove); // flash only


        // wired duplicator
        new Thread(() -> wiredSaveLoop(delayedConditionSave, last_condition)).start();
        new Thread(() -> wiredSaveLoop(delayedTriggerSave, last_trigger)).start();
        new Thread(() -> wiredSaveLoop(delayedEffectSave, last_effect)).start();
        packetInfoSupport.intercept(HMessage.Direction.TOSERVER, "UpdateCondition", this::updateWiredCondition);
        packetInfoSupport.intercept(HMessage.Direction.TOSERVER, "UpdateTrigger", this::updateWiredTrigger);
        packetInfoSupport.intercept(HMessage.Direction.TOSERVER, "UpdateAction", this::updateWiredEffect);
        packetInfoSupport.intercept(HMessage.Direction.TOCLIENT, "Open", this::onOpenWired);


        packetInfoSupport.sendToServer("GetHeightMap");



        onConnect((host, i, s1, s2, hClient) -> {
            furniDataTools = new FurniDataTools(host, observable -> updateUI());
        });
    }


    private void parseFloorItems(HMessage hMessage) {
        HFloorItem[] floorItems = HFloorItem.parse(hMessage.getPacket());

        furnimap = new ArrayList<>();
        for (int i = 0; i < 130; i++) {
            furnimap.add(new ArrayList<>());
            for (int j = 0; j < 130; j++) {
                furnimap.get(i).add(new ArrayList<>());
            }
        }

        for (HFloorItem item : floorItems) {
            furnimap.get(item.getTile().getX()).get(item.getTile().getY()).add(item);
        }

        for(List<List<HFloorItem>> column : furnimap) {
            for (List<HFloorItem> floorItemsOnTile : column) {
                floorItemsOnTile.sort(Comparator.comparingDouble(o -> o.getTile().getZ()));
            }
        }

        inRoom = true;
        updateUI();
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

        this.heightmap = heightmap;

        updateUI();
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

    @Override
    protected void onEndConnection() {
        reset();

        furniDataTools = null;
        updateUI();
    }

    private void reset() {
        synchronized (awaitDropConfirmation) {
            awaitDropConfirmation.clear();
        }
        synchronized (delayedFloorFurniDrop) {
            delayedFloorFurniDrop.clear();
        }
        synchronized (delayedEffectSave) {
            delayedFloorFurniDrop.clear();
        }
        synchronized (delayedConditionSave) {
            delayedFloorFurniDrop.clear();
        }
        synchronized (delayedTriggerSave) {
            delayedFloorFurniDrop.clear();
        }
        inRoom = false;

        updateUI();
    }



    // Quickdrop feature
    private void inventoryFurniRemove(HMessage hMessage) {
        HPacket packet = hMessage.getPacket();
        long furniId = packet.readInteger();

        synchronized (awaitDropConfirmation) {
            if (awaitDropConfirmation.containsKey(furniId)) {
                DropInfo dropInfo = awaitDropConfirmation.remove(furniId);

                hMessage.setBlocked(true); //packet was already

                if (furniId < 0) {
                    // floor furni
                    packetInfoSupport.sendToClient("ObjectRemove",
                            -dropInfo.getTempFurniId() + "", false, 0, 0);
                }
                else {
                    // wall furni
                    packetInfoSupport.sendToClient("ItemRemove",
                            dropInfo.getTempFurniId() + "", 0);
                }



                long timestamp = dropInfo.getDropTimeStamp();

                List<Long> toRemove = new ArrayList<>();

                for(long furniId2 : awaitDropConfirmation.keySet()) {
                    // assume this furni drop experienced an error since its not placed yet
                    if (awaitDropConfirmation.get(furniId2).getDropTimeStamp() < timestamp) {
                        toRemove.add(furniId2);
                    }
                }

                for (long id : toRemove) {
                    DropInfo dropInfo2 = awaitDropConfirmation.remove(id);
                    if (id < 0) {
                        packetInfoSupport.sendToClient("ObjectRemove",
                                -dropInfo2.getTempFurniId() + "", false, 0, 0);
                    }
                    else {
                        packetInfoSupport.sendToClient("ItemRemove",
                                dropInfo2.getTempFurniId() + "", 0);
                    }
                }

            }
        }
    }
    private void onFurniPlace(HMessage hMessage) {
        if (!buildToolsEnabled() || !roomAvailable()) return;

        hMessage.setBlocked(true);

        String info = hMessage.getPacket().readString();
        String[] split = info.split(" ");

        long furniId = Long.parseLong(split[0]);

        if (furniId < 0) {
            if (!qd_floor_cbx.isSelected()) return;

            int x = Integer.parseInt(split[1]);
            int y = Integer.parseInt(split[2]);
            int rotation = Integer.parseInt(split[3]);

            // black box temporary furni
            long tempFurniId = Integer.MIN_VALUE - furniId;

            FloorFurniDropInfo dropInfo = new FloorFurniDropInfo(furniId, tempFurniId, rotation, x, y);
            onFloorFurniPlace(dropInfo);
        }
        else {
            if (!qd_wall_cbx.isSelected()) return;

            long tempFurniId = Integer.MAX_VALUE - furniId;

            WallFurniDropInfo dropInfo = new WallFurniDropInfo(info, tempFurniId);
            onWallFurniPlace(dropInfo);
        }

    }
    private void onFloorFurniPlace(FloorFurniDropInfo dropInfo) {
        synchronized (delayedFloorFurniDrop) {
            delayedFloorFurniDrop.add(dropInfo);
        }

        int heightAsInt = heightmap[dropInfo.getX()][dropInfo.getY()];
        double heightAsDouble = ((double)heightAsInt)/256;

        if (override_rotation_cbx.isSelected()) {
            dropInfo.setRotation(override_rotation_spinner.getValue());
        }

        packetInfoSupport.sendToClient("ObjectAdd", -(int)dropInfo.getTempFurniId(),
                0, dropInfo.getX(), dropInfo.getY(), dropInfo.getRotation(), heightAsDouble + "", "0.0", 0, 0, "", -1, 0, 0, "");

        int flashFurniId = (int)dropInfo.getFurniId();
        packetInfoSupport.sendToClient("FurniListRemove", flashFurniId);
    }
    private void onWallFurniPlace(WallFurniDropInfo dropInfo) {
        synchronized (delayedWallFurniDrop) {
            delayedWallFurniDrop.add(dropInfo);
        }

        WallFurniInfo temp = new WallFurniInfo(dropInfo.getTempFurniId(), dropInfo.getPoint(),
                dropInfo.getxOffset(), dropInfo.getyOffset(), dropInfo.isLeft()
        );

        packetInfoSupport.sendToClient("ItemAdd", temp.getFurniId() + "", 4001,
                temp.moveString(), "", -1, 0, 0, "");

        int flashFurniId = (int)dropInfo.getFurniId();
        packetInfoSupport.sendToClient("FurniListRemove", flashFurniId);
    }
    private void dropFurniLoop() {
        while (true) {
            FloorFurniDropInfo delayedFloorDrop = null;
            WallFurniDropInfo delayedWallDrop = null;
            synchronized (delayedFloorFurniDrop) {
                if (delayedFloorFurniDrop.size() > 0) {
                    delayedFloorDrop = delayedFloorFurniDrop.removeFirst();
                }
            }
            synchronized (delayedWallFurniDrop) {
                if (delayedWallFurniDrop.size() > 0) {
                    delayedWallDrop = delayedWallFurniDrop.removeFirst();
                }
            }

            if (delayedFloorDrop != null) {
                String packetString = String.format("%d %d %d %d",
                        delayedFloorDrop.getFurniId(),
                        delayedFloorDrop.getX(),
                        delayedFloorDrop.getY(),
                        delayedFloorDrop.getRotation()
                );
                packetInfoSupport.sendToServer("PlaceObject", packetString);

                delayedFloorDrop.setDropTimeStamp(System.currentTimeMillis());
                synchronized (awaitDropConfirmation) {
                    awaitDropConfirmation.put(delayedFloorDrop.getFurniId(), delayedFloorDrop);
                }

                Utils.sleep(RATELIMIT);
            }
            if (delayedWallDrop != null) {
                String packetString = delayedWallDrop.placeString();
                packetInfoSupport.sendToServer("PlaceObject", packetString);

                delayedWallDrop.setDropTimeStamp(System.currentTimeMillis());
                synchronized (awaitDropConfirmation) {
                    awaitDropConfirmation.put(delayedWallDrop.getFurniId(), delayedWallDrop);
                }

                Utils.sleep(RATELIMIT);
            }

            if (delayedFloorDrop == null && delayedWallDrop == null) {
                Utils.sleep(2);
            }

        }
    }


    // Wired duplicator feature
    private void updateWiredCondition(HMessage hMessage) {
        last_condition.set(hMessage.getPacket());
        updateUI();
    }
    private void updateWiredTrigger(HMessage hMessage) {
        last_trigger.set(hMessage.getPacket());
        updateUI();
    }
    private void updateWiredEffect(HMessage hMessage) {
        last_effect.set(hMessage.getPacket());
        updateUI();
    }
    private void wiredSaveLoop(LinkedList<Long> delayedWiredSave, Wrapper<HPacket> packet) {
        while (true) {
            long wiredId = -1;
            synchronized (wiredLock) {
                if (delayedWiredSave.size() > 0) {
                    wiredId = delayedWiredSave.removeFirst();
                }
            }

            if (wiredId != -1) {
                HPacket saveWired = packet.get();
                saveWired.replaceInt(6, (int)wiredId);

                sendToServer(saveWired);

                updateUI();
                Utils.sleep(RATELIMIT);
            }
            else {
                Utils.sleep(2);
            }
        }

    }
    private void onOpenWired(HMessage hMessage) {
        if (!buildToolsEnabled()) return;

        long furniId = hMessage.getPacket().readInteger();
        if (rd_wired_trig.isSelected()) {
            synchronized (wiredLock) {
                delayedTriggerSave.add(furniId);
            }
        }
        else if (rd_wired_cond.isSelected()) {
            synchronized (wiredLock) {
                delayedConditionSave.add(furniId);
            }
        }
        else if (rd_wired_effect.isSelected()) {
            synchronized (wiredLock) {
                delayedEffectSave.add(furniId);
            }
        }

        if (!rd_wired_none.isSelected()) {
            hMessage.setBlocked(true);
            updateUI();
        }
    }


    public void toggleAlwaysOnTop(ActionEvent actionEvent) {
        primaryStage.setAlwaysOnTop(always_on_top_cbx.isSelected());
    }

    public void toggleOverrideRotation(ActionEvent actionEvent) {
        updateUI();
    }
}
