# Java Makefile Usage

# Quickstart Guide

**Commands**

Compile: make all
Run: make run
Run with arguments: make run arg1
Clean: make clean

Example: 
cd in chat-p2p
make run 4545 -- to run a peer on port 4545
Then you can apply the following commands :

- help: Display information about available user interface options or command manual.
- myip: Display the IP address of this process.
- myport: Display the port on which this process is listening for incoming connections.
- connect <destination> <port no>: Establish a new TCP connection to the specified destination at the specified port.
- list: Display a numbered list of all connections this process is part of.
- terminate <connection id.>: Terminate the connection listed under the specified number.
- send <connection id.> <message>: Send a message to the host on the connection designated by the number.
- exit: Close all connections and terminate this process.

**Requirements**

Java Development Kit (JDK)
javac and java in system PATH

By Sidney Thomas, Ashley Manese, Darrin Du.
