/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.commonwl.view.graphviz;

import org.commonwl.view.cwl.CWLElement;
import org.commonwl.view.cwl.CWLProcess;
import org.commonwl.view.cwl.CWLStep;
import org.commonwl.view.workflow.Workflow;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Writes GraphViz DOT files from Workflows
 */
public class DotWriter {

    private static final String EOL = System.getProperty("line.separator");
    private Writer writer;

    public DotWriter(Writer writer) {
        this.writer = writer;
    }

    /**
     * Write a graph representing a workflow to the Writer
     * @param workflow The workflow to be graphed
     * @throws IOException Any errors in writing which may have occurred
     */
    public void writeGraph(Workflow workflow) throws IOException {

        /**
         * DOT graph styling is based on the Apache
         * Taverna workflow management system
         */
        // Begin graph
        writeLine("digraph workflow {");

        // Overall graph style
        writeLine("  graph [");
        writeLine("    bgcolor = \"#eeeeee\"");
        writeLine("    color = \"black\"");
        writeLine("    fontsize = \"10\"");
        writeLine("    labeljust = \"left\"");
        writeLine("    clusterrank = \"local\"");
        writeLine("    ranksep = \"0.22\"");
        writeLine("    nodesep = \"0.05\"");
        writeLine("  ]");

        // Overall node style
        writeLine("  node [");
        writeLine("    fontname = \"Helvetica\"");
        writeLine("    fontsize = \"10\"");
        writeLine("    fontcolor = \"black\"");
        writeLine("    shape = \"record\"");
        writeLine("    height = \"0\"");
        writeLine("    width = \"0\"");
        writeLine("    color = \"black\"");
        writeLine("    fillcolor = \"lightgoldenrodyellow\"");
        writeLine("    style = \"filled\"");
        writeLine("  ];");

        // Overall edge style
        writeLine("  edge [");
        writeLine("    fontname=\"Helvetica\"");
        writeLine("    fontsize=\"8\"");
        writeLine("    fontcolor=\"black\"");
        writeLine("    color=\"black\"");
        writeLine("    arrowsize=\"0.7\"");
        writeLine("  ];");

        // Write inputs as a subgraph
        writeInputs(workflow);

        // Write outputs as a subgraph
        writeOutputs(workflow);

        // Write steps as nodes
        writeSteps(workflow);

        // End graph
        writeLine("}");
    }

    /**
     * Writes a set of inputs from a workflow to the Writer
     * @param workflow The workflow to get the inputs from
     * @throws IOException Any errors in writing which may have occurred
     */
    private void writeInputs(Workflow workflow) throws IOException {
        // Get inputs from workflow
        Map<String, CWLElement> inputs = workflow.getInputs();

        // Start of subgraph with styling
        writeLine("  subgraph cluster_inputs {");
        writeLine("    rank = \"same\";");
        writeLine("    style = \"dashed\";");
        writeLine("    label = \"Workflow Inputs\";");

        // Write each of the inputs as a node
        for (Map.Entry<String, CWLElement> input : workflow.getInputs().entrySet()) {
            writeInputOutput(input);
        }

        // End subgraph
        writeLine("  }");
    }

    /**
     * Writes a set of outputs from a workflow to the Writer
     * @param workflow The workflow to get the outputs from
     * @throws IOException Any errors in writing which may have occurred
     */
    private void writeOutputs(Workflow workflow) throws IOException {
        // Start of subgraph with styling
        writeLine("  subgraph cluster_outputs {");
        writeLine("    rank = \"same\";");
        writeLine("    style = \"dashed\";");
        writeLine("    label = \"Workflow Outputs\";");

        // Write each of the outputs as a node
        for (Map.Entry<String, CWLElement> output : workflow.getOutputs().entrySet()) {
            writeInputOutput(output);
        }

        // End subgraph
        writeLine("  }");
    }

    /**
     * Writes a set of steps from a workflow to the Writer
     * @param workflow The workflow to get the steps from
     * @throws IOException Any errors in writing which may have occurred
     */
    private void writeSteps(Workflow workflow) throws IOException {
        // Write each of the steps as a node or subgraph if it is a nested workflow
        for (Map.Entry<String, CWLStep> step : workflow.getSteps().entrySet()) {
            if (step.getValue().getRunType() == CWLProcess.WORKFLOW) {
                writeLine("  \"" + step.getKey() + "\" [fillcolor=\"#F3CEA1\"];");
            } else {
                writeLine("  \"" + step.getKey() + "\";");
            }
        }

        // Write the links between nodes
        // Write links between outputs and penultimate steps
        for (Map.Entry<String, CWLElement> output : workflow.getOutputs().entrySet()) {
            for (String sourceID : output.getValue().getSourceIDs()) {
                writeLine("  \"" + sourceID + "\" -> \"" + output.getKey() + "\";");
            }
        }

        // Write links between the remaining steps
        int defaultCount = 0;
        for (Map.Entry<String, CWLStep> step : workflow.getSteps().entrySet()) {
            if (step.getValue().getInputs() != null) {
                for (Map.Entry<String, CWLElement> input : step.getValue().getInputs().entrySet()) {
                    List<String> sourceIDs = input.getValue().getSourceIDs();

                    // Draw the default value on the graph if there are no step inputs (it is a constant)
                    String defaultVal = input.getValue().getDefaultVal();
                    if (sourceIDs.isEmpty() && defaultVal != null) {
                        // New node for a default value to be used as the source
                        defaultCount++;
                        writeLine("  \"default" + defaultCount + "\" [label=\"" + defaultVal + "\", fillcolor=\"#D5AEFC\"]");
                        writeLine("  \"default" + defaultCount + "\" -> \"" + step.getKey() + "\";");
                    }

                    // Otherwise write regular links from source step to destination step
                    for (String sourceID : sourceIDs) {
                        writeLine("  \"" + sourceID + "\" -> \"" + step.getKey() + "\";");
                    }
                }
            }
        }
    }

    /**
     * Writes a single input or output to the Writer
     * @param inputOutput The input or output
     * @throws IOException Any errors in writing which may have occurred
     */
    private void writeInputOutput(Map.Entry<String, CWLElement> inputOutput) throws IOException {
        // List of options for this node
        List<String> nodeOptions = new ArrayList<>();
        nodeOptions.add("fillcolor=\"#94DDF4\"");

        // Use label if it is defined
        String label = inputOutput.getValue().getLabel();
        if (label != null && !label.isEmpty()) {
            nodeOptions.add("label=\"" + label + "\";");
        }

        // Write the line for the node
        writeLine("    \"" + inputOutput.getKey() + "\" [" + String.join(",", nodeOptions) + "];");
    }

    /**
     * Write a single line using the Writer
     * @param line The line to be written
     * @throws IOException Any errors in writing which may have occurred
     */
    private void writeLine(String line) throws IOException {
        writer.write(line);
        writer.write(EOL);
    }

}