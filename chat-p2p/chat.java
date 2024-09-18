import java.io.IOException;
import java.net.*;
import java.sql.Connection;
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

   
    // connect to a peer
    public void connect(String peerIP, int peerPort, int myPort) throws IOException {
        this.peerIP = peerIP;
        this.peerPort = peerPort;
        this.myPort = myPort;
        String connectionKey = peerIP + ":" + peerPort;

        System.out.println("Attempting to connect to " + connectionKey);

        
        //  check is the IP address is valid
        if(!isValidIP(peerIP)){
            System.err.println("Error: Invalid IP address '" + peerIP + "'. Connection attempt aborted.");
            return;
        }

 
        // check if the connection is to self
        if(isConnectionToSelf(peerIP, peerPort)){
            System.err.println("Error: Attempt to connect to self detected (IP: " + peerIP + ", Port: " + peerPort + "). Connection aborted.");
            return;
        }
    
        // check if the connection already exists
        if(activeConnections.containsKey(connectionKey)){
            Socket existingSocket = activeConnections.get(connectionKey);
            System.out.println("Existing socket found for " + connectionKey);

            if(existingSocket != null && !existingSocket.isClosed()){
                System.err.println("Error: Connection to " + connectionKey + " already exists. Duplicate connection attempt aborted.");
                return;
            } else {
                System.out.println("Removing closed or null socket for " + connectionKey);
                activeConnections.remove(connectionKey);
            }
        }

    
        // * ESTABLISH A CONNECTION TO A PEER 
        // attempt to connect to the specified peer
        newSocket = new Socket(peerIP, peerPort);
        activeConnections.put(connectionKey, newSocket);
        System.out.println("Connected to peer at " + connectionKey);

        // read and write to the newSocket
        input = new BufferedReader(new InputStreamReader(newSocket.getInputStream())); 
        output = new PrintWriter(newSocket.getOutputStream(), true);

        System.out.println("Initiating handshake with peer: " + peerIP + ":" + peerPort);
        state = ConnectionState.CONNECTING;

        // ? (Optional): Implement timeout mechanism for handshake
    

        // ? (Optional): Implement retry mechanism for failed handshakes
    
        sendConnectionMessage(ConnectionMessage.CONNECT_REQUEST);
        System.out.println("Sent CONNECT_REQUEST to peer: " + peerIP + ":" + peerPort);

        ConnectionMessage response = receiveConnectionMessage();
        System.out.println("Received response from peer: " + response);

        if(response == ConnectionMessage.CONNECT_ACK){
            System.out.println("Received CONNECT_ACK from peer: " + peerIP + ":" + peerPort);
            sendConnectionMessage(ConnectionMessage.CONNECT_CONFIRM);
            System.out.println("Sent CONNECT_CONFIRM to peer: " + peerIP + ":" + peerPort);
            state = ConnectionState.CONNECTED;
            System.out.println("Connection established with " + peerIP + ":" + peerPort);
        } else {
            state = ConnectionState.DISCONNECTED;
            System.err.println("Handshake failed. Unexpected response during handshake: " + response);
            throw new IOException("Unexpected response during handshake: " + response);
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
        System.out.println("Attempting to send message: " + message);
        if(output != null){ // check if the output stream is open
            System.out.println("Output stream is not null");
            output.println(message); // send the message
            output.flush(); // sent the message immediately
            System.out.println("Message sent and flushed");
        } else {
            System.out.println("Output stream is null, unable to send message");
        }
    }

  
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
    private BufferedReader input;
    private PrintWriter output;
    private String peerIP;
    private int peerPort;

    private enum ConnectionState {
        CONNECTING,
        CONNECTED,
        DISCONNECTED
    }


    ConnectionState state = ConnectionState.DISCONNECTED;

    public ConnectionHandler(Socket newSocket){
        this.newSocket = newSocket;
        this.peerIP = newSocket.getInetAddress().getHostAddress();
        this.peerPort = newSocket.getPort();
    }

    @Override 
    public void run() {
        System.out.println("Starting connection handler for peer: " + peerIP + ":" + peerPort);
        try{
            setupStreams();
            performHandshake();
            handleMessages();
        } catch (IOException e){
            System.err.println("IO Error in connection handler for " + peerIP + ":" + peerPort + ": " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e){
            System.err.println("Unexpected error in connection handler for " + peerIP + ":" + peerPort + ": " + e.getMessage());
        e.printStackTrace();
        } finally {
            System.out.println("Closing connection with peer: " + peerIP + ":" + peerPort);
            closeConnection();
        }

        
    }

    private void setupStreams() throws IOException {
         // read and write to the newSocket
         input = new BufferedReader(new InputStreamReader(newSocket.getInputStream())); 
         output = new PrintWriter(newSocket.getOutputStream(), true);
    }

    private void performHandshake () throws IOException {
        System.out.println("Starting handshake process with peer: " + peerIP + ":" + peerPort);
        state = ConnectionState.CONNECTING;

        // ? (optional) Implement timeout mechanism for handshake (for socket)
    

        // ? (optional) Implement retry mechanism for failed handshakes
      
        
        ConnectionMessage response = receiveConnectionMessage();
        System.out.println("Received initial message from peer: " + response);
        
        if(response == ConnectionMessage.CONNECT_REQUEST){
            System.out.println("Received CONNECT_REQUEST from peer: " + peerIP + ":" + peerPort);
            sendConnectionMessage(ConnectionMessage.CONNECT_ACK);
            System.out.println("Sent CONNECT_ACK to peer: " + peerIP + ":" + peerPort);

            ConnectionMessage confirm = receiveConnectionMessage();
            System.out.println("Received " + confirm + " from peer: " + peerIP + ":" + peerPort);

            if (confirm == ConnectionMessage.CONNECT_CONFIRM) {
                state = ConnectionState.CONNECTED;
                System.out.println("Handshake completed. Connection established with " + peerIP + ":" + peerPort);
            } else {
                System.err.println("Handshake failed. Unexpected confirmation message: " + confirm);
                throw new IOException("Unexpected confirmation message during handshake: " + confirm);
            }
            } else {
                System.err.println("Handshake failed. Unexpected initial message: " + response);
                throw new IOException("Unexpected initial message during handshake: " + response);
            }
    }

    private void handleMessages() {
        try {
            String inputLine;
            while (state == ConnectionState.CONNECTED && (inputLine = input.readLine()) != null){
                System.out.println("Received from " + peerIP + ":" + peerPort + " - " + inputLine);
            }
        } catch (IOException e) {
            state = ConnectionState.DISCONNECTED;
            System.err.println("Error reading from peer " + peerIP + ":" + peerPort + " - " + e.getMessage());
        }
    }

    private void closeConnection(){
        try {
            if(input != null) input.close();
            if(output != null) output.close();
            if(newSocket != null) newSocket.close();
        } catch (IOException e) {
            System.out.println("Error closing connection: " + e.getMessage());
            state = ConnectionState.DISCONNECTED;
        }
    }

    private void sendConnectionMessage(ConnectionMessage message) throws IOException {
        if (output != null) {
            output.println(message);
            output.flush();
            System.out.println("Sent ConnectionMessage: " + message + " to " + peerIP + ":" + peerPort);
        } else {
            System.err.println("Failed to send ConnectionMessage: output stream is null");
            throw new IOException("Output stream is null");
        }
    }

    private ConnectionMessage receiveConnectionMessage() throws IOException {
        if (input != null) {
            try {
                String inputLine = input.readLine();
                if (inputLine == null) {
                    System.err.println("Connection closed by peer while waiting for ConnectionMessage");
                    throw new IOException("Connection closed by peer");
                }
                ConnectionMessage message = ConnectionMessage.valueOf(inputLine);
                System.out.println("Received ConnectionMessage: " + message + " from " + peerIP + ":" + peerPort);
                return message;
            } catch (IllegalArgumentException e) {
                System.err.println("Received invalid ConnectionMessage: " + e.getMessage());
                throw new IOException("Invalid connection message received: " + e.getMessage());
            } catch (IOException e) {
                System.err.println("Error reading ConnectionMessage from input stream: " + e.getMessage());
                throw new IOException("Error reading from input stream: " + e.getMessage());
            }
        }
        System.err.println("Failed to receive ConnectionMessage: input stream is null");
        throw new IOException("Input stream is null");
    }
}

//* UserInterface CLASS TO PROCESS USER COMMANDS AND DISPLAY INFORMATIONS
class UserInterface implements Runnable {
    @Override 
    public void run() {
        // TODO: Implement user interface logic
    }
}