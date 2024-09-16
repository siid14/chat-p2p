import java.io.IOException;
import java.net.*;


public class chat {

    //* HANDLE INITIALIZATION - PARSE COMMAND LINE - START THREADS 
    public static void main(String[] args) {

        // check if the port number has been included in command line
        if(args.length != 1){
            System.out.println("Missing port number -- make run <port>");
            return;
        }

        int port = Integer.parseInt(args[0]); // convert arg to int (in case it's string)

        PeerServer peerServer = new PeerServer(port);
        UserInterface ui = new UserInterface();

        new Thread(peerServer).start();
        new Thread(ui).start();
        
    }
}

//* MANAGE INCOMING CONNECTIONS - LISTEN ON THE SPECIFIED PORT
class PeerServer implements Runnable {
    private int port;

    public PeerServer (int port){
        this.port = port;
    }

    @Override 
    public void run() {
        try (ServerSocket serverSocket = new ServerSocket(port)){
            System.out.println("Listening on port " + port);
            } catch(IOException e) {
                System.out.println("Server exception " + e.getMessage());

            }
        }

    }


//* MANAGE OUTGOING CONNECTIONS - MANAGE THE 'CONNECT' COMMAND FUNCTIONALITY
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

    }
}

//* MANAGE INDIVIDUAL CONNECTION PEER CONNECTIONS - HANDLE SENDING AND RECEIVING MSG
class ConnectionHandler implements Runnable {

    @Override 
    public void run() {

    }
}

//* PROCESSES USER COMMANDS - DISPLAY INFORMATIONS TO USER
class UserInterface implements Runnable {

    @Override 
    public void run() {

    }
}