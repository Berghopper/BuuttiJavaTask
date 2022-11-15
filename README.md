# Buutti Java Task

## Scope

Java task
By using the Java programming language, implement a simple file copying program.
The program reads characters from a given input file into an in-memory buffer one at a time and writes characters from the same buffer to a given output file. The reading and writing should be handled in separate threads.

The buffer works like a FIFO queue with a maximum length. When the buffer is full, the thread adding characters to the buffer must wait that the other thread has consumed characters from it.
Vice versa, when the buffer is empty, the consuming thread must wait that the other thread has added characters to it.
Your solution should be thread safe.

Other evaluation criteria:
- Design
- Structure
- Use of language features
- Error checking
- Code style and readability
- Comments (where actually needed)
- Testability
- Documentation
- Usability

## Implementation

I chose to implement this as simply as possible; As a basic maven project that can be built, tested and run as a simple standalone jar/command line tool.

## Build / run;

Build project jar;

```mvn clean test install```

This should result in a JAR artifact named `BuuttiBufferCopy.jar` in the maven target folder.

Jar run examples;
```
# help options;
java -jar BuuttiBufferCopy.jar -h
# basic IO with example file;
java -jar BuuttiBufferCopy.jar -I file_in.txt -O file_out.txt
# same, but with 1 second buffer IO timeout and bigger buffersize (4096);
java -jar BuuttiBufferCopy.jar -I file_in.txt -O file_out.txt -B 4096 -T 1000
```