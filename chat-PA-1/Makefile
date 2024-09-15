# Java compiler
JC = javac

# Java runtime
JVM = java

# Source file
MAIN = chat.java

# Class file
MAIN_CLASS = chat

# Compile Java source file
all: $(MAIN)
	$(JC) $(MAIN)

# Run the program
run: all
	$(JVM) $(MAIN_CLASS)

# Clean up compiled files
clean:
	$(RM) *.class

# Default target
.PHONY: all run clean