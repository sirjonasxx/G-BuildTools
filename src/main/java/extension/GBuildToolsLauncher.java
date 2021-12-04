package extension;

import gearth.Main;
import gearth.extensions.ExtensionForm;
import gearth.extensions.ExtensionFormCreator;
import gearth.ui.GEarthController;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

public class GBuildToolsLauncher extends ExtensionFormCreator {
    @Override
    protected ExtensionForm createForm(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("gbuildtools.fxml"));
        Parent root = loader.load();

        stage.setTitle("G-BuildTools 2.0");
        stage.setScene(new Scene(root));
        stage.getScene().getStylesheets().add(GEarthController.class.getResource("/gearth/themes/G-Earth/styling.css").toExternalForm());
        stage.getIcons().add(new Image(Main.class.getResourceAsStream("/gearth/themes/G-Earth/logoSmall.png")));

        stage.setResizable(false);

        return loader.getController();
    }

    public static void main(String[] args) {
        runExtensionForm(args, GBuildToolsLauncher.class);
    }

}
