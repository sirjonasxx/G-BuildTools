package extension;

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
import room.RoomFurniState;
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
        Version =  "1.0",
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


    public CheckBox st_allstacktile_cbx;
    public CheckBox ift_pizza_cbx;


    private final static int RATELIMIT = 525;
    private final static int FAST_RATELIMIT = 15; // furni movement, stacktile update
    private PacketInfoSupport packetInfoSupport = null;
    private FurniDataTools furniDataTools = null;

    private RoomFurniState roomFurniState = null;


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


    // stacktile tools
    private final LinkedList<HFloorItem> delayedStacktileUpdates = new LinkedList<>();
    private volatile int stacktileheight = -1;


    public static void main(String[] args) {
        ExtensionFormLauncher.trigger(GBuildTools.class, args);
    }

    @Override
    public ExtensionForm launchForm(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("gbuildtools.fxml"));
        Parent root = loader.load();

        stage.setTitle("G-BuildTools 1.0");
        stage.setScene(new Scene(root));
        stage.getScene().getStylesheets().add(GEarthController.class.getResource("/gearth/ui/bootstrap3.css").toExternalForm());
        stage.getIcons().add(new Image(Main.class.getResourceAsStream("G-EarthLogoSmaller.png")));
        stage.setHeight(283);
        stage.setWidth(544);

        stage.setResizable(false);

        return loader.getController();
    }


    private boolean buildToolsEnabled() {
        return enable_gbuildtools.isSelected();
    }
    public boolean furniDataReady() {
        return furniDataTools != null && furniDataTools.isReady();
    }
    private HFloorItem stackTileLarge() {
        if (!roomFurniState.inRoom() || !furniDataReady()) return null;
        List<HFloorItem> items = roomFurniState.getItemsFromType(furniDataTools, "tile_stackmagic2");
        if (items.size() == 0) return null;
        return items.get(0);
    }


    public void updateUI() {
        Platform.runLater(() -> {

            room_found_lbl.setText(roomFurniState.inRoom() ? "Room found" : "No room found");
            room_found_lbl.setTextFill(roomFurniState.inRoom() ? Paint.valueOf("Green") : Paint.valueOf("Red"));
            furnidata_lbl.setText(furniDataReady() ? "Furnidata loaded" : "Furnidata not loaded");
            furnidata_lbl.setTextFill(furniDataReady() ? Paint.valueOf("Green") : Paint.valueOf("Red"));
            stack_tile_lbl.setText(stackTileLarge() != null ? "Stack tile found" : "No stack tile found");
            stack_tile_lbl.setTextFill(stackTileLarge() != null ? Paint.valueOf("Green") : Paint.valueOf("Red"));

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



            // other (temporary comment)
            st_allstacktile_cbx.setDisable(!furniDataReady());
            ift_pizza_cbx.setDisable(!furniDataReady());

        });
    }

    @Override
    protected void initExtension() {
        packetInfoSupport = new PacketInfoSupport(this);

        roomFurniState = new RoomFurniState(packetInfoSupport, o -> updateUI());

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


        // Stacktile tools
        new Thread(this::stackTileLoop).start();
        packetInfoSupport.intercept(HMessage.Direction.TOSERVER, "SetCustomStackingHeight", this::setStackHeight);


        roomFurniState.requestRoom(this);


        onConnect((host, i, s1, s2, hClient) -> {
            furniDataTools = new FurniDataTools(host, observable -> {
                updateUI();
                maybeReplaceTBones();
            });
        });
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
            delayedEffectSave.clear();
        }
        synchronized (delayedConditionSave) {
            delayedConditionSave.clear();
        }
        synchronized (delayedTriggerSave) {
            delayedTriggerSave.clear();
        }
        synchronized (delayedStacktileUpdates) {
            delayedStacktileUpdates.clear();
        }

        roomFurniState.reset();
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
        if (!buildToolsEnabled() || !roomFurniState.inRoom()) return;

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

        double height = roomFurniState.getTileHeight(dropInfo.getX(), dropInfo.getY());

        if (override_rotation_cbx.isSelected()) {
            dropInfo.setRotation(override_rotation_spinner.getValue());
        }

        packetInfoSupport.sendToClient("ObjectAdd", -(int)dropInfo.getTempFurniId(),
                0, dropInfo.getX(), dropInfo.getY(), dropInfo.getRotation(), height + "", "0.0", 0, 0, "", -1, 0, 0, "");

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


    // Stack tile tools
    private void stackTileLoop() {
        while (true) {
            HFloorItem stackTile = null;
            synchronized (delayedStacktileUpdates) {
                if (delayedStacktileUpdates.size() > 0) {
                    stackTile = delayedStacktileUpdates.removeFirst();
                }
            }

            if (stackTile != null) {
                packetInfoSupport.sendToServer("SetCustomStackingHeight", stackTile.getId(), stacktileheight);
                Utils.sleep(FAST_RATELIMIT);
            }
            else {
                Utils.sleep(2);
            }
        }
    }
    private void setStackHeight(HMessage hMessage) {
        if (buildToolsEnabled() && st_allstacktile_cbx.isSelected() && furniDataReady() && roomFurniState.inRoom()) {
            List<HFloorItem> allStackTiles = new ArrayList<>();
            allStackTiles.addAll(roomFurniState.getItemsFromType(furniDataTools, "tile_stackmagic2"));
            allStackTiles.addAll(roomFurniState.getItemsFromType(furniDataTools, "tile_stackmagic1"));
            allStackTiles.addAll(roomFurniState.getItemsFromType(furniDataTools, "tile_stackmagic"));

            if (allStackTiles.size() > 1) {
                hMessage.setBlocked(true);
                HPacket packet = hMessage.getPacket();

                packet.readInteger(); // furni id, irrelevant
                synchronized (delayedStacktileUpdates) {
                    delayedStacktileUpdates.clear();
                    delayedStacktileUpdates.addAll(allStackTiles);
                    stacktileheight = packet.readInteger();
                }
            }
        }
    }


    private void maybeReplaceTBones() {
        boolean wasEnabled = roomFurniState.getTypeIdMapper().size() > 0;
        boolean enabled = buildToolsEnabled() && furniDataReady() && ift_pizza_cbx.isSelected();

        if (wasEnabled != enabled) {
            if (enabled)
                roomFurniState.addTypeIdMapper(furniDataTools, "petfood4", "pizza");
            else
                roomFurniState.removeTypeIdMapper(furniDataTools, "petfood4");

            if (roomFurniState.inRoom()) {
                roomFurniState.heavyReload(this);
            }
        }
    }

    public void toggleAlwaysOnTop(ActionEvent actionEvent) {
        primaryStage.setAlwaysOnTop(always_on_top_cbx.isSelected());
    }

    public void toggleOverrideRotation(ActionEvent actionEvent) {
        updateUI();
    }


    public PacketInfoSupport getPacketInfoSupport() {
        return packetInfoSupport;
    }

    public FurniDataTools getFurniDataTools() {
        return furniDataTools;
    }

    public void enable_tgl(ActionEvent actionEvent) {
        maybeReplaceTBones();
    }

    public void tbones_tgl(ActionEvent actionEvent) {
        maybeReplaceTBones();
    }
}
