import java.io.IOException;
import java.net.*;
import java.sql.Connection;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentHashMap.KeySetView;
import java.util.Arrays;
import java.util.logging.*;

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
        UserInterface ui = new UserInterface(port);

        // start PeerServer and UserInterface in separate threads
        new Thread(peerServer).start();
        new Thread(ui).start();
    }

        // inner class for ConnectionManager
    static class ConnectionManager {
        public static final String connectionTerminator = "\u001a";
        public static final ConcurrentHashMap<String, Socket> activeConnections = new ConcurrentHashMap<>();
        public static final int MAX_CONNECTIONS = 3;
    
            // check if a new connection can be accepted
        public static boolean canAcceptNewConnection() {
            return activeConnections.size() < MAX_CONNECTIONS;
        }
    
            // add a connection to the active connections
        public static void addConnection(String key, Socket socket) {
            activeConnections.put(key, socket);
            System.out.println("\nAdded connection: " + key + ". Total connections: " + activeConnections.size());
        }

        // remove a connection from the active connections
        public static void removeConnection(String key) {
            activeConnections.remove(key);
            System.out.println("\nRemoved connection: " + key + ". Total connections: " + activeConnections.size());
        }

        // get a view of the active connections
        public static KeySetView<String, Socket> getActiveConnections() {
            return activeConnections.keySet();
        }

        // send message to peer
        public static void sendMessage(String key, String message) throws IOException {
            Socket socket = activeConnections.get(key);
            if (socket != null) {
                PrintWriter output = new PrintWriter(socket.getOutputStream(), true);
                output.println(message);
                output.flush();
                System.out.println( "Message sent to " + key );
            } else {
                throw new IOException("No active connection for key: " + key);
            }
        
        }
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
    private static final Logger logger = Logger.getLogger(PeerServer.class.getName());
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
                String connectionKey = clientSocket.getInetAddress().getHostAddress() + ":" + clientSocket.getPort();

               if (chat.ConnectionManager.canAcceptNewConnection()) {
                    System.out.println("New connection from " + connectionKey);
                    chat.ConnectionManager.addConnection(connectionKey, clientSocket);
                    new Thread(new ConnectionHandler(clientSocket)).start();
                } else {
                    System.out.println("Maximum connections reached. Rejecting connection from " + connectionKey);
                    clientSocket.close();
                }
            }
        } catch (BindException be) {
            logger.severe("Port already in use: " + port);
            System.err.println("Server failed to start: Port " + port + " is already in use.");
        } catch (IOException e) {
            logger.severe("Server exception: " + e.getMessage());
            System.err.println("Server exception: " + e.getMessage());
        } finally {
            System.out.println("Server has stopped listening on port " + port); // Optional: Handle server shutdown logic here
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
        System.out.println("-----------------------------------------------------------");
        System.out.println("Starting gathering IP addresses");
        try{
            List<String> ipAddresses = NetworkInterface.networkInterfaces()
            // filter out loopback interfaces and interfaces that are not up
            .peek(iface -> System.out.println("\nExamining Interface... " + iface.getName()))
            .filter (iface -> {
                try {
                    return !iface.isLoopback() && iface.isUp();
                } catch (SocketException e){
                    System.out.println("Error checking interface: " + iface.getName() + " : " + e.getMessage());
                    return false;
                }
            })
            // get all IP addresses associated with the interface
            .flatMap(iface -> iface.inetAddresses()) 
            // print each IP address found
            .peek(addr -> System.out.println("Found IP address: "  + addr.getHostAddress()))
             // convert InetAddress objects to String representations of IP addresses
            .map(addr -> addr.getHostAddress())
            // collect the results into a List
            .collect(Collectors.toList());

            System.out.println("\nFinished gathering IP addresses. Total found: " + ipAddresses.size());
            System.out.println("-----------------------------------------------------------\n");
            return ipAddresses;
        }  
        catch (SocketException e){
            System.out.println("Error getting network interfaces: " + e.getMessage());
            return List.of(); // return an empty list in case of error
        }
    }


    public boolean isConnectionToSelf(String peerIP, int peerPort){
        if(myPort == peerPort && myIPs.contains(peerIP)){
            System.out.println("-----------------------------------------------------------");
            System.out.println("Attempting to connect to self (same IP and port). Connection aborted.");
            System.out.println("-----------------------------------------------------------\n");
            return true;
        }

        if (myIPs.contains(peerIP)) {
            System.out.println("-----------------------------------------------------------");
            System.out.println("Note: Connecting to own IP address but different port.");
            System.out.println("-----------------------------------------------------------\n");
        }

        return false;
    }
    
    @Override 
    public void run() {
        try{
            System.out.println("Initiating connection...\n");
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

        // check if the maximum number of connections has been reached
        if (!chat.ConnectionManager.canAcceptNewConnection()) {
            System.out.println("Error: Maximum number of connections (" + chat.ConnectionManager.MAX_CONNECTIONS + ") reached. Cannot connect to " + peerIP + ":" + peerPort);
            return;
        }
    
         // check if the connection already exists
         if(chat.ConnectionManager.activeConnections.containsKey(connectionKey)){
            Socket existingSocket = chat.ConnectionManager.activeConnections.get(connectionKey);
            System.out.println("Existing socket found for " + connectionKey);

            // if the existing socket is not null and is still open
            if(existingSocket != null && !existingSocket.isClosed()){
                System.err.println("Error: Connection to " + connectionKey + " already exists. Duplicate connection attempt aborted.");
                return;
            } else {
                // if the existing socket is null or closed, remove it from active connections
                System.out.println("Removing closed or null socket for " + connectionKey);
                chat.ConnectionManager.removeConnection(connectionKey);
            }
        }


    
        // * ESTABLISH A CONNECTION TO A PEER 
        // attempt to connect to the specified peer
        newSocket = new Socket(peerIP, peerPort);
        chat.ConnectionManager.addConnection(connectionKey, newSocket);
        System.out.println("Connected to peer at " + connectionKey);

        // read and write to the newSocket
        input = new BufferedReader(new InputStreamReader(newSocket.getInputStream())); 
        output = new PrintWriter(newSocket.getOutputStream(), true);

        System.out.println("\n-----------------PeerClient-Handshake-Process------------------------------------------\n");
        System.out.println("Initiating handshake with peer: " + peerIP + ":" + peerPort + "\n");

        // ? (Optional): Implement timeout mechanism for handshake
    

        // ? (Optional): Implement retry mechanism for failed handshakes
    
        sendConnectionMessage(ConnectionMessage.CONNECT_REQUEST);
        System.out.println("Sent CONNECT_REQUEST to peer: " + peerIP + ":" + peerPort + "\n");

        ConnectionMessage response = receiveConnectionMessage();
        System.out.println("Received response from peer: " + response + "\n");

        if(response == ConnectionMessage.CONNECT_ACK){
            System.out.println("Received CONNECT_ACK from peer: " + peerIP + ":" + peerPort);
            sendConnectionMessage(ConnectionMessage.CONNECT_CONFIRM);
            System.out.println("Sent CONNECT_CONFIRM to peer: " + peerIP + ":" + peerPort);
            state = ConnectionState.CONNECTED;
            System.out.println("\nConnection established with " + peerIP + ":" + peerPort);
            System.out.println("-----------------------------------------------------------\n");
        } else {
            state = ConnectionState.DISCONNECTED;
            System.err.println("Handshake failed. Unexpected response during handshake: " + response);
            throw new IOException("Unexpected response during handshake: " + response);
        }
        // start a new thread to handle the connection
        new Thread(() -> {
            try {
                String inputLine = null;
                while ((inputLine = input.readLine()) != null) {
                    if (inputLine.equals(chat.ConnectionManager.connectionTerminator)) {
                        throw new IllegalArgumentException();
                    }
                    System.out.println("Message received from " + peerIP);
                    System.out.println("Sender's Port: " + peerPort);
                    System.out.println("Message: \"" + inputLine + "\"");
                } 
            } catch (IOException e) {
                System.out.println("Error reading from newSocket: " + e.getMessage());
            } catch (IllegalArgumentException iae) {
                chat.ConnectionManager.removeConnection(connectionKey);
                System.out.println("Disconnected " + peerIP + ":" + peerPort);
                return;
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
        System.out.println("Checking IP address: " + peerIP + "\n");
        if (peerIP == null || peerIP.isEmpty()){
            System.out.println("IP address is empty or null" + "\n");
            return false;
        }   
        return VALID_IP_PATTERN.matcher(peerIP).matches();
    }

  
    // close the connection to the specified peer
    public void closeConnection(String peerIP, int peerPort) {
        String connectionKey = peerIP + ":" + peerPort;
        Socket socket = chat.ConnectionManager.activeConnections.get(connectionKey);
        if (socket != null) {
            try {
                socket.close();
                chat.ConnectionManager.removeConnection(connectionKey);
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
            System.out.println("ConnectionMessage sent: " + message );
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
        try {
            setupStreams(); // set up input and output streams
            performHandshake(); // perform the connection handshake
            handleMessages(); // handle incoming messages
        } catch (IOException e){
            System.err.println("IO Error in connection handler for " + peerIP + ":" + peerPort + ": " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e){
            System.err.println("Unexpected error in connection handler for " + peerIP + ":" + peerPort + ": " + e.getMessage());
        e.printStackTrace();
        } finally {
            // ensure the connection is closed when done
            System.out.println("Closing connection with peer: " + peerIP + ":" + peerPort);
            closeConnection();
        }
    }

    // * METHODS
    private void setupStreams() throws IOException {
         // read and write to the newSocket
         input = new BufferedReader(new InputStreamReader(newSocket.getInputStream())); 
         output = new PrintWriter(newSocket.getOutputStream(), true);
    }

    // perform the connection handshake
    private void performHandshake () throws IOException {
        System.out.println("-----------------ConnectionHandler-Handshake-Process------------------------------------------");
        System.out.println("Starting handshake process with peer: " + peerIP + ":" + peerPort);
        state = ConnectionState.CONNECTING;

        // ? (optional) Implement timeout mechanism for handshake (for socket)
    

        // ? (optional) Implement retry mechanism for failed handshakes
      
        // receive the initial connection message from the peer
        ConnectionMessage response = receiveConnectionMessage();
        System.out.println("Received initial message from peer: " + response);
        
        if(response == ConnectionMessage.CONNECT_REQUEST){
            System.out.println("Received CONNECT_REQUEST from peer: " + peerIP + ":" + peerPort);
            sendConnectionMessage(ConnectionMessage.CONNECT_ACK);
            System.out.println("Sent CONNECT_ACK to peer: " + peerIP + ":" + peerPort);

            // wait for confirmation from peer
            ConnectionMessage confirm = receiveConnectionMessage();
            System.out.println("Received " + confirm + " from peer: " + peerIP + ":" + peerPort);

            if (confirm == ConnectionMessage.CONNECT_CONFIRM) {
                state = ConnectionState.CONNECTED;
                System.out.println("Handshake completed. Connection established with " + peerIP + ":" + peerPort);
                System.out.println("-----------------------------------------------------------\n");
                
            } else {
                System.err.println("Handshake failed. Unexpected confirmation message: " + confirm);
                throw new IOException("Unexpected confirmation message during handshake: " + confirm);
            }
            } else {
                System.err.println("Handshake failed. Unexpected initial message: " + response);
                throw new IOException("Unexpected initial message during handshake: " + response);
            }
    }

    // handle incoming messages after connection is established
    private void handleMessages() {
        try {
            String inputLine = null; 
            while (state == ConnectionState.CONNECTED && (inputLine = input.readLine()) != null){
                if (inputLine.equals(chat.ConnectionManager.connectionTerminator)) {
                    throw new IllegalArgumentException();
                } 
                System.out.println("Message received from " + peerIP);
                System.out.println("Sender's Port: " + peerPort);
                System.out.println("Message: \"" + inputLine + "\"");
            }
        } catch (IOException e) {
            state = ConnectionState.DISCONNECTED;
            System.err.println("Error reading from peer " + peerIP + ":" + peerPort + " - " + e.getMessage());
        } catch (IllegalArgumentException iae) {
            state = ConnectionState.DISCONNECTED;
        }
    }

    // close all resources associated with this connection
    private void closeConnection(){
        try {
            if(input != null) input.close();
            if(output != null) output.close();
            if(newSocket != null) newSocket.close();
            chat.ConnectionManager.removeConnection(peerIP + ":" + peerPort);
        } catch (IOException e) {
            System.out.println("Error closing connection: " + e.getMessage());
            state = ConnectionState.DISCONNECTED;
        }
    }

    // send a connection message to the peer
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
    private Scanner scanner;
    private int myPort;

    // Retrieve and print the list of active connections
    KeySetView<String, Socket> activeConnections = chat.ConnectionManager.getActiveConnections();

    public UserInterface(int myPort) {
        scanner = new Scanner(System.in);
        this.myPort = myPort;
    }

    @Override 
    public void run() {
      
        System.out.println("Type /help for a list of available commands.");

        while(true){
            System.out.print(">> ");
            String input = scanner.nextLine();
            String[] parts = input.split("\\s+"); // split input into parts

            switch(parts[0]){
                case "/help":
                    displayHelp();
                    break;
                case "/myip":
                    try {
                        System.out.println(getWiFiIP());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    break;
                case "/list":
                    
                    if(activeConnections.isEmpty()){
                        System.out.println("No active connections.");
                    } else {
                            System.out.println("Active Connections:");
                            int connectionNumber = 1;
                            for (String connection : activeConnections) {
                                String[] connectionParts = connection.split(":");
                                if (connectionParts.length == 2) {
                                    System.out.printf("%d: %s\t\t%s%n", connectionNumber, connectionParts[0], connectionParts[1]);
                                    connectionNumber++;
                                }
                            }
                    }
                    break;
                case "/connect":
                    if (parts.length == 3) {
                        String peerIP = parts[1];
                        int peerPort = Integer.parseInt(parts[2]);
                        System.out.println("Connecting to " + peerIP + ":" + peerPort);
                        connectToPeer(peerIP, peerPort);
                    } else {
                        System.out.println("Usage: /connect <destination> <port no>");
                    }
                    break;
                case "/send":
                    if(parts.length > 2) {
                        int connectionID = Integer.parseInt(parts[1]);
                        if(connectionID > 0 && connectionID <= activeConnections.size()){
                            String[] connectionArray = activeConnections.toArray(new String[0]);
                            String peerKey = connectionArray[connectionID - 1];

                            String message = String.join(" ", Arrays.copyOfRange(parts,2,parts.length));
                            if(message.length() > 100){
                                System.out.println("Message is too long. Should be 100 characters in length");
                            }else{
                                try {
                                    // Use ConnectionManager to send the message
                                    chat.ConnectionManager.sendMessage(peerKey, message);
                                } catch (IOException e) {
                                    System.err.println("Error sending message: " + e.getMessage());
                                }
                            }


                        }
                    }else{
                        System.out.println("Usage: /send <connectionID> <message>");
                    }
                    break;

                case "/terminate":
                    if (parts.length == 2) {
                        try {
                            int connectionID = Integer.parseInt(parts[1]);
                            terminateConnection(connectionID);
                        } catch (NumberFormatException e) {
                            System.out.println("Invalid connection ID. Please provide a valid number.");
                        }
                    } else {
                        System.out.println("Usage: /terminate <connection_id>");
                    }
                    break;
                case "/myport":
                    System.out.println("Server is listening on port: " + myPort);
                    break;
                case "/exit":
                    try {
                        broadcastExitMessage();
                    } catch (IOException e) {
                        e.printStackTrace();
                        System.exit(1);
                    }
                    System.exit(0);
                    break;        
                default:
                    System.out.println("Unknown command. Type /help for a list of commands.");
            }  
        }
    }

    private void connectToPeer(String peerIP, int peerPort) {
        PeerClient client = new PeerClient(peerIP, peerPort, myPort);
        new Thread(client).start();
    }

    private void displayHelp(){
        System.out.println("Information about built in commands: \n\n");
        System.out.println("\t/help: Displays information about the available user interface options or manual.\n");
        System.out.println("\t/myip: Display the IP address of this process.\n");
        System.out.println("\t/myport: Display the port on which this process is listening for incoming connections.\n");
        System.out.println("\t/connect <destination> <port no>: This command establishes a new TCP connection to the specified <destination> at the specified <port_no>. The <destination> is the IP address of the computer.\n");
        System.out.println("\t/list: Display a numbered list of all the connections this process is apart of.\n");
        System.out.println("\t/terminate <connection_id>: This command will terminate the connection listed under the specified number when LIST is used to display all connections.\n");
        System.out.println("\t/send <connection_id> <message>: This will send the message to the host on the connection that is designated. The message to be sent can be up-to 100 characters long, including blank spaces.\n");
        System.out.println("\t/exit: Close all connections and terminate this process.\n");
    }

    public String getWiFiIP() {
        System.out.println("-----------------------------------------------------------");
        System.out.println("Starting to find WiFi IP address");
        try {
             // stream through all network interfaces
            Optional<String> wifiIP = NetworkInterface.networkInterfaces()
            // for each interface, print its details
                .peek(iface -> {
                    try {
                        System.out.println("\nExamining Interface: " + iface.getName() +
                            " (Display name: " + iface.getDisplayName() + ")" +
                            " Is up: " + iface.isUp() +
                            " Is loopback: " + iface.isLoopback() +
                            " Is WiFi: " + isWiFiInterface(iface));
                    } catch (SocketException e) {
                        System.out.println("Error checking interface: " + iface.getName() + " : " + e.getMessage());
                    }
                })
                // filter out interfaces that are not WiFi, are loopback, or are not up
                .filter(iface -> {
                    try {
                        return !iface.isLoopback() && iface.isUp() && isWiFiInterface(iface);
                    } catch (SocketException e) {
                        System.out.println("Error filtering interface: " + iface.getName() + " : " + e.getMessage());
                        return false;
                    }
                })
                // get all IP addresses for the remaining interfaces
                .flatMap(iface -> iface.inetAddresses())
                // filter for IPv4 addresses that are not loopback
                .filter(addr -> addr instanceof Inet4Address && !addr.isLoopbackAddress())
                // convert InetAddress to String representation
                .map(InetAddress::getHostAddress)
                // print each potential WiFi IP address found
                .peek(addr -> System.out.println("Found potential WiFi IP address: " + addr))
                // take the first IP address found (assumig it's wifi connection)
                .findFirst();
    
            System.out.println("\nFinished searching for WiFi IP address.");
            System.out.println("-----------------------------------------------------------\n");
            return wifiIP.orElse("No WiFi IP address found");
        } catch (SocketException e) {
            System.out.println("Error getting network interfaces: " + e.getMessage());
            return "Error: " + e.getMessage();
        }
    }
    
    private boolean isWiFiInterface(NetworkInterface iface) throws SocketException {
        String name = iface.getName().toLowerCase();
        String displayName = iface.getDisplayName().toLowerCase();
        
        // common WiFi interface names across various operating systems
        return
            // generic
            name.contains("wlan") || name.contains("wifi") || name.contains("wi-fi") ||
            displayName.contains("wireless") || displayName.contains("wi-fi") || displayName.contains("wifi") ||
            
            // windows
            name.startsWith("wlan") || name.contains("wireless") ||
            
            // macOS
            name.equals("en0") || displayName.contains("airport") ||
            
            // linux
            name.startsWith("wlp") || name.startsWith("wlo") || name.startsWith("wlx") ||
            
            // freeBSD
            name.startsWith("wlan") ||
            
            // android
            name.startsWith("wlan") ||
            
            // iOS
            name.startsWith("en") ||
            
            // solaris
            name.startsWith("wlan") ||
            
            // virtual WiFi interfaces
            name.contains("vwifi");
    }
    
    private void terminateConnection(int connectionID) {
         // get the set of active connections
        KeySetView<String, Socket> activeConnections = chat.ConnectionManager.getActiveConnections();
        
        // check if the provided connection ID is valid
        if (connectionID <= 0 || connectionID > activeConnections.size()) {
            System.out.println("Error: Invalid connection ID. Use /list to see available connections.");
            return;
        }

        // variables to store the connection key and iterate through connections
        String connectionKey = null;
        int currentID = 1;

         // iterate through active connections to find the matching connection key
        try {
            for (String key : activeConnections) {
                if (currentID == connectionID) {
                    connectionKey = key;
                    break;
                }
                currentID++;
            }
        } catch (Exception e) {
            System.out.println("Error while iterating over connections: " + e.getMessage());
            return;
        }

        // if a matching connection key was found
        if (connectionKey != null) {
            // get the socket associated with the connection key
            Socket socket = chat.ConnectionManager.activeConnections.get(connectionKey);
            if (socket != null) {
                try {
                    chat.ConnectionManager.sendMessage(connectionKey, chat.ConnectionManager.connectionTerminator);
                    socket.close();  // close the socket
                    // remove the connection from the ConnectionManager
                    chat.ConnectionManager.removeConnection(connectionKey);
                    System.out.println("Connection " + connectionID + " (" + connectionKey + ") terminated successfully.");
                } catch (IOException e) {
                    System.out.println("Error closing connection: " + e.getMessage());
                }
            } else {
                System.out.println("Error: Connection " + connectionID + " not found.");
            }
        } else {
            System.out.println("Error: Connection " + connectionID + " not found.");
        }
    }

    private void broadcastExitMessage() throws IOException {
        KeySetView<String, Socket> activeConnections = chat.ConnectionManager.getActiveConnections();
    
        for (String connectionKey : activeConnections) {
            chat.ConnectionManager.sendMessage(connectionKey, chat.ConnectionManager.connectionTerminator);
        }
    }

}

