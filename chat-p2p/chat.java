import java.io.IOException;
import java.net.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

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

// package-private enum ConnectionMessage
enum ConnectionMessage {
    CONNECT_REQUEST,
    CONNECT_ACK,
    CONNECT_CONFIRM
}

// * PeerServer CLASS TO MANAGE INCOMING CONNECTIONS 
class PeerServer implements Runnable {
    private int port;
    private boolean isConnecting, isConnected;

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
    private Socket newSocket;
    private String peerIP;
    private int myPort;
    private int peerPort;
    private List<String> myIPs;
    private BufferedReader input;
    private PrintWriter output;
    private static final String VALID_IP_REGEX = "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$";
    private static final Pattern VALID_IP_PATTERN = Pattern.compile(VALID_IP_REGEX);
    private static final ConcurrentHashMap<String, Socket> activeConnections = new ConcurrentHashMap<>();

    public PeerClient(String peerIP, int peerPort, int myPort){
        this.peerIP = peerIP ;
        this.peerPort = peerPort;
        this.myPort = myPort;
        this.myIPs = getMyIPs();
    }

    private enum ConnectionState {
        CONNECTING,
        CONNECTED,
        DISCONNECTED
    }

     ConnectionState state = ConnectionState.DISCONNECTED;

    // ! TO BE TESTED
    public List<String> getMyIPs() {
        System.out.println("Starting gathering IP addresses");
        try{
            List<String> ipAddresses = NetworkInterface.networkInterfaces()
            .peek(iface -> System.out.println("\nExamining Interface... " + iface.getName()))
            .filter (iface -> {
                try {
                    return !iface.isLoopback() && iface.isUp();
                } catch (SocketException e){
                    System.out.println("Error checking interface: " + iface.getName() + " : " + e.getMessage());
                    return false;
                }
            })
            .flatMap(iface -> iface.inetAddresses())
            .peek(addr -> System.out.println("Found IP address: "  + addr.getHostAddress()))
            .map(addr -> addr.getHostAddress())
            .collect(Collectors.toList());

            System.out.println("\nFinished gathering IP addresses. Total found: " + ipAddresses.size());
            return ipAddresses;
        }  
        catch (SocketException e){
            System.out.println("Error getting network interfaces: " + e.getMessage());
            return List.of(); // return an empty list in case of error
        }
    }

    // ! TO BE TESTED
    public boolean isConnectionToSelf(String peerIP, int peerPort){
        if(myPort == peerPort && myIPs.contains(peerIP)){
            System.out.println("Attempting to connect to self (same IP and port). Connection aborted.");
            return true;
        }

        if (myIPs.contains(peerIP)) {
            System.out.println("Note: Connecting to own IP address but different port.");
        }

        return false;
    }
    
    @Override 
    public void run() {
        try{
            System.out.println("Initiating connection...");
            connect(peerIP, peerPort, myPort);
        } catch (IOException e){
            System.out.println("Connection failed: " + e.getMessage());
        }
    }

    // TODO: add connection confirmation (success/failure) msg to both peers
    // connect to a peer
    public void connect(String peerIP, int peerPort, int myPort) throws IOException {
        this.peerIP = peerIP;
        this.peerPort = peerPort;
        this.myPort = myPort;
        String connectionKey = peerIP + ":" + peerPort;

        // ! TO BE TESTED
        //  check is the IP address is valid
        if(!isValidIP(peerIP)){
            System.out.println("Not Valid IP address: " + peerIP);
            return;
        }

        // ! TO BE TESTED
        // check if the connection is to self
        if(isConnectionToSelf(peerIP, peerPort)){
            System.out.println("Connection to self detected. Aborting connection.");
            return;
        }
    
        // ! TO BE TESTED
        // check if the connection already exists
        if(activeConnections.containsKey(connectionKey)){
            Socket existingSocket = activeConnections.get(connectionKey);
            if(existingSocket != null && !existingSocket.isClosed()){
                System.out.println("Connection to " + connectionKey + " already exists");
                return;
            } else {
                activeConnections.remove(connectionKey);
            }
        }

        // TODO: Implement connection handshake protocol
        // TODO: Add timeout mechanism for connection attempts
        // TODO: Update connection status based on handshake result
        // TODO: Log success or failure of connection attempt
    
        // * ESTABLISH A CONNECTION TO A PEER 
        // attempt to connect to the specified peer
        newSocket = new Socket(peerIP, peerPort);
        activeConnections.put(connectionKey, newSocket);
        System.out.println("Connected to peer at " + connectionKey);

        // read and write to the newSocket
        input = new BufferedReader(new InputStreamReader(newSocket.getInputStream())); 
        output = new PrintWriter(newSocket.getOutputStream(), true);

        state = ConnectionState.CONNECTING;
        sendConnectionMessage(ConnectionMessage.CONNECT_REQUEST);

        ConnectionMessage response = receiveConnectionMessage();
        if(response == ConnectionMessage.CONNECT_ACK){
            sendConnectionMessage(ConnectionMessage.CONNECT_CONFIRM);
            state = ConnectionState.CONNECTED;
            System.out.println("Connection established with " + peerIP + ":" + peerPort);
        } else {
            state = ConnectionState.DISCONNECTED;
            throw new IOException("Unexpected response during handshake");
        }
        // start a new thread to handle the connection
        new Thread(() -> {
            try {
                String inputLine;
                while ((inputLine = input.readLine()) != null) {
                    System.out.println("Received message: " + inputLine + " from " + peerIP);
                }
            } catch (IOException e) {
                System.out.println("Error reading from newSocket: " + e.getMessage());
            }
        }).start();
    }

    // * METHODS
    // send a message to the connected peer
    public void sendMessage(String message){
        if(output != null){ // check if the output stream is open
            output.println(message); // send the message
            output.flush(); // sent the message immediately
        }
    }

    // ! TO BE TESTED
    // check if the IP address is valid
    public static boolean isValidIP(String peerIP){
        System.out.println("Checking IP address: " + peerIP);
        if (peerIP == null || peerIP.isEmpty()){
            System.out.println("IP address is empty or null");
            return false;
        }   
        return VALID_IP_PATTERN.matcher(peerIP).matches();
    }

    // close the connection to the specified peer
    public void closeConnection(String peerIP, int peerPort) {
        String connectionKey = peerIP + ":" + peerPort;
        Socket newSocket = activeConnections.remove(connectionKey);
        if (newSocket != null) {
            try {
                newSocket.close();
                System.out.println("Closed connection to " + connectionKey);
            } catch (IOException e) {
                System.out.println("Error closing connection: " + e.getMessage());
            }
        }
    }

    public void sendConnectionMessage(ConnectionMessage message){
        if(output != null){ // check if the output stream is open
            output.println(message); // send the message
            output.flush(); // sent the message immediately
            System.out.println("ConnectionMessage sent: " + message);
        }
    }

    public ConnectionMessage receiveConnectionMessage() throws IOException {
        if (input != null) {
            try {
                String inputLine = input.readLine();
                if (inputLine == null) {
                    throw new IOException("Connection closed by peer");
                }
                ConnectionMessage message = ConnectionMessage.valueOf(inputLine);
                System.out.println("Received ConnectionMessage: " + message);
                return message;
            } catch (IllegalArgumentException e) {
                String inputLine = e.getMessage();
                throw new IOException("Invalid connection message received: " + inputLine);
            } catch (IOException e) {
                throw new IOException("Error reading from input stream: " + e.getMessage());
            }
        }
        throw new IOException("Input stream is null");
}


}

//* ConnectionHandler CLASS TO MANAGE INDIVIDUAL PEER CONNECTIONS
class ConnectionHandler implements Runnable {
    private Socket newSocket;

    public ConnectionHandler(Socket newSocket){
        this.newSocket = newSocket;
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