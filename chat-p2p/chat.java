import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.net.InetAddress;
import java.net.UnknownHostException;

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

public List<String> getMyIPs()
{
    System.out.println("Starting gathering IP addresses");
    try{
        List<String> ipAdresses = NetworkInterface.networkInterfaces()
        // stream through all network interfaces
        .peek(iface -> System.out.println("\nExamining Interface... " + iface.getName()))
        // filter out loopback interfaces and interfaces that are down
        .filter (iface -> {
            try {
                return !iface.isLoopback() && iface.isUp();
            } catch (SocketException e){
                System.out.println("Error checking interface: " + iface.getName() + " : " + e.getMessage());
                return false;
            }
        })
         // get all IP addresses associated with each interface
        .flatMap(iface -> iface.inetAddresses())
        // log each IP address found
        .peek(addr -> System.out.println("Found IP address: "  + addr.getHostAddress()))
        // convert InetAddress objects to string representations
        .map(addr -> addr.getHostAddress())
         // collect all IP addresses into a list
        .collect(Collectors.toList());

        System.out.println("\nFinished gathering IP addresses. Total found: " + ipAdresses.size());
        return ipAdresses;
    }  
    catch (SocketException e){
        System.out.println("Error getting network interfaces: " + e.getMessage());
        return List.of(); // return an empty list in case of error
    }
}

    
    

    @Override 
    public void run() {
        connect(peerIP, peerPort, myPort);
    }

    // TODO: add connection confirmation msg to both peers
    // connect to a peer
    public void connect(String peerIP, int peerPort, int myPort){
        this.peerIP = peerIP;
        this.peerPort = peerPort;
        this.myPort = myPort;
        String connectionKey = peerIP + ":" + peerPort;

        //  check is the IP address is valid
        if(!isValidIP(peerIP)){
            System.out.println("Not Valid IP address: " + peerIP);
            return;
        }

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
     
        // * ESTABLISH A CONNECTION TO A PEER 
        try {
            // attempt to connect to the specified peer
            newSocket = new Socket(peerIP, peerPort);
            activeConnections.put(connectionKey, newSocket);
            System.out.println("Connected to peer at " + connectionKey);

            // read and write to the newSocket
            input = new BufferedReader(new InputStreamReader(newSocket.getInputStream())); 
            output = new PrintWriter(newSocket.getOutputStream(), true);

            
            
 
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
        } catch (IOException e) {
            System.out.println("Connection failed: " + e.getMessage());
        }
    }

    // * METHODS
    // send a message to the connected peer
    public void sendMessage(String message){
        if(output != null){
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

    // check if the connection attempt is to the same machine
    public boolean isConnectionToSelf(String peerIP, int peerPort){
        // check if the peer IP and port match any of our own IPs and the listening port
        if(myPort == peerPort && myIPs.contains(peerIP)){
            System.out.println("Attempting to connect to self (same IP and port). Connection aborted.");
            return true;
        }

        // warn if connecting to own IP but different port
        if (myIPs.contains(peerIP)) {
            System.out.println("Note: Connecting to own IP address but different port.");
        }

        return false;
    }
}

//* ConnectionHandler CLASS TO MANAGE INDIVIDUAL PEER CONNECTIONS
class ConnectionHandler implements Runnable {
    private Socket newSocket;

    public ConnectionHandler(Socket newSocket){
        this.newSocket= newSocket;

    }

    @Override 
    public void run() {
        // TODO: Implement logic for sending and receiving messages
    }
}


//* UserInterface CLASS TO PROCESS USER COMMANDS AND DISPLAY INFORMATIONS
class UserInterface implements Runnable {
    private Scanner scanner;

    public UserInterface() {
        scanner = new Scanner(System.in);
    }

    @Override 
    public void run() {
        // TODO: Implement user interface logic
        System.out.println("Type /help for a list of available commands.");

        while(true){
            System.out.print(">> ");
            String input = scanner.nextLine();

            switch(input){
                case "/help":
                    displayHelp();
                    break;

                case "/myip":
                    try {
                        System.out.println(getPrivateIP());
                    } catch (UnknownHostException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                    break;
            }

            
        }

       
    }
    private void displayHelp(){
        System.out.println("Information about builtin commands: \n\n");
        System.out.println("\t/help: Displays information about the available user interface options or manual.\n");
        System.out.println("\t/myip: Display the IP address of this process.\n");
        System.out.println("\t/myport: Display the port on which this process is listening for incoming connections.\n");
        System.out.println("\t/connect <destination> <port no>: This command establishes a new TCP connection to the specified <destination> at the specified <port_no>. The <destination> is the IP address of the computer.\n");
        System.out.println("\t/list: Display a numbered list of all the connections this process is apart of.\n");
        System.out.println("\t/terminate <connection_id>: This command will terminate the connection listed under the specified number when LIST is used to display all connections.\n");
        System.out.println("\t/send <connection_id> <message>: This will send the message to the host on the connection that is designated. The message to be sent can be up-to 100 characters long, including blank spaces.\n");
        System.out.println("\t/exit: Close all connections and terminate this process.\n");
    }

    //Gets IP address of computer
    private String getPrivateIP() throws UnknownHostException {
        return InetAddress.getLocalHost().getHostAddress();
    }

   
    
}