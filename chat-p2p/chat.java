import java.io.IOException;
import java.net.*;


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

// * PeerServe CLASS TO MANAGE INCOMING CONNECTIONS 
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

    public PeerClient(String peerIP, int peerPort){
        peerIP = this.peerIP;
        peerPort = this.peerPort;
    }

    @Override 
    public void run() {
        try{ 
            // attempt to connect to the specified peer
            socket = new Socket(peerIP, peerPort);
            System.out.println("Connected to peer at " + peerIP + ":" + peerPort);
            // TODO: handle the established connection
        } catch(IOException e){
            System.out.println("Client exception " + e.getMessage());
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