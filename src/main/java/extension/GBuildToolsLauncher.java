package extension;

import gearth.extensions.ThemedExtensionFormCreator;
import javafx.stage.Stage;

import java.net.URL;


public class GBuildToolsLauncher extends ThemedExtensionFormCreator {

    @Override
    protected String getTitle() {
        return "G-BuildTools 2.0.1";
    }

    @Override
    protected URL getFormResource() {
        return getClass().getResource("gbuildtools.fxml");
    }

    @Override
    protected void initialize(Stage primaryStage) {
        primaryStage.getScene().getStylesheets().add(getClass().getResource("styles.css").toExternalForm());
    }

    public static void main(String[] args) {
        runExtensionForm(args, GBuildToolsLauncher.class);
    }

}

