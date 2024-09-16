# java compiler
JC = javac

# java runtime
JVM = java

# source file
MAIN = chat.java

# class file
MAIN_CLASS = chat

# compile Java source file
all: $(MAIN)
	$(JC) $(MAIN)

# run the program with arguments
run: all
	$(JVM) $(MAIN_CLASS) $(filter-out $@,$(MAKECMDGOALS))

# clean up compiled files
clean:
	$(RM) *.class

# default target
.PHONY: all run clean

# catch-all target to allow arguments
%:
	@: