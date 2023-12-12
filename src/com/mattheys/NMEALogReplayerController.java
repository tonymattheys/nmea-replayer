/*
 * This is where a lot of the action happens.
 *
 */
package com.mattheys;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.ResourceBundle;
import java.util.logging.Level;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.web.WebEngine;
import javafx.stage.FileChooser;
import javax.imageio.ImageIO;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 *
 * @author tony
 *
 * JavaFX controller for the NMEA log replayer. Contains a bunch of methods that
 * are called to update the displayed map and extract time and position data from
 * the data stream.
 *
 */
public class NMEALogReplayerController implements Initializable {

    @FXML private Label fileNameLabel;
    @FXML private Slider slider;
    @FXML private Label sliderLabel; // The time from the GPS sentences
    @FXML private TextArea NMEASentences;
    @FXML private Button playButton;
    @FXML private ImageView image1;
    @FXML private ImageView image2;
    @FXML private ImageView image3;
    @FXML private ImageView image4;
    @FXML private ImageView image5;
    @FXML private ImageView image6;
    @FXML private ImageView image7;
    @FXML private ImageView image8;
    @FXML private ImageView image9;
    @FXML private ImageView circleImage;
    @FXML private CheckBox sprayAndPray ;

    RandomAccessFile randomAccessFile;
    long rafLength = 0;
    static WebEngine webEngine = null;
    static double latitude = 0.0;
    static double longitude = 0.0;
    static int zoom = 10;
    static int x, y = 0;
    static NMEAPlayer player = new NMEAPlayer();
    static String timeString = "??:??:??";
    
    private static final Logger logger = LogManager.getLogger(NMEALogReplayer.class);

    /**
     *
     * @param x
     * @param y
     * @param zoom
     * @return
     *
     * Some utility functions that will calculate the bounding box values for a tile
     * given the zoom value and x and y values. returns a BoundingBox that contains
     * latitude and longitude of the bounding box of that tile.
     *
     */
    public BoundingBox tile2boundingBox(final int x, final int y, final int zoom) {
        BoundingBox bb = new BoundingBox();
        bb.north = tile2lat(y, zoom);
        bb.south = tile2lat(y + 1, zoom);
        bb.west = tile2lon(x, zoom);
        bb.east = tile2lon(x + 1, zoom);
        return bb;
    }

    /**
     *
     * @param x
     * @param z
     * @return
     *
     * Longitude of a tile
     *
     */
    static double tile2lon(int x, int z) {
        return x / Math.pow(2.0, z) * 360.0 - 180;
    }

    /**
     *
     * @param x
     * @param z
     * @return
     *
     * Latitude of a tile
     *
     */
    static double tile2lat(int y, int z) {
        double n = Math.PI - (2.0 * Math.PI * y) / Math.pow(2.0, z);
        return Math.toDegrees(Math.atan(Math.sinh(n)));
    }

    /**
     *
     * @param lat
     * @param lon
     * @param zoom
     * @return zoom, xtile, ytile
     *
     * Utility method to figure out which tile to fetch from a tile server based on
     * the zoom level and the latitude and longitude. The tile retrieved is the one
     * that contains that location at the selected zoom level.
     */
    public static String getTileNumber(final double lat, final double lon, final int zoom) {
        int xtile = (int) Math.floor((lon + 180) / 360 * (1 << zoom));
        int ytile = (int) Math.floor((1 - Math.log(Math.tan(Math.toRadians(lat)) + 1 / Math.cos(Math.toRadians(lat))) / Math.PI) / 2 * (1 << zoom));
        if (xtile < 0) {
            xtile = 0;
        }
        if (xtile >= (1 << zoom)) {
            xtile = ((1 << zoom) - 1);
        }
        if (ytile < 0) {
            ytile = 0;
        }
        if (ytile >= (1 << zoom)) {
            ytile = ((1 << zoom) - 1);
        }
        return ("" + zoom + "/" + xtile + "/" + ytile);
    }

    /**
     *
     * @param lat
     * @param lon
     *
     * Update the pointer on the map and set it at the location specified.
     *
     * Basically we need to calculate where that lat/long is located on the map tiles
     * we are displaying and then put the cursor image there. The image must be put
     * slightly offset by half its width and height so that the centre of the image
     * is exactly on the right location.
     *
     */
    public void updateMapPointer(double lat, double lon) {
        logger.debug("Image properties X=" + image5.getLayoutX() + " Y=" + image5.getLayoutY());
        logger.debug("Image properties width=" + image5.getFitWidth() + " height=" + image5.getFitHeight());
        logger.debug("Current Position Latitude = " + String.format("%.4f", latitude) + " Longitude = " + String.format("%.4f", longitude));
        logger.debug("image5 Bounding Box NORTH=" + tile2boundingBox(x, y, zoom).north);
        logger.debug("image5 Bounding Box SOUTH=" + tile2boundingBox(x, y, zoom).south);
        logger.debug("image5 Bounding Box  EAST=" + tile2boundingBox(x, y, zoom).east);
        logger.debug("image5 Bounding Box  WEST=" + tile2boundingBox(x, y, zoom).west);
        double dblx = image5.getLayoutX() + (Math.abs(longitude - tile2boundingBox(x, y, zoom).west) / Math.abs(tile2boundingBox(x, y, zoom).east - tile2boundingBox(x, y, zoom).west)) * image5.getFitWidth();
        logger.debug("X location in screen pixels " + Math.rint(dblx));
        double dbly = image5.getLayoutY() + (Math.abs(latitude - tile2boundingBox(x, y, zoom).north) / Math.abs(tile2boundingBox(x, y, zoom).south - tile2boundingBox(x, y, zoom).north)) * image5.getFitHeight();
        logger.debug("Y location in screen pixels " + Math.rint(dbly));
        circleImage.setLayoutX(Math.rint(dblx) - circleImage.getFitWidth() / 2);
        circleImage.setLayoutY(Math.rint(dbly) - circleImage.getFitHeight() / 2);
    }

    /**
     * getMapTile - fetches a map tile either from cache or from a URL
     *
     * @param str
     * @return
     */
    public Image getMapTile(String str) {
        BufferedImage img = null;
        URL url = null;

        logger.debug("Getting map tile " + str);
        // https://a.tile-cyclosm.openstreetmap.fr/cyclosm/10/511/511.png
        try {
            url = new URL("https://a.tile-cyclosm.openstreetmap.fr/cyclosm/" + str);
//          url = new URL("https://pyrene.openstreetmap.org/" + str);
        } catch (MalformedURLException ex) {
            java.util.logging.Logger.getLogger(NMEALogReplayerController.class.getName()).log(Level.SEVERE, null, ex);
        }

        File f = new File(".cache." + str.replace("/", "."));
        if (f.exists()) {
            logger.debug("cache hit for .cache." + str.replace("/", "."));
            try {
                img = ImageIO.read(f);
            } catch (IOException ex) {
                java.util.logging.Logger.getLogger(NMEALogReplayerController.class.getName()).log(Level.SEVERE, null, ex);
            }
        } else {
            logger.debug("cache miss for .cache." + str.replace("/", "."));
            try {
                img = ImageIO.read(url);
                logger.debug("Creating new cache file .cache." + str.replace("/", "."));
                f.createNewFile();
                ImageIO.write(img, "PNG", f);
            } catch (IOException ex) {
                java.util.logging.Logger.getLogger(NMEALogReplayerController.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        logger.debug("Returning map tile for " + str);
        return SwingFXUtils.toFXImage(img, null);
    }

    /**
     *
     * @param lat - latitude
     * @param lon - longitude
     *
     * Just updates the map with a marker at the specified latitude and longitude
     * Scroll value is set from a variable but it basically stays static right now.
     */
    public void updateMap(int zoom, double lat, double lon) {
        String str = getTileNumber(lat, lon, zoom);
        x = Integer.parseInt(str.split("/")[1]);
        y = Integer.parseInt(str.split("/")[2]);

        logger.debug("Update Map with : zoom=" + zoom + " lat=" + lat + " lon=" + lon);
        logger.debug("Update Map tile number calculated as " + str + ".png");

        image1.setImage(getMapTile(String.format("%d", zoom) + "/" + String.format("%d", x - 1) + "/" + String.format("%d", y - 1) + ".png"));
        image2.setImage(getMapTile(String.format("%d", zoom) + "/" + String.format("%d", x) + "/" + String.format("%d", y - 1) + ".png"));
        image3.setImage(getMapTile(String.format("%d", zoom) + "/" + String.format("%d", x + 1) + "/" + String.format("%d", y - 1) + ".png"));
        image4.setImage(getMapTile(String.format("%d", zoom) + "/" + String.format("%d", x - 1) + "/" + String.format("%d", y) + ".png"));
        image5.setImage(getMapTile(String.format("%d", zoom) + "/" + String.format("%d", x) + "/" + String.format("%d", y) + ".png"));
        image6.setImage(getMapTile(String.format("%d", zoom) + "/" + String.format("%d", x + 1) + "/" + String.format("%d", y) + ".png"));
        image7.setImage(getMapTile(String.format("%d", zoom) + "/" + String.format("%d", x - 1) + "/" + String.format("%d", y + 1) + ".png"));
        image8.setImage(getMapTile(String.format("%d", zoom) + "/" + String.format("%d", x) + "/" + String.format("%d", y + 1) + ".png"));
        image9.setImage(getMapTile(String.format("%d", zoom) + "/" + String.format("%d", x + 1) + "/" + String.format("%d", y + 1) + ".png"));

        logger.debug("Map update is complete. Updating the pointer as well.");

        updateMapPointer(lat, lon);
    }

    /**
     *
     * @param ae
     *
     * Zoom the map
     *
     */
    @FXML private void zoomIn(ActionEvent ae) {
        zoom = zoom + 1;
        if (zoom > 20) {
            zoom = 20;
        }
        updateMap(zoom, latitude, longitude);
        sliderLabel.setText(timeString);
    }

    @FXML private void zoomOut(ActionEvent ae) {
        zoom = zoom - 1;
        if (zoom < 1) {
            zoom = 1;
        }
        updateMap(zoom, latitude, longitude);
        sliderLabel.setText(timeString);
    }

    /**
     *
     * @param me - Mouse Event
     *
     * Handles the case of the slider scale being clicked to move the button to a
     * new location. All we need to do is update the map.
     *
     */
    @FXML private void sliderClicked(MouseEvent me) {
        logger.debug("Slider mouse event just happened : " + me.getEventType().getName());
        updateMap(zoom, latitude, longitude);
        sliderLabel.setText(timeString);
    }

    /**
     *
     * @param fileLine - The NMEA sentence that we need to look at to grab any time
     * and/or position information to update the GUI. Basically, we look to see if
     * this is a $>__GLL, $__GGA or $__RMC record, and if so we extract time and
     * position data from the relevant fields.
     *
     * We need to be careful here because some GPS units send out empty sentences for
     * some reason so it's possible we might see null fields which will mess up the
     * interpretation.
     *
     */
    public static void updateTimeAndPosition(String fileLine) {
        logger.debug("Update Time and Position with : " + fileLine);
        String[] gpsFields = fileLine.split(",");
        logger.debug("Fields being considered : " + Arrays.toString(gpsFields));

        Boolean fieldsOK = true;
        int maxIndex = 6;
        for (int i = 0; (i < gpsFields.length) && (i < maxIndex); i++) {
            if (gpsFields[i].trim().isEmpty()) {
                fieldsOK = false;
                logger.debug("One or more fields are empty so we are skipping this record.");
            }
        }

        if (fieldsOK) {
            if (gpsFields[0].contains("GLL")) {
                logger.debug(gpsFields[1] + gpsFields[2]);
                logger.debug(gpsFields[3] + gpsFields[4]);
                logger.debug("GPS Latitude  = " + player.GPStoDecimal(gpsFields[1], gpsFields[2]));
                logger.debug("GPS Longitude = " + player.GPStoDecimal(gpsFields[3], gpsFields[4]));
                latitude = player.GPStoDecimal(gpsFields[1], gpsFields[2]);
                longitude = player.GPStoDecimal(gpsFields[3], gpsFields[4]);
                logger.debug("Time = " + gpsFields[5]);
                timeString = gpsFields[5].substring(0, 2) + ":" + gpsFields[5].substring(2, 4) + ":" + gpsFields[5].substring(4, 6);
                logger.debug("Time String = " + timeString);
            }
            if (gpsFields[0].contains("GGA")) {
                logger.debug(gpsFields[2] + gpsFields[3]);
                logger.debug(gpsFields[4] + gpsFields[5]);
                logger.debug("GPS Latitude  = " + player.GPStoDecimal(gpsFields[2], gpsFields[3]));
                logger.debug("GPS Longitude = " + player.GPStoDecimal(gpsFields[4], gpsFields[5]));
                latitude = player.GPStoDecimal(gpsFields[2], gpsFields[3]);
                longitude = player.GPStoDecimal(gpsFields[4], gpsFields[5]);
                logger.debug("Time = " + gpsFields[1]);
                timeString = gpsFields[1].substring(0, 2) + ":" + gpsFields[1].substring(2, 4) + ":" + gpsFields[1].substring(4, 6);
                logger.debug("Time String = " + timeString);
            }
            if (gpsFields[0].contains("RMC")) {
                logger.debug(gpsFields[3] + gpsFields[4]);
                logger.debug(gpsFields[5] + gpsFields[6]);
                logger.debug("GPS Latitude  = " + player.GPStoDecimal(gpsFields[3], gpsFields[4]));
                logger.debug("GPS Longitude = " + player.GPStoDecimal(gpsFields[5], gpsFields[6]));
                latitude = player.GPStoDecimal(gpsFields[3], gpsFields[4]);
                longitude = player.GPStoDecimal(gpsFields[5], gpsFields[6]);
                logger.debug("Time = " + gpsFields[1]);
                timeString = gpsFields[1].substring(0, 2) + ":" + gpsFields[1].substring(2, 4) + ":" + gpsFields[1].substring(4, 6);
                logger.debug("Time String = " + timeString);
            }
        }
    }

    @FXML private void sprayAndPraySelected(ActionEvent event) {
        logger.debug("Spray and Pray : " + event.toString());
        logger.debug("Checkbox isSelected??? : " + sprayAndPray.isSelected() );
        player.setSprayAndPray(sprayAndPray.isSelected());
    }
    /**
     * @param event - the event that triggered this call
     *
     * This method will be called when the user selects the "Open File" button.
     *
     * The user will be prompted for the file to be opened and the program will go
     * ahead and open the file with the file pointer set to the first byte. Then
     * it will read the first few sentences and display them in the GUI so the
     * user can see them and finally reset the file pointer back to the beginning
     * of the file.
     *
     */
    @FXML private void fileOpenButtonPressed(ActionEvent event) {
        logger.debug("OPEN FILE");
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Open NMEA File");
        File file = chooser.showOpenDialog(fileNameLabel.getParent().getScene().getWindow());
        fileNameLabel.setText(file.toString());
        logger.debug("FILE = " + fileNameLabel);

        try {
            randomAccessFile = new RandomAccessFile(file, "r");
            rafLength = randomAccessFile.length();
        } catch (FileNotFoundException ex) {
            logger.fatal(ex);
        } catch (IOException ex) {
            logger.fatal(ex);
        }

        player.setRandomAccessFile(randomAccessFile);
        player.setPlaying(false);

        try {
            randomAccessFile.seek((long) 0.0);
        } catch (IOException ex) {
            logger.fatal(ex);
        }
        // Now fill up the TextArea with whatever we found in the file.
        NMEASentences.clear();
        sliderLabel.setText(timeString);
        for (int i = 0; i < NMEASentences.getHeight(); i++) {
            String fileLine = null;
            try {
                fileLine = randomAccessFile.readLine();
            } catch (IOException ex) {
                logger.fatal(ex);
            }
            NMEASentences.appendText(fileLine + "\r\n");
            updateTimeAndPosition(fileLine);
        }
        try {
            // Now go back to the beginning of the file again
            randomAccessFile.seek((long) 0.0);
        } catch (IOException ex) {
            logger.fatal(ex);
        }
        updateMap(zoom, latitude, longitude);
        sliderLabel.setText(timeString);
        slider.setValue(0.0d);
        if (player.isAlive()) {
            logger.debug("Player thread is already alive. No need to start it again.");
        } else {
            logger.debug("Starting the Player thread for the first time.");
            player.start();
        }
    }

    /**
     * @param event - the event that triggered this call
     *
     * This method simply flips the Boolean "playing" to indicate whether or not
     * we are playing a file right now. If a file is NOT being played, we switch over
     * to play mode and fire up the thread that plays to the network.
     *
     * We also set the text shown on the Play button to reflect the current state
     */
    @FXML private void updateButtonPressed(ActionEvent event) {
        updateMap(zoom, latitude, longitude);
        sliderLabel.setText(timeString);
    }

    /**
     * @param event - the event that triggered this call
     *
     * This method simply flips the Boolean "playing" to indicate whether or not
     * we are playing a file right now. If a file is NOT being played, we switch over
     * to play mode and fire up the thread that plays to the network.
     *
     * We also set the text shown on the Play button to reflect the current state
     */
    @FXML private void playButtonPressed(ActionEvent event) {
        // Start playing NMEA sentences from the current file location
        if (player.isPlaying()) {
            playButton.setText("Play");
            player.setPlaying(false);
            try {
                slider.setValue((randomAccessFile.getFilePointer() * slider.getMax()) / rafLength);
            } catch (IOException ex) {
                logger.fatal(ex);
            }
        } else {
            playButton.setText("Stop");
            player.setPlaying(true);
        }
    }

    /**
     *
     * @param url
     * @param rb
     *
     * This is where we initialize the code. Basically we set up the moving map and
     * set the file pointer to the beginning of the file. We also read the first 20
     * sentences from the file and try to extract any time and position information
     * that is recorded there. In noisy AIS environments it's quite possible that we
     * might not find anything in the first 20 records because we don't try to scan
     * AIS records for this information.
     *
     * Finally we create a ChangListener on the file position slider so that when the
     * user scrubs along the slider scale the GUI gets updated with the latest time
     * and position data and the map is relocated to the new position.
     *
     */
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        /**
         * Do the initial load of the map. Set the slider to the beginning of
         * the file and the time value to some initial value ("??:??:??")
         *
         */
        updateMap(zoom, latitude, longitude);
        slider.setValue(0.0);
        sliderLabel.setText(timeString);
        /**
         * Listen for changes on the slider position. If a change is detected we
         * scan the next NMEASentences.getHeight() records and update time and
         * position date on the GUI
         */
        slider.valueProperty().addListener(new ChangeListener<Number>() {
            @Override
            public void changed(ObservableValue<? extends Number> ov, Number old_val, Number new_val) {
                logger.debug("Change Listener method for the slider. Old Value is " + old_val + " and new value is " + new_val);
                if (rafLength > 0 && !player.isPlaying()) {
                    logger.debug("File length is fine and we are not busy playing so we can seek to a new location.");
                    try {
                        randomAccessFile.seek((long) ((slider.getValue() / slider.getMax()) * rafLength));
                        randomAccessFile.readLine(); // to seek to the beginning of the next sentence
                        // Now fill up the TextArea with whatever we found in the file.
                        NMEASentences.clear();
                        for (int i = 0; i < NMEASentences.getHeight(); i++) {
                            String fileLine = randomAccessFile.readLine();
                            NMEASentences.appendText(fileLine + "\r\n");
                            if (!fileLine.isEmpty() && fileLine.startsWith("$")) { // GPS Sentence
                                updateTimeAndPosition(fileLine);
                            }
                            sliderLabel.setText(timeString);
                        }
                        // Now go back to where we were when we started
                        randomAccessFile.seek((long) ((slider.getValue() / slider.getMax()) * rafLength));
                        randomAccessFile.readLine(); // to seek to the beginning of the next sentence
                        if (!slider.isPressed()) {
                            logger.debug("Not pressing the slider button so we can update the map.");
                            updateMap(zoom, latitude, longitude);
                        }
                    } catch (IOException ex) {
                        logger.fatal(ex);
                    }
                }
                logger.debug("Change Listener method EXITING");
            }
        });
    }
}
