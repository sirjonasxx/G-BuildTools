package extension;

import furnidata.FurniDataTools;
import gearth.extensions.ExtensionForm;
import gearth.extensions.ExtensionInfo;
import gearth.extensions.parsers.HFloorItem;
import gearth.extensions.parsers.HPoint;
import gearth.protocol.HMessage;
import gearth.protocol.HPacket;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.control.*;
import room.FloorState;
import room.StackTileInfo;
import room.StackTileSetting;
import room.StackTileUtils;
import stuff.*;
import utils.Utils;
import utils.Wrapper;

import java.util.*;
import java.util.stream.Collectors;

import component.IllusionAssistComponent;

@ExtensionInfo(
        Title =  "G-BuildTools",
        Description =  "For all your building needs",
        Version =  "2.0.1",
        Author =  "sirjonasxx"
)
public class GBuildTools extends ExtensionForm {

    public CheckBox always_on_top_cbx;
    public Hyperlink readmeLink;

    public Slider ratelimiter;
    public Spinner<Integer> packet_spam_spinner;
    public CheckBox cbx_spam;

    public CheckBox block_walking_cbx;


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

    // black hole stacking tools
    public CheckBox replace_bh_cbx;

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

    // hide furni
    public CheckBox pickup_hide_cbx;
    public Button makevisibleBtn;

    // illusion assist
    public CheckBox flatten_floor_cbx;
    public CheckBox translate_heights_cbx;

    private static final int ratelimitStartOffset = 15;


    private volatile int RATELIMIT = 526;
    private volatile int STACKTILE_RATELIMIT = 16;
    private volatile int MOVEFURNI_RATELIMIT = 30;

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

    // invis furni & black hole
    private volatile long latestReq = -1;

    // illusion assist
    private IllusionAssistComponent illusionAssist;


    public void onRotatedFurniClick(ActionEvent actionEvent) {
        fm_cbx_rotatefurni.setSelected(false);
        fm_cbx_rotatefurni.setDisable(true);
        furniMoverSendInfo("Couldn't implement this feature because Sulake didn't implement rotations correctly");
    }

    public void reload_inv(ActionEvent actionEvent) {
        sendToServer(new HPacket("RequestFurniInventory", HMessage.Direction.TOSERVER));
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

    @Override
    protected void onShow() {
        primaryStage.sizeToScene();
    }

    public boolean buildToolsEnabled() {
        return enable_gbuildtools.isSelected();
    }
    public boolean furniDataReady() {
        return furniDataTools != null && furniDataTools.isReady();
    }
    private List<StackTileInfo> allStackTiles() {
        if (!floorState.inRoom() || !furniDataReady()) return new ArrayList<>();
        List<StackTileInfo> allAvailableStackTiles = new ArrayList<>();
        Set<String> allStacktileClasses = Arrays.stream(StackTileSetting.values()).map(StackTileSetting::getClassName).collect(Collectors.toSet());
        allStacktileClasses.forEach(c -> {
            List<HFloorItem> stackTiles = floorState.getItemsFromType(furniDataTools, c);
            if (stackTiles.size() > 0) {
                HFloorItem stackTile = stackTiles.get(0);
                allAvailableStackTiles.add(new StackTileInfo(
                        stackTile.getId(),
                        stackTile.getTile(),
                        stackTile.getFacing().ordinal(),
                        StackTileSetting.fromClassName(c).getDimension(),
                        stackTile.getTypeId()
                ));
            }
        });

        return allAvailableStackTiles;
    }


    public void updateUI() {
        Platform.runLater(() -> {
            boolean stackLAvailable = allStackTiles().size() > 0;

            room_found_lbl.getStyleClass().clear();
            furnidata_lbl.getStyleClass().clear();
            stack_tile_lbl.getStyleClass().clear();
            room_found_lbl.setText(floorState.inRoom() ? "Room found" : "No room found");
            room_found_lbl.getStyleClass().add(floorState.inRoom() ? "lblgreen" : "lblred");
            furnidata_lbl.setText(furniDataReady() ? "Furnidata loaded" : "Furnidata not loaded");
            furnidata_lbl.getStyleClass().add(furniDataReady() ? "lblgreen" : "lblred");
            stack_tile_lbl.setText(stackLAvailable ? "Stack tile found" : "No stack tile found");
            stack_tile_lbl.getStyleClass().add(stackLAvailable ? "lblgreen" : "lblred");

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


            // invisible furni tools & black hole stacking
            ift_pizza_cbx.setDisable(!furniDataReady() || latestReq > System.currentTimeMillis() - RATELIMIT);
            replace_bh_cbx.setDisable(!furniDataReady() || latestReq > System.currentTimeMillis() - RATELIMIT);


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


            // hide furni
            pickup_hide_cbx.setDisable(!buildToolsEnabled()); // make sure you're not gonna pick up furni if gbuildtools not enabled
            makevisibleBtn.setDisable(!floorState.hasHiddenFurni());

            // illusion assist
            flatten_floor_cbx.setDisable(!buildToolsEnabled());
            translate_heights_cbx.setDisable(!buildToolsEnabled());
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

        ratelimiter.valueProperty().setValue(ratelimitStartOffset);


        floorState = new FloorState(this, o -> updateUI());

        intercept(HMessage.Direction.TOCLIENT, "CloseConnection", m -> reset());
        intercept(HMessage.Direction.TOSERVER, "Quit", m -> reset());
        intercept(HMessage.Direction.TOCLIENT, "RoomReady", m -> reset());

        // quickdrop furni
        new Thread(this::dropFurniLoop).start();
        intercept(HMessage.Direction.TOSERVER, "PlaceObject", this::onFurniPlace);
        intercept(HMessage.Direction.TOCLIENT, "FurniListRemove", this::inventoryFurniRemove);
        intercept(HMessage.Direction.TOSERVER, "BuildersClubPlaceRoomItem", this::buildersClubItemPlace);


        // wired duplicator
        new Thread(() -> wiredSaveLoop(delayedConditionSave, last_condition)).start();
        new Thread(() -> wiredSaveLoop(delayedTriggerSave, last_trigger)).start();
        new Thread(() -> wiredSaveLoop(delayedEffectSave, last_effect)).start();
        intercept(HMessage.Direction.TOSERVER, "UpdateCondition", this::updateWiredCondition);
        intercept(HMessage.Direction.TOSERVER, "UpdateTrigger", this::updateWiredTrigger);
        intercept(HMessage.Direction.TOSERVER, "UpdateAction", this::updateWiredEffect);
        intercept(HMessage.Direction.TOCLIENT, "Open", this::onOpenWired);


        // Stacktile tools
        new Thread(this::stackTileLoop).start();
        intercept(HMessage.Direction.TOSERVER, "SetCustomStackingHeight", this::setStackHeight);


        // furni mover
        new Thread(this::furniMoveLoop).start();
        intercept(HMessage.Direction.TOSERVER, "Chat", this::onUserChat);
        intercept(HMessage.Direction.TOSERVER, "MoveAvatar", this::onTileClick);
        intercept(HMessage.Direction.TOSERVER, "MoveObject", this::onMoveFurni);


        // poster mover
        // {out:MoveWallItem}{i:10616766}{s:":w=0,5 l=4,29 l"}
        intercept(HMessage.Direction.TOSERVER, "MoveWallItem", this::onMoveWallItem);
        intercept(HMessage.Direction.TOCLIENT, "ItemUpdate", this::posterMoved);

        // hide furni
        intercept(HMessage.Direction.TOSERVER, "PickupObject", this::onPickUpItem);

        // illusion assist
        illusionAssist = new IllusionAssistComponent(this);
        illusionAssist.flattenFloorEnabledProperty().bind(flatten_floor_cbx.selectedProperty());
        illusionAssist.translateEnabledProperty().bind(translate_heights_cbx.selectedProperty());

        floorState.requestRoom(this);


        onConnect((host, i, s1, s2, hClient) -> {
            furniDataTools = new FurniDataTools(host, observable -> {
                maybeReplaceBlackHoles();
                maybeReplaceTBones();
                updateUI();
            });
        });

        updateUI();
    }

    private void onPickUpItem(HMessage hMessage) {
        if (buildToolsEnabled() && pickup_hide_cbx.isSelected()) {
            HPacket packet = hMessage.getPacket();
            int mode = packet.readInteger();
            int furniId = packet.readInteger();
            if (mode == 2) {
                floorState.hideFurni(furniId);
                hMessage.setBlocked(true);
                sendToClient(new HPacket("ObjectRemove", HMessage.Direction.TOCLIENT, "" + furniId, false, 0, 0));
                updateUI();
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
                    sendToClient(new HPacket("ObjectRemove", HMessage.Direction.TOCLIENT,
                            -dropInfo.getTempFurniId() + "", false, 0, 0));
                }
                else {
                    // wall furni
                    sendToClient(new HPacket("ItemRemove", HMessage.Direction.TOCLIENT,
                            dropInfo.getTempFurniId() + "", 0));
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
                        sendToClient(new HPacket("ObjectRemove", HMessage.Direction.TOCLIENT,
                                -dropInfo2.getTempFurniId() + "", false, 0, 0));
                    }
                    else {
                        sendToClient(new HPacket("ItemRemove", HMessage.Direction.TOCLIENT,
                                dropInfo2.getTempFurniId() + "", 0));
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

        double height = illusionAssist.getTranslatedHeightmapHeight(dropInfo.getX(), dropInfo.getY());

        if (override_rotation_cbx.isSelected()) {
            dropInfo.setRotation(override_rotation_spinner.getValue());
        }

        sendToClient(new HPacket("ObjectAdd", HMessage.Direction.TOCLIENT, -(int)dropInfo.getTempFurniId(),
                0, dropInfo.getX(), dropInfo.getY(), dropInfo.getRotation(), height + "", "0.0", 0, 0, "", -1, 0, 0, ""));

        int flashFurniId = (int)dropInfo.getFurniId();
        sendToClient(new HPacket("FurniListRemove", HMessage.Direction.TOCLIENT, flashFurniId));
    }
    private void buildersClubItemPlace(HMessage hMessage) {
        if (buildToolsEnabled()) {

            HPacket packet = hMessage.getPacket();

            if (override_rotation_cbx.isSelected()) {
                packet.readInteger();
                packet.readInteger();
                packet.readString();
                packet.readInteger();
                packet.readInteger();
                packet.replaceInt(packet.getReadIndex(), override_rotation_spinner.getValue());
            }

        }
    }
    private void onWallFurniPlace(WallFurniDropInfo dropInfo) {
        synchronized (delayedWallFurniDrop) {
            delayedWallFurniDrop.add(dropInfo);
        }

        WallFurniInfo temp = new WallFurniInfo(dropInfo.getTempFurniId(), dropInfo.getX(), dropInfo.getY(),
                dropInfo.getxOffset(), dropInfo.getyOffset(), dropInfo.isLeft()
        );

        sendToClient(new HPacket("ItemAdd", HMessage.Direction.TOCLIENT, temp.getFurniId() + "", 4001,
                temp.moveString(), "", -1, 0, 0, ""));

        int flashFurniId = (int)dropInfo.getFurniId();
        sendToClient(new HPacket("FurniListRemove", HMessage.Direction.TOCLIENT, flashFurniId));
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
                sendToServer(new HPacket("PlaceObject", HMessage.Direction.TOSERVER, packetString));

                delayedFloorDrop.setDropTimeStamp(System.currentTimeMillis());
                synchronized (awaitDropConfirmation) {
                    awaitDropConfirmation.put(delayedFloorDrop.getFurniId(), delayedFloorDrop);
                }

                Utils.sleep(RATELIMIT);
            }
            if (delayedWallDrop != null) {
                String packetString = delayedWallDrop.placeString();
                sendToServer(new HPacket("PlaceObject", HMessage.Direction.TOSERVER, packetString));

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
                sendToServer(new HPacket("SetCustomStackingHeight", HMessage.Direction.TOSERVER, stackTile.getId(), stacktileheight));
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

            for (StackTileSetting stackTile : StackTileSetting.values()) {
                allStackTiles.addAll(floorState.getItemsFromType(furniDataTools, stackTile.getClassName()));
            }

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
            sendToServer(new HPacket("MoveObject", HMessage.Direction.TOSERVER, furniId, x, y, rot));
            Utils.sleep(delay);
        }
    }
    private void setStackHeight(int stackTileId, int delay, int z) {
        for (int i = 0; i < getBurstValue(); i++) {
            sendToServer(new HPacket("SetCustomStackingHeight", HMessage.Direction.TOSERVER , stackTileId, z));
            Utils.sleep(delay);
        }
    }

    // furnimover
    private void furniMoverSendInfo(String text) {
        if (fm_cbx_visualhelp.isSelected()) {
            sendToClient(new HPacket("Whisper", HMessage.Direction.TOCLIENT, -1, text, 0, 30, 0, -1));
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

                StackTileInfo stackTileInfo = moveFurniState == MoveFurniState.UNDOING ? movement.getUndoStackInfo() : movement.getStackTileInfo();

                if (stackTileInfo != null) {
                    HPoint stackLoc = stackTileInfo.getLocation();
                    int stackRot = stackTileInfo.getRotation();
                    int stacktileId = stackTileInfo.getFurniId();

                    if (latestStackMove == null || latestStackMove.getX() != stackLoc.getX() || latestStackMove.getY() != stackLoc.getY()) {
                        moveFurni(stacktileId, MOVEFURNI_RATELIMIT/2, stackLoc.getX(), stackLoc.getY(), stackRot, -1);
                        latestStackMove = null;
                    }

                    if (latestStackMove == null || !latestStackMove.equals(new HPoint(stackLoc.getX(), stackLoc.getY(), z))) {
                        setStackHeight(stacktileId, (MOVEFURNI_RATELIMIT*2)/3, z);
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
            for (StackTileSetting stackTile : StackTileSetting.values()) {
                stacktiles.add(furniDataTools.getFloorTypeId(stackTile.getClassName()));
            }
        }

        // convert
        int xOffset = target.getX() - sourcePosition.getX();
        int yOffset = target.getY() - sourcePosition.getY();

        Set<Integer> usedStackTiles = new HashSet<>();
        List<StackTileInfo> potentialStackTiles = allStackTiles();

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

            int newX = x+xOffset;
            int newY = y+yOffset;
            if (fm_cbx_inversedir.isSelected()) {
                int xdiff = x - sourcePosition.getX();
                int ydiff = y - sourcePosition.getY();

                newX += (ydiff-xdiff);
                newY += (xdiff-ydiff);
            }

            StackTileInfo usedStackTile = null;
            StackTileInfo undoStackInfo = null;
            if (fm_cbx_usestacktile.isSelected()
                    && furniDataTools.isStackable(classname)) {
                usedStackTile = StackTileUtils.findBestDropLocation(newX, newY, potentialStackTiles, floorState);
                if (usedStackTile != null) {
                    usedStackTiles.add(usedStackTile.getFurniId());
                }

                undoStackInfo = StackTileUtils.findBestDropLocation(x, y, potentialStackTiles, floorState);
                if (undoStackInfo != null) {
                    usedStackTiles.add(undoStackInfo.getFurniId());
                }
            }

            FloorFurniMovement movement = new FloorFurniMovement(floorItem.getTypeId(), floorItem.getId(),
                    x, y, rot, newX, newY, rot, oldZ, newZ, usedStackTile, undoStackInfo
            );
            floorFurniMovements.add(movement);
        }

        // decide order
        floorFurniMovements.sort((o1, o2) -> {
            int maybe1 = Boolean.compare(o1.getStackTileInfo() != null, o2.getStackTileInfo() != null);
            if (maybe1 == 0) {
                int maybe2 = Integer.compare(o1.getOldY(), o2.getOldY());
                if (maybe2 == 0) {
                    int maybe3 = Integer.compare(o1.getOldX(), o2.getOldX());
                    if (maybe3 == 0) {
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

        for(int stackTileId : usedStackTiles) {
            StackTileInfo stackTile = potentialStackTiles.stream().filter(stackTileInfo1 -> stackTileInfo1.getFurniId() == stackTileId).findFirst().get();
            int x = stackTile.getLocation().getX();
            int y = stackTile.getLocation().getY();
            int rot = stackTile.getRotation();
            int typeId = stackTile.getTypeId();

            // set stacktile back to original position
            floorFurniMovements.add(new FloorFurniMovement(typeId, stackTileId,
                    x, y, rot, x, y, rot, 0, 0, null, null));
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

        if (block_walking_cbx.isSelected() && buildToolsEnabled()) {
            hMessage.setBlocked(true);
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
        sendToServer(new HPacket("MoveWallItem", HMessage.Direction.TOSERVER, (int)(newWallFurniInfo.getFurniId()), newWallFurniInfo.moveString()));
    }


    private void reloadRoom() {
        if (floorState.inRoom()) {
            floorState.heavyReload(this);
            latestReq = System.currentTimeMillis();
            new Thread(() -> {
                Utils.sleep(RATELIMIT + 50);
                updateUI();
            }).start();
        }
    }

    private void maybeReplaceTBones() {
        boolean enabled = buildToolsEnabled() && furniDataReady() && ift_pizza_cbx.isSelected();

        List<String> invisibleFurni1State = Arrays.asList("petfood4", "s_snowball_machine", "wf_blob", "wf_blob2",
                "wf_blob_invis", "wf_blob2_vis");

        List<String> invisibleFurni2States = Arrays.asList("room_invisible_block", "tile_fxprovider_nfs", "room_wl15_infolink");

        int oldSize = floorState.amountMappings();

        if (enabled) {
            invisibleFurni1State.forEach(f -> floorState.addTypeIdMapper(furniDataTools, f, "pizza"));
            invisibleFurni2States.forEach(f -> floorState.addTypeIdMapper(furniDataTools, f, "antique_c21_magnifyinglass"));
        }
        else {
            invisibleFurni1State.forEach(f -> floorState.removeTypeIdMapper(furniDataTools, f));
            invisibleFurni2States.forEach(f -> floorState.removeTypeIdMapper(furniDataTools, f));
        }

        // changes have been made
        if (floorState.amountMappings() != oldSize) {
            reloadRoom();
        }
    }
    private void maybeReplaceBlackHoles() {
        boolean enabled = buildToolsEnabled() && furniDataReady() && replace_bh_cbx.isSelected();

        int oldSize = floorState.amountMappings();
        if (enabled) {
            floorState.addTypeIdMapper(furniDataTools, "hole", "usva2_rug");
        }
        else {
            floorState.removeTypeIdMapper(furniDataTools, "hole");
        }

        // changes have been made
        if (floorState.amountMappings() != oldSize) {
            reloadRoom();
        }
    }


    public void toggleAlwaysOnTop(ActionEvent actionEvent) {
        primaryStage.setAlwaysOnTop(always_on_top_cbx.isSelected());
    }

    public void toggleOverrideRotation(ActionEvent actionEvent) {
        updateUI();
    }


    public FurniDataTools getFurniDataTools() {
        return furniDataTools;
    }

    public void enable_tgl(ActionEvent actionEvent) {
        maybeReplaceTBones();
        maybeReplaceBlackHoles();
        updateUI();
    }

    public void tbones_tgl(ActionEvent actionEvent) {
        maybeReplaceTBones();
    }

    public void replaceBlackHolesClick(ActionEvent actionEvent) {
        maybeReplaceBlackHoles();
    }

    public void makeVisibleClick(ActionEvent actionEvent) {
        floorState.clearHiddenFurni();
        updateUI();
        reloadRoom();
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
