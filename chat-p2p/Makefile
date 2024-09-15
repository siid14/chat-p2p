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

# Run the program with arguments
run: all
	$(JVM) $(MAIN_CLASS) $(filter-out $@,$(MAKECMDGOALS))

# Clean up compiled files
clean:
	$(RM) *.class

# Default target
.PHONY: all run clean

# Catch-all target to allow arguments
%:
	@: