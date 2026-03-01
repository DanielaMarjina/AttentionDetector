import javafx.animation.FadeTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.videoio.VideoCapture;
import javafx.scene.image.ImageView;
import org.opencv.imgproc.Imgproc;
import javafx.scene.control.Button;

import java.awt.*;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.atomic.AtomicBoolean;

public class EyeDetector extends Application {
    static {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
    }

    private VideoCapture camera;
    private CascadeClassifier faceDetector;
    private CascadeClassifier eyeDetector;
    private ImageView cameraView;
    private MediaView videoView;
    private MediaPlayer mediaPlayer;
    private volatile boolean cameraActive = true;
    private Thread cameraThread;
    private Stage cameraStage;
    private Stage videoStage;
    private ListView<String> videos;
    private Media media;
    private Boolean openBrowser = false;
    private boolean browserOpened = false;
    private Button btnExit = new Button("Exit");
    private Text text=new Text("Choose your poison:");

    @Override
    public void start(Stage primaryStage) throws Exception {
        cameraStage = primaryStage;
        setUpCameraStage();

        videoStage = new Stage();
        videoStage.setTitle("I warned you..");

        camera = new VideoCapture(0);
        faceDetector = new CascadeClassifier("haarcascades/haarcascade_frontalface_default.xml");
        eyeDetector = new CascadeClassifier("haarcascades/haarcascade_eye.xml");
        if (!camera.isOpened()) {
            System.out.println("Nu a putut fi conectata camera");
            Platform.exit();
            return;
        }

        startCameraLoop();
    }


    private ListView<String> getListView() {
        videos = new ListView<>();
        videos.setMaxHeight(75);
        ObservableList<String> data = FXCollections.observableArrayList("COTCOOODAAAC!!!", "Your future", "The walking Dev");
        videos.setItems(data);
        videos.getSelectionModel().selectFirst();
        videos.getSelectionModel().selectedItemProperty().addListener((observableValue, o, t1) -> {
            if (t1 != null) {
                if (mediaPlayer != null) {
                    mediaPlayer.stop();
                    mediaPlayer.dispose();
                }
                if (t1.equals("COTCOOODAAAC!!!")) {
                    openBrowser = false;
                    media = new Media(new File("media/video.mp4").toURI().toString());
                    mediaPlayer = new MediaPlayer(media);
                    mediaPlayer.setCycleCount(MediaPlayer.INDEFINITE);
                    if (videoView != null) {
                        videoView.setMediaPlayer(mediaPlayer);
                    }
                } else if (t1.equals("The walking Dev")) {
                    openBrowser = false;
                    media = new Media(new File("media/video2.mp4").toURI().toString());
                    mediaPlayer = new MediaPlayer(media);
                    mediaPlayer.setCycleCount(MediaPlayer.INDEFINITE);
                    if (videoView != null) {
                        videoView.setMediaPlayer(mediaPlayer);
                    }
                } else if (t1.equals("Your future")) {
                    videoStage.hide();
                    openBrowser = true;
                    if (mediaPlayer != null) {
                        mediaPlayer.stop();
                    }
                }
            }
        });

        return videos;
    }

    private void launchURL() {
        try {
            new ProcessBuilder(
                    "cmd", "/c", "start chrome --fullscreen https://www.mcdonalds.ro/cariera"
            ).start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void setUpCameraStage() {
        cameraView = new ImageView();
        cameraView.setFitWidth(400);
        cameraView.setFitHeight(500);

        videos = getListView();
        videos.setStyle(
                "-fx-background-color: #34495e; " +
                        "-fx-text-fill: white;"
        );
        videos.setTooltip(new Tooltip("Select a video or 'Your future' to open the browser"));
        media = new Media(new File("media/video.mp4").toURI().toString());
        mediaPlayer = new MediaPlayer(media);
        mediaPlayer.setCycleCount(MediaPlayer.INDEFINITE);

        btnExit.setOnAction(actionEvent -> Platform.exit());
        btnExit.setTooltip(new Tooltip("Click to exit the program"));
        btnExit.setStyle(
                "-fx-background-color: red; " +
                        "-fx-text-fill: white; " +
                        "-fx-font-weight: bold; " +
                        "-fx-font-size: 14px;"
        );

        text.setStyle("-fx-fill: white; -fx-font-size: 16px; -fx-font-weight: bold;");

        VBox controlsVBox = new VBox(10,text,videos, btnExit);
        controlsVBox.setPrefWidth(200);
        controlsVBox.setStyle("-fx-padding: 10; -fx-background-color: blue;");

        cameraView.setStyle("-fx-effect: dropshadow(gaussian, black, 10, 0.5, 0, 0);");

        HBox mainHBox = new HBox(10);
        mainHBox.getChildren().addAll(controlsVBox, cameraView);
        mainHBox.setStyle("-fx-padding: 10;");

        Scene cameraScene = new Scene(mainHBox, 600, 500);
        cameraStage.setScene(cameraScene);
        cameraStage.setTitle("LOOK AT ME!");
        cameraStage.show();
    }

    private void setupVideoStage() {
        if (videoView == null) {
            videoView = new MediaView(mediaPlayer);
            videoView.setFitHeight(600);
            videoView.setFitWidth(300);
            StackPane videoRoot = new StackPane(videoView);
            videoStage.setScene(new Scene(videoRoot, 300, 500));
            videoStage.setX(cameraStage.getX() + 520);
            videoStage.setY(cameraStage.getY());
            videoView.setStyle("-fx-effect: dropshadow(gaussian, black, 20, 0.5, 0, 0);");
            FadeTransition ft = new FadeTransition(Duration.millis(500), videoView);
            ft.setFromValue(0);
            ft.setToValue(1);
            ft.play();
        }
    }

    private void startCameraLoop() {
        cameraThread = new Thread(() -> {
            Mat frame = new Mat();
            Mat grayFrame = new Mat();
            try {
                while (cameraActive && !Thread.currentThread().isInterrupted()) {
                    if (!camera.read(frame) || frame.empty()) break;
                    if (frame.empty()) continue;
                    Mat frameCopy = frame.clone();
                    Imgproc.cvtColor(frame, grayFrame, Imgproc.COLOR_BGR2GRAY);
                    MatOfRect faces = new MatOfRect();
                    faceDetector.detectMultiScale(grayFrame, faces);
                    boolean eyeContact = false;
                    for (Rect face : faces.toArray()) {
                        Mat faceROI = grayFrame.submat(face);
                        MatOfRect eyes = new MatOfRect();
                        eyeDetector.detectMultiScale(faceROI, eyes);
                        if (eyes.toArray().length > 0) {
                            eyeContact = true;
                            break;
                        }
                    }
                    boolean attention = !faces.empty() && eyeContact;
                    Platform.runLater(() -> {
                        cameraView.setImage(matToImage(frameCopy));
                        if (attention) {
                            if (videoStage.isShowing()) {
                                videoStage.hide();
                                mediaPlayer.pause();
                            }
                            if (openBrowser)
                                browserOpened = false;
                        } else {
                            if (openBrowser) {
                                if (!browserOpened) {
                                    launchURL();
                                    browserOpened = true;
                                }
                            } else {
                                if (mediaPlayer != null) {
                                    setupVideoStage();
                                    videoStage.show();
                                    mediaPlayer.play();
                                }
                            }
                        }
                    });
                    try {
                        Thread.sleep(30);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } finally {
                if (camera != null) {
                    try {
                        camera.release();
                    } catch (Exception e) {
                        System.err.println("Camera failes" + e.getMessage());
                    }
                    camera = null;
                }
            }
        });
        cameraThread.setDaemon(true);
        cameraThread.start();
    }

    private Image matToImage(Mat frame) {
        MatOfByte buffer = new MatOfByte();
        Imgcodecs.imencode(".png", frame, buffer);
        return new Image(new ByteArrayInputStream(buffer.toArray()));
    }

    @Override
    public void stop() {
        cameraActive = false;
        if (cameraThread != null) {
            cameraThread.interrupt();

        }

        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.dispose();
            mediaPlayer = null;
        }
        videoView = null;
    }

    public static void main(String[] args) {
        launch(args);
    }
}