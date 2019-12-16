package org.genomenexus.vep_wrapper;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;

public class VepRunner {
    public static final String VEP_DEFAULT_PARAMS = null;

    private static int[] processingOrder = null; // a reordering of the request to put them into chromosomal order for processing
    private static int[] responseOrder = null; // a reordering of the processing output to restore the original request order in our response

    private static final String INDEX_DELIMITER = "#";

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

    public static String run(List<String> regions, Boolean convertToListJSON) throws IOException, InterruptedException {
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
            "-o STDOUT",
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

        System.out.println("running command: " + commands);
        //Run macro on target
        ProcessBuilder pb = new ProcessBuilder(commands);
        pb.directory(new File("/opt/vep/src/ensembl-vep"));
        pb.redirectErrorStream(true);
        System.out.println("staring..");
        Process process = pb.start();

        // compute forward and backword reordering
        computeOrders(regions);

        // send regions to stdin
        System.out.println("processing requests");
        OutputStream stdin = process.getOutputStream();
        BufferedWriter stdinWriter = new BufferedWriter(new OutputStreamWriter(stdin));
        for (int index : processingOrder) {
            String region = regions.get(index);
            stdinWriter.write(region);
            stdinWriter.write("\n");
            System.out.print(".");
        }
        stdinWriter.flush();
        stdinWriter.close();

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
