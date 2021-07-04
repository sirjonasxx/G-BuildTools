import gearth.Main;
import gearth.extensions.ExtensionForm;
import gearth.extensions.ExtensionFormLauncher;
import gearth.extensions.ExtensionInfo;
import gearth.extensions.extra.tools.PacketInfoSupport;
import gearth.protocol.HMessage;
import gearth.protocol.HPacket;
import gearth.protocol.connection.HClient;
import gearth.ui.GEarthController;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import utils.Utils;

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

    public CheckBox enable_gbuildtools;
    public Label room_found_lbl;
    public Label stack_tile_lbl;

    public CheckBox qd_floor_cbx;
    public CheckBox qd_wall_cbx;
    public CheckBox override_rotation_cbx;
    public Spinner override_rotation_spinner;

    private final static int RATELIMIT = 530;
    private PacketInfoSupport packetInfoSupport = null;


    private final LinkedList<FloorFurniDropInfo> delayedFurniDrop = new LinkedList<>();
    private final Map<Long, FloorFurniDropInfo> awaitDropConfirmation = new HashMap<>();

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

    private void updateUI() {
        Platform.runLater(() -> {

        });
    }


    private void dropFloorFurniLoop() {
        while (true) {
            FloorFurniDropInfo delayedDrop = null;
            synchronized (delayedFurniDrop) {
                if (delayedFurniDrop.size() > 0) {
                    delayedDrop = delayedFurniDrop.removeFirst();
                }
            }

            if (delayedDrop != null) {
                String packetString = String.format("-%d %d %d %d",
                        delayedDrop.getFurniId(),
                        delayedDrop.getX(),
                        delayedDrop.getY(),
                        delayedDrop.getRotation()
                );
                packetInfoSupport.sendToServer("PlaceObject", packetString);

                delayedDrop.setDropTimeStamp(System.currentTimeMillis());
                synchronized (awaitDropConfirmation) {
                    awaitDropConfirmation.put(-delayedDrop.getFurniId(), delayedDrop);
                }
                Utils.sleep(RATELIMIT);
            }
            else {
                Utils.sleep(2);
            }
        }
    }

    @Override
    protected void initExtension() {
        packetInfoSupport = new PacketInfoSupport(this);

        new Thread(this::dropFloorFurniLoop).start();

        packetInfoSupport.intercept(HMessage.Direction.TOCLIENT, "RoomReady", m -> reset());

        packetInfoSupport.intercept(HMessage.Direction.TOSERVER, "PlaceObject", this::onFloorFurniPlaceFlash);
        packetInfoSupport.intercept(HMessage.Direction.TOCLIENT, "FurniListRemove", this::inventoryFurniRemove); // flash
    }

    @Override
    protected void onEndConnection() {
        reset();
    }

    private void reset() {
        synchronized (awaitDropConfirmation) {
            awaitDropConfirmation.clear();
        }
        synchronized (delayedFurniDrop) {
            delayedFurniDrop.clear();
        }
    }


    private void inventoryFurniRemove(HMessage hMessage) {
        HPacket packet = hMessage.getPacket();
        long furniId = packet.readInteger();

        synchronized (awaitDropConfirmation) {
            if (awaitDropConfirmation.containsKey(furniId)) {
                if (furniId < 0) {
                    // is floor furni
                    hMessage.setBlocked(true); //packet was already

                    FloorFurniDropInfo dropInfo = awaitDropConfirmation.remove(furniId);
                    packetInfoSupport.sendToClient("ObjectRemove",
                            dropInfo.getTempFurniId() + "", false, 0, 0);


                }
                else {
                    // is wall furni
                }

            }
        }
    }

    private void onFloorFurniPlaceFlash(HMessage hMessage) {
        hMessage.setBlocked(true);

        String info = hMessage.getPacket().readString();
        String[] split = info.split(" ");

        long furniId = -Long.parseLong(split[0]);

        int x = Integer.parseInt(split[1]);
        int y = Integer.parseInt(split[2]);
        int rotation = Integer.parseInt(split[3]);

        // black box temporary furni
        long tempFurniId = Integer.MAX_VALUE - furniId;

        FloorFurniDropInfo dropInfo = new FloorFurniDropInfo(furniId, tempFurniId, rotation, x, y);
        onFloorFurniPlace(dropInfo);
    }
    private void onFloorFurniPlaceUnity(HMessage hMessage) {
        hMessage.setBlocked(true);

        HPacket packet = hMessage.getPacket();
        long furniId = -packet.readLong();
        int x = packet.readInteger();
        int y = packet.readInteger();
        int rotation = packet.readInteger();

        // black box temporary furni
        long tempFurniId = Integer.MAX_VALUE - furniId;

        FloorFurniDropInfo dropInfo = new FloorFurniDropInfo(furniId, tempFurniId, rotation, x, y);
        onFloorFurniPlace(dropInfo);
    }
    private void onFloorFurniPlace(FloorFurniDropInfo dropInfo) {
        if (!buildToolsEnabled()) return;

        synchronized (delayedFurniDrop) {
            delayedFurniDrop.add(dropInfo);
        }

        packetInfoSupport.sendToClient("ObjectAdd", (int) dropInfo.getTempFurniId(),
                0, dropInfo.getX(), dropInfo.getY(), dropInfo.getRotation(), "0.0", "0.0", 0, 0, "", -1, 0, 0, "");

        int flashFurniId = -(int)dropInfo.getFurniId();
        packetInfoSupport.sendToClient("FurniListRemove", flashFurniId);
    }


    public void toggleAlwaysOnTop(ActionEvent actionEvent) {
        primaryStage.setAlwaysOnTop(always_on_top_cbx.isSelected());
    }
}
