import java.io.*;
import java.net.*;
import java.util.*;

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

   
    
}