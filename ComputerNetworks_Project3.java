package computernetworks_project3;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Optional;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Pair;

public class ComputerNetworks_Project3 extends Application {

    public Stage stage;
    public Scene scene;
    public BorderPane bp = new BorderPane();
    public VBox vbox1 = new VBox(), vbox2 = new VBox();
    public ScrollPane scrollPane = new ScrollPane();

    public MenuBar menuBar = new MenuBar();
    public Menu menu;
    public MenuItem connect, wait, changeNick, disconnect;

    public Label label;

    public static boolean connected = false;

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;
        ChatState.historyTextArea = new TextArea();
        ChatState.sendMessageTextField = new TextField();
        ChatState.sendMessageTextField.setDisable(true);

        initClientGUI();

        bp.setTop(menuBar);
        bp.setCenter(vbox1);
        bp.setBottom(vbox2);

        bp.setPrefSize(430, 330);
        bp.setPadding(new Insets(10));

        scene = new Scene(bp, 430, 330);
        stage.setScene(scene);
        stage.setTitle("CSC 469 FileServer Client");
        
        scene.addEventHandler(KeyEvent.KEY_PRESSED, (key) -> {
           if(key.getCode() == KeyCode.ENTER) {
               if(connected)
                   sendAction();
           } 
        });
        
        stage.show();
        ChatState.netReaderThread = new Thread();

    }

    public void initClientGUI() {
        ChatState.sendMessageButton = new Button("Send Command");
        ChatState.sendMessageButton.setDisable(true);

        label = new Label("Server Interaction History");

        connect = new MenuItem("Connect to a waiting chat partner...");
        wait = new MenuItem("Wait for a chat partner...");
        changeNick = new MenuItem("Change your nick name");
        disconnect = new MenuItem("Disconnect and Exit");
        menu = new Menu("Chat");

        menu.getItems().addAll(wait, connect, changeNick, disconnect);

        wait.setOnAction((ActionEvent evt) -> {
            waitAction();
        });

        connect.setOnAction((ActionEvent evt) -> {
            connectAction();
        });

        disconnect.setOnAction((ActionEvent evt) -> {
            disconnectAction();
        });

        changeNick.setOnAction((ActionEvent evt) -> {
            nickAction();
        });

        menuBar.getMenus().addAll(menu);

        ChatState.historyTextArea.setPrefSize(200, 200);
        ChatState.historyTextArea.setEditable(false);

        scrollPane.setPrefSize(200, 200);
        scrollPane.setFitToHeight(true);
        scrollPane.setFitToWidth(true);
        scrollPane.setContent(ChatState.historyTextArea);

        ChatState.sendMessageButton.setOnAction((ActionEvent evt) -> {
            sendAction();
        });
        
        vbox1.setAlignment(Pos.CENTER);
        vbox1.getChildren().addAll(label, scrollPane);

        vbox2.setAlignment(Pos.CENTER);
        vbox2.getChildren().addAll(ChatState.sendMessageTextField, ChatState.sendMessageButton);
    }

    public static void main(String[] args) {
        launch(args);
    }

    public void nickAction() {
        if(connected) {
            GridPane grid = new GridPane();
            ButtonType okay = new ButtonType("Okay", ButtonBar.ButtonData.OK_DONE);
            Dialog dialog = new Dialog();
            dialog.setTitle(stage.getTitle());
            dialog.setHeaderText("Change your nickname");

            grid.setHgap(10);
            grid.setVgap(10);
            grid.setPadding(new Insets(20, 150, 10, 10));

            Label nickPrompt = new Label("Nickname:");
            TextField nickname = new TextField("");

            grid.add(nickPrompt, 0, 0);
            grid.add(nickname, 1, 0);

            dialog.getDialogPane().setContent(grid);
            dialog.getDialogPane().getButtonTypes().addAll(okay);

            dialog.setResultConverter(dialogButton -> {
                return nickname.getText();
            });

            Optional<String> result = dialog.showAndWait();

            ChatState.localNickName = result.get() + ">";
            ChatState.sendMessage("nickname " + result.get() + "\n");
            ChatState.historyTextArea.appendText(ChatState.localNickName + "I changed my name to " + result.get() + "\n");
        }
    }

    public void waitAction() {
        if(!connected) {
            try {
                ChatState.socket = new DatagramSocket(50001);
                ChatState.isServer = true;
                ChatState.localNickName = "S>";
                ChatState.remoteNickName = "C>";
                ChatState.netReaderThread = new Thread(new NetworkThread());
                ChatState.netReaderThread.start();
            }
            catch(IOException ex) {
                Logger.getLogger(ComputerNetworks_Project3.class.getName()).log(Level.SEVERE, null, ex);
                throw new RuntimeException(ex);
            }
        }
    }

    public void connectAction() {
        if(!connected) {
            GridPane grid = new GridPane();
            ButtonType okay = new ButtonType("Okay", ButtonBar.ButtonData.OK_DONE);
            Dialog dialog = new Dialog();
            dialog.setTitle(stage.getTitle());
            dialog.setHeaderText("Enter Server Connection Information");

            grid.setHgap(10);
            grid.setVgap(10);
            grid.setPadding(new Insets(20, 150, 10, 10));

            Label ipPrompt = new Label("IP Address:");
            Label portPrompt = new Label("Port:");
            TextField ipAddress = new TextField("localhost");
            TextField portNumber = new TextField("50001");

            grid.add(ipPrompt, 0, 0);
            grid.add(ipAddress, 1, 0);
            grid.add(portPrompt, 0, 1);
            grid.add(portNumber, 1, 1);

            dialog.getDialogPane().setContent(grid);
            dialog.getDialogPane().getButtonTypes().addAll(okay);

            dialog.setResultConverter(dialogButton -> {
                return new Pair<>(ipAddress.getText(), Integer.parseInt(portNumber.getText()));
            });

            Optional<Pair<String, Integer>> result = dialog.showAndWait();

            result.ifPresent(connectToServer -> {
                try {
                    ChatState.socket = new DatagramSocket();
                    ChatState.remotePort = connectToServer.getValue();
                    ChatState.remoteIpAddress = InetAddress.getByName(connectToServer.getKey());

                    ChatState.localNickName = "C>";
                    ChatState.remoteNickName = "S>";

                    ChatState.sendMessage("hello Hello\n");
                    ChatState.historyTextArea.appendText(ChatState.localNickName + "Hello\n");

                    ChatState.netReaderThread = new Thread(new NetworkThread());
                    ChatState.netReaderThread.start();

                    ChatState.sendMessageButton.setDisable(false);
                    ChatState.sendMessageTextField.setDisable(false);
                    connected = true;
                }
                catch(IOException ex) {
                    Logger.getLogger(ComputerNetworks_Project3.class.getName()).log(Level.SEVERE, null, ex);
                    throw new RuntimeException(ex);
                }
            });
        }
    }

    public void disconnectAction() {
        if(connected) {
            ChatState.sendMessage("bye");
            ChatState.socket.close();
        }
        System.exit(0);
    }

    public void sendAction() {
        ChatState.sendMessage("chat " + ChatState.sendMessageTextField.getText());
        ChatState.historyTextArea.appendText(ChatState.localNickName + ChatState.sendMessageTextField.getText() + "\n");
        ChatState.sendMessageTextField.clear();
    }

}

class ChatState {

    public static Button sendMessageButton = null;
    public static TextArea historyTextArea = null;
    public static TextField sendMessageTextField = null;
    public static DatagramSocket socket = null;
    public static Thread netReaderThread = null;
    public static boolean isServer = false;
    public static InetAddress remoteIpAddress = null;
    public static int remotePort;
    public static String remoteNickName;
    public static String localNickName;

    public static void sendNickNameChange() {
        String message = String.format("nickname %s", localNickName);
        sendMessage(message);
    }

    public static void sendMessage(String message) {
        byte[] messageBytes = message.getBytes();
        DatagramPacket packet = new DatagramPacket(messageBytes,
                messageBytes.length,
                remoteIpAddress,
                remotePort);
        try {
            socket.send(packet);
        }
        catch(IOException ex) {
            Logger.getLogger(ChatState.class.getName())
                    .log(Level.SEVERE, null, ex);
            throw new RuntimeException(ex);
        }
    }
}

class NetworkThread implements Runnable {

    @Override
    public void run() {
        while(true) {
            byte[] buffer = new byte[4096];
            DatagramPacket recvPacket = new DatagramPacket(buffer, buffer.length);
            try {
                ChatState.socket.receive(recvPacket);
            }
            catch(IOException ex) {
                Logger.getLogger(ChatState.class.getName())
                        .log(Level.SEVERE, null, ex);
                throw new RuntimeException(ex);
            }
            String message = new String(recvPacket.getData(), 0, recvPacket.getLength());
            Scanner sc = new Scanner(message);
            String opCode = sc.next().toLowerCase();
            switch(opCode) {
                case "hello":
                    if(ChatState.isServer) {
                        ChatState.remoteIpAddress = recvPacket.getAddress();
                        ChatState.remotePort = recvPacket.getPort();
                        ComputerNetworks_Project3.connected = true;
                        
                        if(sc.hasNext()) {
                            Platform.runLater(new Runnable() {
                                @Override
                                public void run() {
                                    ChatState.sendMessageButton.setDisable(false);
                                    ChatState.sendMessageTextField.setDisable(false);

                                    ChatState.historyTextArea.appendText(ChatState.remoteNickName
                                            + sc.next() + "\n");
                                }
                            });
                        }
                        ChatState.sendMessage("hello");
                    }
                    continue;
                case "nickname":
                    String newName = sc.next();
                    ChatState.remoteNickName = newName + ">";
                    Platform.runLater(new Runnable() {
                        @Override
                        public void run() {
                            ChatState.historyTextArea.appendText(ChatState.remoteNickName
                                    + "I changed my name to " + newName + "\n");
                        }
                    });
                    continue;
                case "bye":
                    Platform.runLater(new Runnable() {
                        @Override
                        public void run() {
                            Alert alert = new Alert(AlertType.INFORMATION);
                            alert.setTitle("Disconnection");
                            alert.setHeaderText(null);
                            alert.setContentText("The other user has disconnected");

                            Optional<ButtonType> result = alert.showAndWait();

                            ChatState.socket.close();
                            System.exit(0);
                        }
                    });
                    continue;
                case "chat":
                default:
                    String userMessage = sc.nextLine();
                    Platform.runLater(new Runnable() {
                        @Override
                        public void run() {
                            ChatState.historyTextArea.appendText(ChatState.remoteNickName
                                    + userMessage.trim() + "\n");
                        }
                    });
                    continue;
            }
        }
    }

}
