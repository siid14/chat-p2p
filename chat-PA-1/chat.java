public class chat {
    public static void main(String[] args) {
        System.out.println("Hello, World! This is the Chat Application.");
        
        if (args.length > 0) {
            System.out.println("Received port number: " + args[0]);
        } else {
            System.out.println("No port number provided.");
        }
    }
}