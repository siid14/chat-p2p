import java.io.IOException;
import java.net.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.regex.Pattern;


public class chat {

    // * MAIN CLASS TO INIT AND START P2P CHAT 
    public static void main(String[] args) {

        // check if the port number has been included in command line
        if(args.length != 1){
            System.out.println("Missing port number -- make run <port>");
            return;
        }

        int port = Integer.parseInt(args[0]);  // convert arg to int (in case it's string)

        PeerServer peerServer = new PeerServer(port);
        UserInterface ui = new UserInterface();

        // start PeerServer and UserInterface in separate threads
        new Thread(peerServer).start();
        new Thread(ui).start();
        
    }
}

// * PeerServer CLASS TO MANAGE INCOMING CONNECTIONS 
class PeerServer implements Runnable {
    private int port;

    public PeerServer (int port){
        this.port = port;
    }

    @Override 
    public void run() {
        // create a ServerSocket to listen for incoming connections
        try (ServerSocket serverSocket = new ServerSocket(port)){
            System.out.println("Listening on port " + port);
            while(true){
                // accept incoming connections
                Socket clientSocket = serverSocket.accept();
                System.out.println("New connection from " + clientSocket.getInetAddress());

                // start a new thread to handle connection
                new Thread(new ConnectionHandler(clientSocket)).start();
            }
            } catch(IOException e) {
                System.out.println("Server exception " + e.getMessage());

            }
        }

    }


// * PeerClient CLASS TO MANAGE OUTGOING CONNECTION
class PeerClient implements Runnable {
    private Socket socket;
    private String peerIP;
    private int peerPort;
    private BufferedReader input;
    private PrintWriter output;
    private static final String VALID_IP_REGEX = "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$";
    private static final Pattern VALID_IP_PATTERN = Pattern.compile(VALID_IP_REGEX);

    public PeerClient(String peerIP, int peerPort){
        this.peerIP = peerIP ;
        this.peerPort = peerPort;
    }

    // ! TO BE TESTED
    // check if the IP address is valid
    public static boolean isValidIP(String peerIP){
        if (peerIP == null || peerIP.isEmpty()){
            System.out.println("IP address is empty or null");
            return false;
        }   
        return VALID_IP_PATTERN.matcher(peerIP).matches();
    }

    @Override 
    public void run() {
        connect(peerIP, peerPort);
    }

    // TODO: add check for invalid IP + duplicate connections
    // TODO: add check for connection to self
    // TODO: add connection confirmation msg to both peers
    // connect to a peer
    public void connect(String destination, int port){
        this.peerIP = destination;
        this.peerPort = port;

      

        try {
            // attempt to connect to the specified peer
            socket = new Socket(peerIP, peerPort);

            // read and write to the socket
            input = new BufferedReader(new InputStreamReader(socket.getInputStream())); 
            output = new PrintWriter(socket.getOutputStream(), true);

            System.out.println("Connected to peer at " + destination + ":" + port);
            
 
            // start a new thread to handle the connection
            new Thread(() -> {
                try {
                    String inputLine;
                    while ((inputLine = input.readLine()) != null) {
                        System.out.println("Received message: " + inputLine + " from " + destination);
                    }
                } catch (IOException e) {
                    System.out.println("Error reading from socket: " + e.getMessage());
                }
            }).start();
        } catch (IOException e) {
            System.out.println("Connection failed: " + e.getMessage());
        }
    }

    // send a message to the connected peer
    public void sendMessage(String message){
        if(output != null){
            output.println(message); // send the message
            output.flush(); // sent the message immediately
        }
    }
}

//* ConnectionHandler CLASS TO MANAGE INDIVIDUAL PEER CONNECTIONS
class ConnectionHandler implements Runnable {
    private Socket socket;

    public ConnectionHandler(Socket socket){
        this.socket= socket;

    }

    @Override 
    public void run() {
        // TODO: Implement logic for sending and receiving messages
    }
}

//* UserInterface CLASS TO PROCESS USER COMMANDS AND DISPLAY INFORMATIONS
class UserInterface implements Runnable {

    @Override 
    public void run() {
        // TODO: Implement user interface logic
    }
}