package extension;

import furnidata.FurniDataTools;
import gearth.Main;
import gearth.extensions.ExtensionForm;
import gearth.extensions.ExtensionFormLauncher;
import gearth.extensions.ExtensionInfo;
import gearth.extensions.extra.tools.PacketInfoSupport;
import gearth.extensions.parsers.HFloorItem;
import gearth.extensions.parsers.HPoint;
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
import room.FloorState;
import stuff.*;
import utils.Utils;
import utils.Wrapper;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

@ExtensionInfo(
        Title =  "G-BuildTools",
        Description =  "For all your building needs",
        Version =  "1.0.1",
        Author =  "sirjonasxx"
)
public class GBuildTools extends ExtensionForm {

    public CheckBox always_on_top_cbx;
    public Hyperlink readmeLink;

    public Slider ratelimiter;
    public Spinner<Integer> packet_spam_spinner;
    public CheckBox cbx_spam;


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

    // stack tools
    public CheckBox st_allstacktile_cbx;

    // invisible furni tools
    public CheckBox ift_pizza_cbx;

    // furni mover
    public CheckBox fm_visualhelp_lbl;

    public RadioButton rd_fm_mode_tile;
    public RadioButton rd_fm_mode_rect;
    public RadioButton rd_fm_mode_auto;
    public CheckBox fm_cbx_inversedir;

    public CheckBox fm_cbx_usestacktile;
    public RadioButton rd_fm_stack_offset;
    public RadioButton rd_fm_stack_flatten;
    public Spinner<Double> height_offset_spinner;
    public Spinner<Double> flatten_height_spinner;
    public Label fm_grow_lbl;
    public Spinner<Double> grow_factor_spinner;

    public CheckBox fm_cbx_rotatefurni;
    public CheckBox fm_cbx_wiredsafety;
    public CheckBox fm_cbx_visualhelp;



    // poster mover
    public ToggleGroup pm_loc_tgl;
    public ToggleGroup pm_loc_offset;
    public Label pm_furni_lbl;

    public TextField pm_location_txt;
    public RadioButton pm_rd_left;
    public RadioButton pm_rd_right;
    public Button pm_loc_up_btn;
    public Button pm_loc_right_btn;
    public Button pm_loc_down_btn;
    public Button pm_loc_left_btn;
    public Button pm_offset_up_btn;
    public Button pm_offset_right_btn;
    public Button pm_offset_down_btn;
    public Button pm_offset_left_btn;

    private volatile int RATELIMIT = 526;
    private volatile int STACKTILE_RATELIMIT = 16;
    private volatile int MOVEFURNI_RATELIMIT = 30;

    private PacketInfoSupport packetInfoSupport = null;
    private FurniDataTools furniDataTools = null;

    private FloorState floorState = null;


    // quickdrop furni
    private final LinkedList<FloorFurniDropInfo> delayedFloorFurniDrop = new LinkedList<>();
    private final LinkedList<WallFurniDropInfo> delayedWallFurniDrop = new LinkedList<>();
    private final Map<Long, DropInfo> awaitDropConfirmation = new HashMap<>();


    // wired duplicator
    private final Wrapper<HPacket> last_condition = new Wrapper<>();
    private final Wrapper<HPacket> last_effect = new Wrapper<>();
    private final Wrapper<HPacket> last_trigger = new Wrapper<>();
    private final LinkedList<Integer> delayedConditionSave = new LinkedList<>();
    private final LinkedList<Integer> delayedEffectSave = new LinkedList<>();
    private final LinkedList<Integer> delayedTriggerSave = new LinkedList<>();
    private final Object wiredLock = new Object();


    // stacktile tools
    private final LinkedList<HFloorItem> delayedStacktileUpdates = new LinkedList<>();
    private volatile int stacktileheight = -1;

    // invis furni
    private volatile long latestTboneReq = -1;



    public void onRotatedFurniClick(ActionEvent actionEvent) {
        fm_cbx_rotatefurni.setSelected(false);
        fm_cbx_rotatefurni.setDisable(true);
        furniMoverSendInfo("Couldn't implement this feature because Sulake didn't implement rotations correctly");
    }

    public void reload_inv(ActionEvent actionEvent) {
        packetInfoSupport.sendToServer("RequestFurniInventory");
    }


    // furnimover
    private enum MoveFurniState {
        NONE,
        MOVING,
        UNDOING
    }

    private enum SelectionState {
        NONE,
        AWAIT_SELECTION,
        AWAIT_SELECTION2,
        AWAIT_MOVE
    }

    private enum ExcludeFurniState {
        NONE,
        EXCLUDE,
        INCLUDE
    }
    private final Object furniMoveLock = new Object();

    private LinkedList<LinkedList<FloorFurniMovement>> moveHistory = new LinkedList<>();
    private LinkedList<LinkedList<FloorFurniMovement>> workList = new LinkedList<>();

    private volatile MoveFurniState moveFurniState = MoveFurniState.NONE;
    private volatile SelectionState selectionState = SelectionState.NONE;

    private volatile HPoint sourcePosition = null;
    private volatile HPoint sourceEndPosition = null;
    private List<HFloorItem> selection = new ArrayList<>();
    private volatile HPoint latestStackMove = null;

    private volatile Set<String> excludedFurnis = new HashSet<>();
    private volatile ExcludeFurniState excludeFurniState = ExcludeFurniState.NONE;


    // poster mover
    private volatile WallFurniInfo latestWallFurniMovement = null;
    private volatile boolean updateLocationStringUI = false;


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

        stage.setResizable(false);

        return loader.getController();
    }

    @Override
    protected void onShow() {
        primaryStage.sizeToScene();
    }

    private boolean buildToolsEnabled() {
        return enable_gbuildtools.isSelected();
    }
    public boolean furniDataReady() {
        return furniDataTools != null && furniDataTools.isReady();
    }
    private HFloorItem stackTileLarge() {
        if (!floorState.inRoom() || !furniDataReady()) return null;
        List<HFloorItem> items = floorState.getItemsFromType(furniDataTools, "tile_stackmagic2");
        if (items.size() == 0) return null;
        return items.get(0);
    }


    public void updateUI() {
        Platform.runLater(() -> {
            boolean stackLAvailable = stackTileLarge() != null;

            room_found_lbl.setText(floorState.inRoom() ? "Room found" : "No room found");
            room_found_lbl.setTextFill(floorState.inRoom() ? Paint.valueOf("Green") : Paint.valueOf("Red"));
            furnidata_lbl.setText(furniDataReady() ? "Furnidata loaded" : "Furnidata not loaded");
            furnidata_lbl.setTextFill(furniDataReady() ? Paint.valueOf("Green") : Paint.valueOf("Red"));
            stack_tile_lbl.setText(stackLAvailable ? "Stack tile found" : "No stack tile found");
            stack_tile_lbl.setTextFill(stackLAvailable ? Paint.valueOf("Green") : Paint.valueOf("Red"));

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


            // stack tools
            st_allstacktile_cbx.setDisable(!furniDataReady());


            // invisible furni tools
            ift_pizza_cbx.setDisable(!furniDataReady() || latestTboneReq > System.currentTimeMillis() - RATELIMIT);


            // furni mover
            fm_cbx_inversedir.setDisable(!rd_fm_mode_rect.isSelected());
            fm_cbx_usestacktile.setDisable(!stackLAvailable);
            rd_fm_stack_offset.setDisable(!stackLAvailable || !fm_cbx_usestacktile.isSelected());
            rd_fm_stack_flatten.setDisable(!stackLAvailable || !fm_cbx_usestacktile.isSelected());
            fm_grow_lbl.setDisable(!stackLAvailable || !fm_cbx_usestacktile.isSelected());
            height_offset_spinner.setDisable(!stackLAvailable || !fm_cbx_usestacktile.isSelected() || !rd_fm_stack_offset.isSelected());
            flatten_height_spinner.setDisable(!stackLAvailable || !fm_cbx_usestacktile.isSelected() || !rd_fm_stack_flatten.isSelected());
            grow_factor_spinner.setDisable(!stackLAvailable || !fm_cbx_usestacktile.isSelected());

            rd_fm_mode_rect.setDisable(selectionState == SelectionState.AWAIT_SELECTION2 || selectionState == SelectionState.AWAIT_MOVE);
            rd_fm_mode_auto.setDisable(selectionState == SelectionState.AWAIT_SELECTION2 || selectionState == SelectionState.AWAIT_MOVE);
            rd_fm_mode_tile.setDisable(selectionState == SelectionState.AWAIT_SELECTION2 || selectionState == SelectionState.AWAIT_MOVE);


            // poster mover
            pm_location_txt.setDisable(latestWallFurniMovement == null || !buildToolsEnabled());
            pm_rd_left.setDisable(latestWallFurniMovement == null || !buildToolsEnabled());
            pm_rd_right.setDisable(latestWallFurniMovement == null || !buildToolsEnabled());
            pm_loc_up_btn.setDisable(latestWallFurniMovement == null || !buildToolsEnabled());
            pm_loc_right_btn.setDisable(latestWallFurniMovement == null || !buildToolsEnabled());
            pm_loc_down_btn.setDisable(latestWallFurniMovement == null || !buildToolsEnabled());
            pm_loc_left_btn.setDisable(latestWallFurniMovement == null || !buildToolsEnabled());
            pm_offset_up_btn.setDisable(latestWallFurniMovement == null || !buildToolsEnabled());
            pm_offset_right_btn.setDisable(latestWallFurniMovement == null || !buildToolsEnabled());
            pm_offset_down_btn.setDisable(latestWallFurniMovement == null || !buildToolsEnabled());
            pm_offset_left_btn.setDisable(latestWallFurniMovement == null || !buildToolsEnabled());

            String poster_lbl = latestWallFurniMovement == null ? "None" : latestWallFurniMovement.getFurniId()+"";
            pm_furni_lbl.setText(poster_lbl);

            if (updateLocationStringUI) {
                updateLocationStringUI = false;
                pm_location_txt.setText(
                        latestWallFurniMovement == null ? "" :
                        latestWallFurniMovement.moveString()
                );
            }

            if (latestWallFurniMovement != null) {
                pm_rd_left.setSelected(latestWallFurniMovement.isLeft());
                pm_rd_right.setSelected(!latestWallFurniMovement.isLeft());
            }

        });
    }

    @Override
    protected void initExtension() {
        moveHistory.add(new LinkedList<>());
        readmeLink.setTooltip(new Tooltip("https://github.com/sirjonasxx/G-BuildTools/blob/master/README.md"));
        readmeLink.setOnAction((ActionEvent event) -> {
            Hyperlink h = (Hyperlink) event.getTarget();
            String s = h.getTooltip().getText();
            getHostServices().showDocument(s);
            event.consume();
        });

        // javafx spinner updates bugfix
        Spinner[] spinners = {height_offset_spinner, flatten_height_spinner, override_rotation_spinner, grow_factor_spinner, packet_spam_spinner};
        for(Spinner spinner : spinners) {
            spinner.focusedProperty().addListener((observable, oldValue, newValue) -> {
                if (!newValue) spinner.increment(0); // won't change value, but will commit editor
            });
        }
        ratelimiter.valueProperty().addListener((observable, oldValue, newValue) -> {
            int val = newValue.intValue();

            RATELIMIT = 526 + val;
            STACKTILE_RATELIMIT = 16 + val;
            MOVEFURNI_RATELIMIT = 30 + val;
        });


        packetInfoSupport = new PacketInfoSupport(this);

        floorState = new FloorState(packetInfoSupport, o -> updateUI());

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


        // furni mover
        new Thread(this::furniMoveLoop).start();
        packetInfoSupport.intercept(HMessage.Direction.TOSERVER, "Chat", this::onUserChat);
        packetInfoSupport.intercept(HMessage.Direction.TOSERVER, "MoveAvatar", this::onTileClick);
        packetInfoSupport.intercept(HMessage.Direction.TOSERVER, "MoveObject", this::onMoveFurni);


        // poster mover
        // {out:MoveWallItem}{i:10616766}{s:":w=0,5 l=4,29 l"}
        packetInfoSupport.intercept(HMessage.Direction.TOSERVER, "MoveWallItem", this::onMoveWallItem);
        packetInfoSupport.intercept(HMessage.Direction.TOCLIENT, "ItemUpdate", this::posterMoved);

        floorState.requestRoom(this);


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
        synchronized (furniMoveLock) {
            moveHistory.clear();
            moveHistory.add(new LinkedList<>());
            workList.clear();
            moveFurniState = MoveFurniState.NONE;
            selectionState = SelectionState.NONE;
            selection.clear();
            sourcePosition = null;
            sourceEndPosition = null;
            excludeFurniState = ExcludeFurniState.NONE;
        }
        latestWallFurniMovement = null;
        updateLocationStringUI = true;
        floorState.reset();
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
        if (!buildToolsEnabled() || !floorState.inRoom()) return;

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

        double height = floorState.getTileHeight(dropInfo.getX(), dropInfo.getY());

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

        WallFurniInfo temp = new WallFurniInfo(dropInfo.getTempFurniId(), dropInfo.getX(), dropInfo.getY(),
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
    private void wiredSaveLoop(LinkedList<Integer> delayedWiredSave, Wrapper<HPacket> packet) {
        while (true) {
            int wiredId = -1;
            synchronized (wiredLock) {
                if (delayedWiredSave.size() > 0) {
                    wiredId = delayedWiredSave.removeFirst();
                }
            }

            if (wiredId != -1) {
                HPacket saveWired = packet.get();
                saveWired.replaceInt(6, wiredId);

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

        int furniId = hMessage.getPacket().readInteger();
        String className =
                (!furniDataReady() || !floorState.inRoom() || floorState.furniFromId(furniId) == null) ? null :
                        furniDataTools.getFloorItemName(floorState.furniFromId(furniId).getTypeId());

        if (rd_wired_trig.isSelected()) {
            synchronized (wiredLock) {
                if (className == null || className.startsWith("wf_trg_")) {
                    hMessage.setBlocked(true);
                    delayedTriggerSave.add(furniId);
                }
            }
        }
        else if (rd_wired_cond.isSelected()) {
            synchronized (wiredLock) {
                if (className == null || className.startsWith("wf_cnd_")) {
                    hMessage.setBlocked(true);
                    delayedConditionSave.add(furniId);
                }
            }
        }
        else if (rd_wired_effect.isSelected()) {
            synchronized (wiredLock) {
                if (className == null || className.startsWith("wf_act_")) {
                    hMessage.setBlocked(true);
                    delayedEffectSave.add(furniId);
                }
            }
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
                Utils.sleep(STACKTILE_RATELIMIT);
            }
            else {
                Utils.sleep(2);
            }
        }
    }
    private void setStackHeight(HMessage hMessage) {
        if (buildToolsEnabled() && st_allstacktile_cbx.isSelected() && furniDataReady() && floorState.inRoom()) {
            List<HFloorItem> allStackTiles = new ArrayList<>();
            allStackTiles.addAll(floorState.getItemsFromType(furniDataTools, "tile_stackmagic2"));
            allStackTiles.addAll(floorState.getItemsFromType(furniDataTools, "tile_stackmagic1"));
            allStackTiles.addAll(floorState.getItemsFromType(furniDataTools, "tile_stackmagic"));

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


    private void moveFurni(int furniId, int delay, int x, int y, int rot, int expected_z) {
        for (int i = 0; i < getBurstValue(); i++) {
            packetInfoSupport.sendToServer("MoveObject", furniId, x, y, rot);
            Utils.sleep(delay);
        }
    }
    private void setStackHeight(int stackTileId, int delay, int z) {
        for (int i = 0; i < getBurstValue(); i++) {
            packetInfoSupport.sendToServer("SetCustomStackingHeight", stackTileId, z);
            Utils.sleep(delay);
        }
    }

    private HPoint stackTileBestMoveLocation(int x, int y) {
        char tileHeight = floorState.floorHeight(x, y);

        if (floorState.inRoom() && tileHeight != 'x') {
            int[][] offsets = {{0, 0}, {-1, 0}, {0, -1}, {-1, -1}};
            return Arrays.stream(offsets).filter(
                    o1 -> Arrays.stream(offsets).map(o2 -> floorState.floorHeight(x + o1[0] - o2[0], y + o1[1] - o2[1])).distinct().count() == 1)
                    .findFirst().map(l -> new HPoint(l[0], l[1])).orElse(new HPoint(-1, -1));


//            int[][] potentialOffsets = {{0, 0}, {-1, 0}, {0, -1}, {-1, -1}};
//            for (int[] offsets : potentialOffsets) {
//                int xOffset = offsets[0];
//                int yOffset = offsets[1];
//
//                boolean valid = true;
//                for (int x2 = x + xOffset; x2 < x + xOffset + 2; x2++) {
//                    for (int y2 = y + xOffset; y2 < y + yOffset + 2; y2++) {
//                        if (floorState.floorHeight(x2, y2) != tileHeight) {
//                            valid = false;
//                        }
//                    }
//                }
//                if (valid) return new HPoint(x + xOffset, y + yOffset);
//            }

        }

        return new HPoint(-1, -1);
    }
    // furnimover
    private void furniMoverSendInfo(String text) {
        if (fm_cbx_visualhelp.isSelected()) {
            packetInfoSupport.sendToClient("Whisper", -1, text, 0, 30, 0, -1);
        }
    }
    private void furniMoveLoop() {
        while (true) {

            FloorFurniMovement movement = null;
            synchronized (furniMoveLock) {
                if (moveFurniState == MoveFurniState.MOVING) {
                    while (workList.size() > 0 && movement == null) {
                        LinkedList<FloorFurniMovement> task = workList.getFirst();
                        if (task.size() == 0) {
                            workList.removeFirst();
                            moveHistory.add(new LinkedList<>());
                            if (!rd_fm_mode_auto.isSelected()) {
                                furniMoverSendInfo("Finished movements");
                            }
                            latestStackMove = null;
                        }
                        else {
                            movement = task.removeFirst();
                            moveHistory.getLast().add(movement);
                        }
                    }
                    if (movement == null) {
                        moveFurniState = MoveFurniState.NONE;
                    }
                }
                else if (moveFurniState == MoveFurniState.UNDOING) {
                    while (moveFurniState == MoveFurniState.UNDOING && movement == null) {
                        LinkedList<FloorFurniMovement> task = moveHistory.getLast();
                        if (task.size() == 0) {
                            furniMoverSendInfo("Undone last furni movements");
                            moveFurniState = MoveFurniState.NONE;
                            latestStackMove = null;
                        }
                        else {
                            movement = task.removeFirst();
                        }
                    }
                }
            }

            if (movement != null) {
                int furniId = movement.getFurniId();
                int x, y, z, rot;
                if (moveFurniState == MoveFurniState.UNDOING) {
                    x = movement.getOldX();
                    y = movement.getOldY();
                    z = movement.getOldZ();
                    rot = movement.getOldRot();
                }
                else {
                    x = movement.getNewX();
                    y = movement.getNewY();
                    z = movement.getNewZ();
                    rot = movement.getNewRot();
                }

//                HFloorItem stackTile = stackTileLarge();
                if (movement.useStacktileId() != -1) {
                    HPoint stackLoc = stackTileBestMoveLocation(x, y);

                    int stacktileId = movement.useStacktileId();

                    if (latestStackMove == null || latestStackMove.getX() != stackLoc.getX() || latestStackMove.getY() != stackLoc.getY()) {
                        moveFurni(stacktileId, MOVEFURNI_RATELIMIT/2, stackLoc.getX(), stackLoc.getY(), 0, -1);
                        latestStackMove = null;
                    }

                    if (latestStackMove == null || !latestStackMove.equals(new HPoint(stackLoc.getX(), stackLoc.getY(), z))) {
                        setStackHeight(stacktileId, MOVEFURNI_RATELIMIT/2, z);
                    }
                    latestStackMove = new HPoint(stackLoc.getX(), stackLoc.getY(), z);
                }

                moveFurni(furniId, MOVEFURNI_RATELIMIT, x, y, rot, z);
            }
            else {
                Utils.sleep(2);
            }
        }
    }
    private void onUserChat(HMessage hMessage) {
        if (!buildToolsEnabled()) return;
        String message = hMessage.getPacket().readString();

        switch (message) {
            case ":move":
            case ":m":
                hMessage.setBlocked(true);
                if (!furniDataReady()) {
                    furniMoverSendInfo("Wait until furnidata is parsed");
                    return;
                }

                if (rd_fm_mode_rect.isSelected()) {
                    furniMoverSendInfo("Select the start of the rectangle");
                } else if (rd_fm_mode_auto.isSelected()) {
                    furniMoverSendInfo("You can now start moving");
                } else if (rd_fm_mode_tile.isSelected()) {
                    furniMoverSendInfo("Select the source tile");
                }

                selectionState = SelectionState.AWAIT_SELECTION;
                break;
            case ":abort":
            case ":a":
                excludeFurniState = ExcludeFurniState.NONE;
                hMessage.setBlocked(true);
                synchronized (furniMoveLock) {
                    if (moveFurniState == MoveFurniState.UNDOING) {
                        moveHistory.add(new LinkedList<>());
                    } else if (moveFurniState == MoveFurniState.MOVING) {
                        workList.clear();
                        moveHistory.add(new LinkedList<>());
                    }
                    selectionState = SelectionState.NONE;
                    moveFurniState = MoveFurniState.NONE;
                    furniMoverSendInfo("Succesfully aborted");
                }
                break;
            case ":undo":
            case ":u":
                hMessage.setBlocked(true);
                synchronized (furniMoveLock) {
                    if (moveFurniState == MoveFurniState.NONE) {
                        if (moveHistory.size() > 1) {
                            moveHistory.removeLast();
                            furniMoverSendInfo("Undoing latest movements");
                            moveFurniState = MoveFurniState.UNDOING;
                        }
                        else {
                            furniMoverSendInfo("Nothing to undo");
                        }
                    }
                    else {
                        furniMoverSendInfo("Abort what you're doing first!");
                    }
                }
                break;
            case ":exclude":
            case ":e":
                hMessage.setBlocked(true);
                if (!furniDataReady() || !floorState.inRoom() ) {
                    furniMoverSendInfo("Wait until furnidata is parsed and room is loaded");
                    return;
                }
                excludeFurniState = ExcludeFurniState.EXCLUDE;
                furniMoverSendInfo("SHIFT+CLICK the furni you want to exclude");
                break;
            case ":include":
            case ":i":
                hMessage.setBlocked(true);
                if (!furniDataReady() || !floorState.inRoom() ) {
                    furniMoverSendInfo("Wait until furnidata is parsed and room is loaded");
                    return;
                }
                excludeFurniState = ExcludeFurniState.INCLUDE;
                furniMoverSendInfo("SHIFT+CLICK the furni you want to include");
                break;
            case ":reset":
            case ":r":
                hMessage.setBlocked(true);
                excludedFurnis.clear();
                furniMoverSendInfo("Cleared list of excluded furniture");
                excludeFurniState = ExcludeFurniState.NONE;
                break;
        }

        updateUI();
    }
    private void enqueueSelection(HPoint target) {
        if (moveFurniState == MoveFurniState.UNDOING) {
            furniMoverSendInfo("Can not move while undoing previous movements");
            return;
        }

        Set<Integer> stacktiles = new HashSet<>();
        if (furniDataReady()) {
            stacktiles.add(furniDataTools.getFloorTypeId("tile_stackmagic"));
            stacktiles.add(furniDataTools.getFloorTypeId("tile_stackmagic1"));
            stacktiles.add(furniDataTools.getFloorTypeId("tile_stackmagic2"));
        }

        // convert
        int xOffset = target.getX() - sourcePosition.getX();
        int yOffset = target.getY() - sourcePosition.getY();

        HFloorItem stackTile = stackTileLarge();
        int stackTileId = stackTile == null ? -1 : stackTile.getId();
        boolean isStackTileUsed = false;

        Optional<Integer> optMinZ = selection.stream().map(hFloorItem -> (int)(hFloorItem.getTile().getZ() * 100)).min(Integer::compareTo);
        int lowestZ = optMinZ.orElse(0);

        List<FloorFurniMovement> floorFurniMovements = new ArrayList<>();
        for (HFloorItem floorItem : selection) {
            if (stacktiles.contains(floorItem.getTypeId())) {
                continue;
            }

            String classname = furniDataTools.getFloorItemName(floorItem.getTypeId());
            if (excludedFurnis.contains(classname)) {
                continue;
            }

            int x = floorItem.getTile().getX();
            int y = floorItem.getTile().getY();
            int rot = floorItem.getFacing().ordinal();

            // dont implement new rotation


            int oldZ = (int)(floorItem.getTile().getZ() * 100);
            int newZ = oldZ;
            if (fm_cbx_usestacktile.isSelected()) {
                if (rd_fm_stack_flatten.isSelected()) {
                    newZ = (int)(flatten_height_spinner.getValue() * 100);
                }
                if (rd_fm_stack_offset.isSelected()) {
                    double growFactor = grow_factor_spinner.getValue();
                    int oldZgrown = (int)((oldZ - lowestZ) * growFactor) + lowestZ;

                    newZ = ((int)(height_offset_spinner.getValue() * 100)) + oldZgrown;
                }
            }


            boolean useStackTile = fm_cbx_usestacktile.isSelected()
                    && stackTileLarge() != null &&
                    furniDataTools.isStackable(classname);
            if (useStackTile) {
                isStackTileUsed = true;
            }

            int newX = x+xOffset;
            int newY = y+yOffset;
            if (fm_cbx_inversedir.isSelected()) {
                int xdiff = x - sourcePosition.getX();
                int ydiff = y - sourcePosition.getY();

                newX += (ydiff-xdiff);
                newY += (xdiff-ydiff);
            }

            FloorFurniMovement movement = new FloorFurniMovement(floorItem.getTypeId(), floorItem.getId(),
                    x, y, rot, newX, newY, rot, oldZ, newZ, useStackTile ? stackTileId : -1
            );
            floorFurniMovements.add(movement);
        }

        // decide order
        floorFurniMovements.sort((o1, o2) -> {
            int maybe1 = Boolean.compare(o1.useStacktileId() != -1, o2.useStacktileId() != -1);
            if (maybe1 == 0) {
                int maybe2 = Integer.compare(o1.getOldY(), o2.getOldY());
                if (maybe2 == 0) {
                    int maybe3 = Integer.compare(o1.getOldX(), o2.getOldX());
                    if (maybe3 == 0) {
                        // todo wired ordering?
                        return Integer.compare(o1.getOldZ(), o2.getOldZ());
                    }
                    return maybe3;
                }
                return maybe2;
            }
            return maybe1;
        });

        if (fm_cbx_wiredsafety.isSelected()) {
            int prevX = -1;
            int prevY = -1;

            LinkedList<FloorFurniMovement> effects = new LinkedList<>();
            LinkedList<FloorFurniMovement> conditions = new LinkedList<>();
            LinkedList<FloorFurniMovement> triggers = new LinkedList<>();

            List<FloorFurniMovement> wireds = new ArrayList<>();
            for (FloorFurniMovement movement : floorFurniMovements) {
                if (movement.getOldX() != prevX || movement.getOldY() != prevY) {

                    Map<FloorFurniMovement, Integer> newFurniId = new HashMap<>();
                    for (FloorFurniMovement floorFurniMovement : wireds) {
                        FloorFurniMovement curr = effects.size() > 0 ? effects.removeFirst() :
                                conditions.size() > 0 ? conditions.removeFirst() : triggers.removeFirst();
                        newFurniId.put(floorFurniMovement, curr.getFurniId());
                    }
                    for (FloorFurniMovement mov : newFurniId.keySet()) mov.setFurniId(newFurniId.get(mov));


                    effects.clear();
                    conditions.clear();
                    triggers.clear();
                    wireds.clear();

                    prevX = movement.getOldX();
                    prevY = movement.getOldY();
                }

                if (furniDataTools.getFloorItemName(movement.getTypeId()).startsWith("wf_trg_")) {
                    triggers.add(movement);
                    wireds.add(movement);
                }
                else if (furniDataTools.getFloorItemName(movement.getTypeId()).startsWith("wf_cnd_")) {
                    conditions.add(movement);
                    wireds.add(movement);
                }
                else if (furniDataTools.getFloorItemName(movement.getTypeId()).startsWith("wf_act_")) {
                    effects.add(movement);
                    wireds.add(movement);
                }
            }

            Map<FloorFurniMovement, Integer> newFurniId = new HashMap<>();
            for (FloorFurniMovement floorFurniMovement : wireds) {
                FloorFurniMovement curr = effects.size() > 0 ? effects.removeFirst() :
                        conditions.size() > 0 ? conditions.removeFirst() : triggers.removeFirst();
                newFurniId.put(floorFurniMovement, curr.getFurniId());
            }
            for (FloorFurniMovement mov : newFurniId.keySet()) mov.setFurniId(newFurniId.get(mov));
        }

        if (isStackTileUsed && stackTile != null) {
            int x = stackTile.getTile().getX();
            int y = stackTile.getTile().getY();
            int rot = stackTile.getFacing().ordinal();

            // set stacktile back to original position
            floorFurniMovements.add(new FloorFurniMovement(stackTile.getTypeId(), stackTileId,
                    x, y, rot, x, y, rot, 0, 0, -1));
        }

        LinkedList<FloorFurniMovement> queue = new LinkedList<>(floorFurniMovements);

        if (queue.size() > 0) {
            workList.add(queue);
            moveFurniState = MoveFurniState.MOVING;
            furniMoverSendInfo("Enqueued movements");
        }
        else {
            furniMoverSendInfo("No furniture on selected tiles");
        }

    }
    private void onTileClick(HMessage hMessage) {
        if (selectionState != SelectionState.NONE) {
            synchronized (furniMoveLock) {
                hMessage.setBlocked(true);
                HPacket packet = hMessage.getPacket();
                HPoint point = new HPoint(packet.readInteger(), packet.readInteger());

                if (selectionState == SelectionState.AWAIT_SELECTION) {
                    sourcePosition = point;

                    if (rd_fm_mode_tile.isSelected() || rd_fm_mode_auto.isSelected()) { // single tile -> move
//                        if (rd_fm_mode_tile.isSelected()) {
                            furniMoverSendInfo("Select the target tile");
//                        }
                        selectionState = SelectionState.AWAIT_MOVE;
                    }
                    else { // rectangle mode
                        furniMoverSendInfo("Select the end of the rectangle");
                        selectionState = SelectionState.AWAIT_SELECTION2;
                    }
                }
                else if (selectionState == SelectionState.AWAIT_SELECTION2) {
                    if (rd_fm_mode_rect.isSelected()) {
                        furniMoverSendInfo("Select the target tile");
                        selectionState = SelectionState.AWAIT_MOVE;
                        sourceEndPosition = point;
                    }
                }
                else if (selectionState == SelectionState.AWAIT_MOVE) {


                    if (sourceEndPosition != null) { // rectangle
                        int x1 = sourcePosition.getX();
                        int y1 = sourcePosition.getY();
                        int x2 = sourceEndPosition.getX();
                        int y2 = sourceEndPosition.getY();
                        if (x1 > x2) {
                            int temp = x1;
                            x1 = x2;
                            x2 = temp;
                        }
                        if (y1 > y2) {
                            int temp = y1;
                            y1 = y2;
                            y2 = temp;
                        }

                        selection.clear();
                        for (int x = x1; x <= x2; x++) {
                            for (int y = y1; y <= y2; y++) {
                                selection.addAll(floorState.getFurniOnTile(x, y));
                            }
                        }

                        sourceEndPosition = null;
                    }
                    else { // tile or auto
                        selection = floorState.getFurniOnTile(sourcePosition.getX(), sourcePosition.getY());
                    }

                    if (rd_fm_mode_auto.isSelected()) {
                        selectionState = SelectionState.AWAIT_SELECTION;
                    }
                    else {
                        selectionState = SelectionState.NONE;
                    }

                    enqueueSelection(point);
                    sourcePosition = null;
                }
            }
        }
    }
    public void onChangeMovementMode(ActionEvent actionEvent) {
        synchronized (furniMoveLock) {
            selectionState = SelectionState.NONE;
            sourcePosition = null;
            sourceEndPosition = null;
        }
        updateUI();
    }
    private void onMoveFurni(HMessage hMessage) {
        if (furniDataReady() && floorState.inRoom() && excludeFurniState != ExcludeFurniState.NONE) {
            hMessage.setBlocked(true);

            int furniId = hMessage.getPacket().readInteger();
            HFloorItem floorItem = floorState.furniFromId(furniId);
            if (floorItem != null) {
                String furniName = furniDataTools.getFloorItemName(floorItem.getTypeId());
                if (excludeFurniState == ExcludeFurniState.EXCLUDE) {
                    if (excludedFurnis.contains(furniName)) {
                        furniMoverSendInfo(String.format("\"%s\" was already excluded from being moved", furniName));
                    }
                    else {
                        excludedFurnis.add(furniName);
                        furniMoverSendInfo(String.format("Succesfully excluded \"%s\" from being moved", furniName));
                    }
                }
                else if (excludeFurniState == ExcludeFurniState.INCLUDE) {
                    if (excludedFurnis.contains(furniName)) {
                        excludedFurnis.remove(furniName);
                        furniMoverSendInfo(String.format("Succesfully included \"%s\" again", furniName));
                    }
                    else {
                        furniMoverSendInfo(String.format("\"%s\" was not excluded", furniName));
                    }
                }
            }
            else {
                furniMoverSendInfo("Shouldn't happen");
            }
            excludeFurniState = ExcludeFurniState.NONE;
        }
    }



    // poster mover
    private void onMoveWallItem(HMessage hMessage) {
        int id = hMessage.getPacket().readInteger();
        latestWallFurniMovement = new WallFurniInfo(id, hMessage.getPacket().readString());
        updateLocationStringUI = true;

        updateUI();
    }
    private void posterMoved(HMessage hMessage) {
        HPacket packet = hMessage.getPacket();

        if (latestWallFurniMovement != null) {
            String id = packet.readString();
            long wallFurniId = latestWallFurniMovement.getFurniId();
            if (id.equals(wallFurniId+"")) {
                packet.readInteger();
                String posterLocationString = packet.readString();
                latestWallFurniMovement = new WallFurniInfo(wallFurniId, posterLocationString);
                updateLocationStringUI = true;

                updateUI();
            }
        }
    }
    private void movePoster(WallFurniInfo newWallFurniInfo) {
        packetInfoSupport.sendToServer("MoveWallItem", (int)(newWallFurniInfo.getFurniId()), newWallFurniInfo.moveString());
    }


    private void maybeReplaceTBones() {
        boolean wasEnabled = floorState.hasMappings();
        boolean enabled = buildToolsEnabled() && furniDataReady() && ift_pizza_cbx.isSelected();

        if (wasEnabled != enabled) {
            if (enabled)
                floorState.addTypeIdMapper(furniDataTools, "petfood4", "pizza");
            else
                floorState.removeTypeIdMapper(furniDataTools, "petfood4");

            if (floorState.inRoom()) {
                floorState.heavyReload(this);
                latestTboneReq = System.currentTimeMillis();
                new Thread(() -> {
                    Utils.sleep(RATELIMIT + 50);
                    updateUI();
                }).start();
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
        updateUI();
    }

    public void tbones_tgl(ActionEvent actionEvent) {
        maybeReplaceTBones();
    }


    private int locSteps() {
        return Integer.parseInt(((RadioButton)(pm_loc_tgl.getSelectedToggle())).getText());
    }
    private int offsetSteps() {
        return Integer.parseInt(((RadioButton)(pm_loc_offset.getSelectedToggle())).getText());
    }

    public void pm_loc_up_click(ActionEvent actionEvent) {
        WallFurniInfo f = new WallFurniInfo(latestWallFurniMovement);
        f.setY(f.getY() - locSteps());
        movePoster(f);
    }

    public void pm_loc_right_click(ActionEvent actionEvent) {
        WallFurniInfo f = new WallFurniInfo(latestWallFurniMovement);
        f.setX(f.getX() + locSteps());
        movePoster(f);
    }

    public void pm_loc_down_click(ActionEvent actionEvent) {
        WallFurniInfo f = new WallFurniInfo(latestWallFurniMovement);
        f.setY(f.getY() + locSteps());
        movePoster(f);
    }

    public void pm_loc_left_btn_click(ActionEvent actionEvent) {
        WallFurniInfo f = new WallFurniInfo(latestWallFurniMovement);
        f.setX(f.getX() - locSteps());
        movePoster(f);
    }

    public void pm_offset_up_click(ActionEvent actionEvent) {
        WallFurniInfo f = new WallFurniInfo(latestWallFurniMovement);
        f.setyOffset(f.getyOffset() - offsetSteps());
        movePoster(f);
    }

    public void pm_offset_right_click(ActionEvent actionEvent) {
        WallFurniInfo f = new WallFurniInfo(latestWallFurniMovement);
        f.setxOffset(f.getxOffset() + offsetSteps());
        movePoster(f);
    }

    public void pm_offset_down_click(ActionEvent actionEvent) {
        WallFurniInfo f = new WallFurniInfo(latestWallFurniMovement);
        f.setyOffset(f.getyOffset() + offsetSteps());
        movePoster(f);
    }

    public void pm_offset_left_click(ActionEvent actionEvent) {
        WallFurniInfo f = new WallFurniInfo(latestWallFurniMovement);
        f.setxOffset(f.getxOffset() - offsetSteps());
        movePoster(f);
    }

    public void pm_rd_left_click(ActionEvent actionEvent) {
        WallFurniInfo f = new WallFurniInfo(latestWallFurniMovement);
        f.setLeft(true);
        movePoster(f);
    }

    public void pm_rd_right_click(ActionEvent actionEvent) {
        WallFurniInfo f = new WallFurniInfo(latestWallFurniMovement);
        f.setLeft(false);
        movePoster(f);
    }

    public void pm_location_update(ActionEvent actionEvent) {

        try {
            WallFurniInfo f = new WallFurniInfo(latestWallFurniMovement.getFurniId(), pm_location_txt.getText());
            movePoster(f);

        } catch (Exception ignore) { }
    }


    public void packetSpamEnableDisable(ActionEvent actionEvent) {
        packet_spam_spinner.setDisable(!cbx_spam.isSelected());
    }

    private int getBurstValue() {
        return cbx_spam.isSelected() ? packet_spam_spinner.getValue() : 1;
    }

}
