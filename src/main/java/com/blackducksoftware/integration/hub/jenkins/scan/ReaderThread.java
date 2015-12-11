package com.blackducksoftware.integration.hub.jenkins.scan;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import com.blackducksoftware.integration.suite.sdk.logging.IntLogger;

public class ReaderThread extends Thread {

    private final File cliOutput;

    private final IntLogger logger;

    private StringBuilder outputBuilder = new StringBuilder();

    public ReaderThread(IntLogger logger, File cliOutput) {
        this.logger = logger;
        this.cliOutput = cliOutput;
    }

    public String getOutputString() {
        return outputBuilder.toString();
    }

    public Boolean hasOutput() {
        return outputBuilder.length() > 0;
    }

    @Override
    public void run() {
        BufferedReader buffReader = null;
        try {
            buffReader = new BufferedReader(new FileReader(cliOutput));
            String line = null;
            while (true) {
                line = buffReader.readLine();
                if (line == null) {
                    Thread.sleep(500);
                } else {
                    // Only interested in the error logs and the warning logs
                    if (line.contains("Exception")) {
                        outputBuilder.append(line + System.getProperty("line.separator"));
                        logger.error(line);
                    } else if (line.contains("ERROR:")) {
                        outputBuilder.append(line + System.getProperty("line.separator"));
                        logger.error(line);
                    } else if (line.contains("WARN:")) {
                        outputBuilder.append(line + System.getProperty("line.separator"));
                        logger.warn(line);
                    } else if (line.contains("INFO:")) {
                        outputBuilder.append(line + System.getProperty("line.separator"));
                        logger.info(line);
                    } else if (line.contains("Finished in")) {
                        outputBuilder.append(line + System.getProperty("line.separator"));
                        logger.info(line);
                    }
                }
            }
        } catch (IOException ioe) {
            logger.error("Could not read the CLI output file.", ioe);
        } catch (InterruptedException e) {
            // Thread was interrupted
        } finally {
            try {
                buffReader.close();
            } catch (IOException ioe1) {
                // Leave It
            }
        }
    }
}
