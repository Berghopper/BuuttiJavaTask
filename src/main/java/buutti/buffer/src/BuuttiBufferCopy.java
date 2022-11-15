package buutti.buffer.src;

import buutti.buffer.exceptions.SyncBufferSupplierExhaustedException;
import buutti.buffer.exceptions.SyncBufferTimeoutException;
import buutti.buffer.interfaces.SyncBuffer;
import buutti.buffer.util.AbstractSyncBuffer;
import buutti.buffer.util.SyncBufferImpl;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.CountDownLatch;


import org.apache.commons.cli.*;

/**
 * The main file copying class.
 * Contains methods for read/writing and managing CLI options.
 */
public class BuuttiBufferCopy {
    /**
     * Main.
     * @param args String array.
     * @throws InterruptedException on thread interruption.
     */
    public static void main(String[] args) throws InterruptedException {
        CommandLineParser parser = new DefaultParser();
        Options options = getCLIOptions();
        try {
            CommandLine commandLine = parser.parse(options, args);

            if (commandLine.hasOption('h')) {
                printHelp(options);
            }

            int bufferSize = parseBufferSize(commandLine);
            long bufferTimeout = parseBufferTimeout(commandLine);

            fileArgsCheck(commandLine);

            String in = commandLine.getOptionValue('I');
            String out = commandLine.getOptionValue('O');

            // Start copy.
            try {
                doCopy(in, out, bufferSize, bufferTimeout);
            } catch (OutOfMemoryError e) {
                System.out.println("Memory error occurred, try a smaller buffer size?");
                e.printStackTrace();
                System.exit(1);
            }
            System.out.println("Done!");
        } catch (ParseException e) {
            printHelp(options);
        }

    }

    /**
     * Parses and validated the buffer timeout option.
     * @param commandLine CommandLine object containing parsed arguments.
     * @return long bufferTimeout
     */
    private static long parseBufferTimeout(final CommandLine commandLine) {
        String s = commandLine.getOptionValue('T', "-1");
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            printHelp(getCLIOptions());
        }
        return -1;
    }

    /**
     * Parses and validated the buffer size option.
     * @param commandLine CommandLine object containing parsed arguments.
     * @return int bufferSize
     */
    private static int parseBufferSize(final CommandLine commandLine) {
        String s = commandLine.getOptionValue('B', "16");
        try {
            int retval = Integer.parseInt(s);
            if (retval < 1) {
                printHelp(getCLIOptions());
            }
            return retval;
        } catch (NumberFormatException e) {
            printHelp(getCLIOptions());
        }
        return 16;
    }

    /**
     * Checks whether in/out files are read/writable and valid.
     * @param commandLine CommandLine object containing parsed arguments.
     */
    private static void fileArgsCheck(final CommandLine commandLine) {
        try {
            Path inputPath = Paths.get(commandLine.getOptionValue('I'));
            if (!(Files.exists(inputPath) && Files.isReadable(inputPath))) {
                System.out.println("The input file specified is not a valid file or cannot be read.");
                System.exit(1);
            }
        } catch (InvalidPathException e) {
            System.out.println("The input file specified is not a valid file/path.");
            System.exit(1);
        }
        try {
            Path outputPath = Paths.get(commandLine.getOptionValue('O'));
            File file = outputPath.toFile();
            file.createNewFile();
            if (!Files.isWritable(outputPath)) {
                System.out.println("The output file specified is not a writable file.");
                System.exit(1);
            }
        } catch (InvalidPathException e) {
            System.out.println("The output file specified is not a valid file/path.");
            System.exit(1);
        } catch (IOException | SecurityException e) {
            System.out.println("The output file specified is not writable or another error occurred.");
            System.exit(1);
        }
    }

    /**
     * Prints the CLI help info.
     * @param options command line options.
     */
    private static void printHelp(final Options options) {
        HelpFormatter hf = new HelpFormatter();
        hf.setWidth(110);
        hf.printHelp("BuuttiBufferCopy.jar", options, true);
        System.exit(1);
    }

    /**
     * Generates CLI options object.
     * @return Options object with CLI options.
     */
    private static Options getCLIOptions() {
        Option option_I = Option.builder("I")
                .required(true)
                .hasArg()
                .desc("The input file.")
                .longOpt("input")
                .build();
        Option option_O = Option.builder("O")
                .required(true)
                .hasArg()
                .desc("The output file.")
                .longOpt("output")
                .build();
        Option option_B = Option.builder("B")
                .required(false)
                .hasArg()
                .type(Number.class)
                .desc("The buffer size. Default is 16. Min: 1. Max: " + Integer.MAX_VALUE)
                .longOpt("buffer-size")
                .build();
        Option option_T =  Option.builder("T")
                .required(false)
                .hasArg()
                .type(Number.class)
                .desc("The buffer IO timeout in millis. Any value below 0 = (wait forever/until supplier is done).")
                .longOpt("buffer-timeout")
                .build();
        Option option_h =  Option.builder("h")
                .required(false)
                .desc("Request this help printout.")
                .longOpt("help")
                .build();
        Options options = new Options();
        options.addOption(option_I);
        options.addOption(option_O);
        options.addOption(option_B);
        options.addOption(option_T);
        options.addOption(option_h);
        return options;
    }

    /**
     * Starts the read/write threads together with parsing through the buffer. Awaits threads to finish before exit.
     * @param in input file
     * @param out output file
     * @param bufferSize buffer size
     * @param bufferTimeout buffer timeout
     * @throws InterruptedException on thread interruption.
     */
    private static void doCopy(final String in, final String out,
                               final int bufferSize, final long bufferTimeout) throws InterruptedException {
        CountDownLatch cdl = new CountDownLatch(2);
        try (AbstractSyncBuffer<Character> sb = new SyncBufferImpl<>(bufferSize, bufferTimeout)) {
            Thread t1 = new Thread(() ->
            {
                try {
                    read(sb, new File(in));
                } catch (IOException | InterruptedException e) {
                    throw new RuntimeException(e);
                } finally {
                    cdl.countDown();
                }
            });
            Thread t2 = new Thread(() ->
            {
                try {
                    write(sb, new File(out));
                } catch (IOException | InterruptedException e) {
                    throw new RuntimeException(e);
                } finally {
                    cdl.countDown();
                }
            });
            t1.start();
            t2.start();
            cdl.await();
        }
    }

    /**
     * Read method. Reads from the input file and writes to the buffer.
     * @param sb SyncBuffer object for writing buffer.
     * @param f File object to read from.
     * @throws IOException on file read/buffer error.
     * @throws InterruptedException on thread interruption.
     */
    private static void read(final AbstractSyncBuffer<Character> sb, final File f) throws IOException, InterruptedException {
        try (sb; FileInputStream fis = new FileInputStream(f)) {
            int r = 0;
            while ((r = fis.read()) != -1) {
                try {
                    sb.supply((char) r);
                } catch (SyncBufferTimeoutException e) {
                    System.out.println("Buffer timeout while reading. Buffer full!");
                    System.exit(1);
                }
            }
        }
    }

    /**
     * Write method. Reads from the buffer and writes to the output file.
     * @param sb SyncBuffer object for reading buffer.
     * @param f File object to write to.
     * @throws IOException on file write/buffer error.
     * @throws InterruptedException on thread interruption.
     */
    private static void write(final SyncBuffer<Character> sb, final File f) throws IOException, InterruptedException {
        try (FileOutputStream fos = new FileOutputStream(f)) {
            while (true) {
                try {
                    fos.write(sb.consume());
                } catch (SyncBufferTimeoutException e) {
                    if (sb.isEmpty() && sb.isSupplierIsExhausted()) {
                        break;
                    }
                    System.out.println("Buffer timeout while writing/supplier not exhausted? Buffer empty!");
                    System.exit(1);
                } catch (SyncBufferSupplierExhaustedException e) {
                    // No more items.
                    break;
                }
            }
        }
    }
}