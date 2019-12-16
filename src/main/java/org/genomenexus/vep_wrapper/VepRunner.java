package org.genomenexus.vep_wrapper;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;

public class VepRunner {
    public static final String VEP_DEFAULT_PARAMS = null;

    private static int[] processingOrder = null; // a reordering of the request to put them into chromosomal order for processing
    private static int[] responseOrder = null; // a reordering of the processing output to restore the original request order in our response

    private static final String INDEX_DELIMITER = "#";

    private static final boolean do_sorting = false;
    private static final String CONSTRUCTED_INPUT_FILENAME = "/opt/vep/.vep/input/constructed_input_file.txt";

    private static void printTimestamp() {
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        System.out.println(timestamp);
    }

    private static void computeOrders(List<String> requestList) {
        ArrayList<String> workingRequestOrder = new ArrayList();
        processingOrder = new int[requestList.size()];
        responseOrder = new int[requestList.size()];
        System.out.println("computing order of input list (list size: " + requestList.size() + ")");
        int index = 0;
        for (String request : requestList) {
            workingRequestOrder.add(request + INDEX_DELIMITER + Integer.toString(index));
            index = index + 1;
        }
        Collections.sort(workingRequestOrder);
        int sortedIndex= 0;
        for (String request : workingRequestOrder) {
            String[] parts = request.split(INDEX_DELIMITER);
            if (parts.length < 2) {
                System.out.println("something bad happened during split of working order");
                System.exit(3);
            }
            try {
                int originalIndex = Integer.parseInt(parts[1]);
                processingOrder[originalIndex] = sortedIndex;
                responseOrder[sortedIndex] = originalIndex;
            } catch (NumberFormatException e) {
                System.out.println("something bad happened during parse of offset of working order");
                System.exit(3);
            }
            sortedIndex = sortedIndex + 1;
        }
    }

    private static void writeRegionsToConstructedInput(List<String> regions) {
        try {
            PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(CONSTRUCTED_INPUT_FILENAME)));
            for (String region : regions) {
                out.println(region);
            }
            out.close();
        } catch (IOException e) {
            System.err.println("Error - could not construct input file " +  CONSTRUCTED_INPUT_FILENAME);
            System.exit(5);
        }
    }

    public static String run(List<String> regions, Boolean convertToListJSON) throws IOException, InterruptedException {
        printTimestamp();
        System.out.println("Running vep");
        // get vep pameters (use -Dvep.params to change)
        String vepParameters = System.getProperty("vep.params", String.join(" ",
            "--cache",
            "--offline",
            "--everything",
            "--hgvsg",
            "--assembly GRCh37",
            "--format region",
            "--fork 4",
            "--fasta /opt/vep/.vep/homo_sapiens/98_GRCh37/Homo_sapiens.GRCh37.75.dna.primary_assembly.fa.gz",
            "--json",
            "-i " + CONSTRUCTED_INPUT_FILENAME,
            "-o /opt/vep/.vep/output/output_from_constructed_input.txt",
            "--no_stats"
        ));

        //Build command
        List<String> commands = new ArrayList<String>();
        commands.add("vep");
        for (String param : vepParameters.split(" ")) {
            commands.add(param);
        }

        // Check reference genome environment variable and replace ref genome if necessary
        String assembly = System.getenv("VEP_ASSEMBLY");
        if (assembly != null && !"".equals(assembly)) {
            commands = replaceOptValue(commands, "--assembly", assembly);
        }

        printTimestamp();
        System.out.println("running command: " + commands);
        //Run macro on target
        ProcessBuilder pb = new ProcessBuilder(commands);
        pb.directory(new File("/opt/vep/src/ensembl-vep"));
        pb.redirectErrorStream(true);
        printTimestamp();
        System.out.println("starting..");
        Process process = pb.start();

if (do_sorting) {
        // compute forward and backword reordering
        printTimestamp();
        System.out.println("computing order..");
        computeOrders(regions);
        printTimestamp();
        System.out.println("done computing order");
}

        printTimestamp();
        System.out.println("writing constructed input file");
        writeRegionsToConstructedInput(regions);

        // send regions to stdin
        printTimestamp();
        System.out.println("processing requests");
        OutputStream stdin = process.getOutputStream();
        BufferedWriter stdinWriter = new BufferedWriter(new OutputStreamWriter(stdin));
if (do_sorting) {
        for (int index : processingOrder) {
            String region = regions.get(index);
            stdinWriter.write(region);
            stdinWriter.write("\n");
            System.out.print(".");
        }
} else {
        int case_number = 0;
        for (String region : regions) {
            stdinWriter.write(region);
            stdinWriter.write("\n");
            System.out.print(".");
            System.out.flush();
            if ((case_number % 500) == 0) {
                stdinWriter.write("now submitting record#" + case_number);
                stdinWriter.write("\n");
            }
            case_number = case_number + 1;
        }
}
        stdinWriter.flush();
        stdinWriter.close();
        printTimestamp();
        System.out.println("done processing requests");

        //Read output
        StringBuilder out = new StringBuilder();
        BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line = null, previous = null;
        if (convertToListJSON) {
            out.append('[');
            out.append('\n');
        }
        while ((line = br.readLine()) != null) {
            if (previous != null && convertToListJSON) {
                out.append(',');
                out.append('\n');
                System.out.print("o");
            }
            out.append(line);
            previous = line;
        }
        if (convertToListJSON) {
            out.append(']');
            out.append('\n');
        }

        // Check result
        int statusCode = process.waitFor();
        if (statusCode == 0) {
            System.out.println("OK");
            System.out.println(out.toString());
            return out.toString();
        }

        //TODO: Abnormal termination: Log command parameters and output and throw ExecutionException
        System.out.println("abnormal termination");
        System.out.println("exited with status: " + statusCode);
        System.out.println("returning output anyway:");
        System.out.println(out.toString());
        return out.toString();
    }

    /**
     * Function to replace a specific value in the VEP parameters list.
     */
    private static List<String> replaceOptValue(List<String> commands, String optionName, String newValue) {
        List<String> result = new ArrayList<String>();
        boolean substituteNext = false;
        for (String command : commands) {

            // Find argument to replace
            if (command.equals(optionName)) {
                result.add(command);

                // Replace value
                result.add(newValue);
                substituteNext = true;

            } else {

                // Skip value if it was replaced in the previous iteration
                if (substituteNext) {
                    substituteNext = false;
                } else {
                    result.add(command);
                }
            }
        }
        return result;
    }
}
