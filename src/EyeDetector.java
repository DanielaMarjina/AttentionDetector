import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.stage.Stage;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.videoio.VideoCapture;
import javafx.scene.image.ImageView;
import org.opencv.imgproc.Imgproc;

import java.io.ByteArrayInputStream;
import java.io.File;

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

    @Override
    public void start(Stage primaryStage) throws Exception {
        camera = new VideoCapture(0);
        faceDetector = new CascadeClassifier("haarcascades/haarcascade_frontalface_default.xml");
        eyeDetector = new CascadeClassifier("haarcascades/haarcascade_eye.xml");
        if (!camera.isOpened()) {
            System.out.println("Nu a putut fi conectata camera");
            Platform.exit();
            return;
        }
        cameraView = new ImageView();
        cameraView.setFitWidth(500);
        cameraView.setFitHeight(600);
        Media media = new Media(new File("media/video.mp4").toURI().toString());
        mediaPlayer = new MediaPlayer(media);
        videoView = new MediaView(mediaPlayer);
        videoView.setVisible(false);
        videoView.setFitHeight(600);
        videoView.setFitWidth(500);
        mediaPlayer.setCycleCount(MediaPlayer.INDEFINITE);
        StackPane root = new StackPane();
        root.getChildren().addAll(cameraView, videoView);
        Scene scene = new Scene(root, 500, 600);
        primaryStage.setScene(scene);
        primaryStage.setTitle("LOOK AT ME!");
        primaryStage.show();
        startCameraLoop();
    }

    private void startCameraLoop() {
        cameraThread = new Thread(() -> {
            Mat frame = new Mat();
            Mat grayFrame = new Mat();
            try {
                while (cameraActive && !Thread.currentThread().isInterrupted()) {
                    if (!camera.read(frame) || frame.empty()) break;
                    if (frame.empty()) continue;
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
                        if (attention) {
                            videoView.setVisible(false);
                            mediaPlayer.pause();
                            if (!frame.empty()) cameraView.setImage(matToImage(frame));
                        } else {
                            videoView.setVisible(true);
                            if (mediaPlayer.getStatus() != MediaPlayer.Status.PLAYING) {
                                mediaPlayer.play();
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
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}