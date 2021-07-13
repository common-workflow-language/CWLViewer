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

package org.commonwl.view.cwl;

import static org.apache.commons.io.FileUtils.readFileToString;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.jena.iri.IRI;
import org.apache.jena.iri.IRIFactory;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.RiotException;
import org.commonwl.view.docker.DockerService;
import org.commonwl.view.git.GitDetails;
import org.commonwl.view.graphviz.ModelDotWriter;
import org.commonwl.view.graphviz.RDFDotWriter;
import org.commonwl.view.workflow.Workflow;
import org.commonwl.view.workflow.WorkflowNotFoundException;
import org.commonwl.view.workflow.WorkflowOverview;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

/**
 * Provides CWL parsing for workflows to gather an overview
 * for display and visualisation
 */
@Service
public class CWLService {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final IRIFactory iriFactory = IRIFactory.iriImplementation();

    // Autowired properties/services
    private final RDFService rdfService;
    private final CWLTool cwlTool;
    private final int singleFileSizeLimit;

    // CWL specific strings
    private final String DOC_GRAPH = "$graph";
    private final String CLASS = "class";
    private final String WORKFLOW = "Workflow";
    private final String COMMANDLINETOOL = "CommandLineTool";
    private final String EXPRESSIONTOOL = "ExpressionTool";
    private final String STEPS = "steps";
    private final String INPUTS = "inputs";
    private final String IN = "in";
    private final String OUTPUTS = "outputs";
    private final String OUT = "out";
    private final String ID = "id";
    private final String TYPE = "type";
    private final String LABEL = "label";
    private final String DEFAULT = "default";
    private final String OUTPUT_SOURCE = "outputSource";
    private final String SOURCE = "source";
    private final String DOC = "doc";
    private final String DESCRIPTION = "description";
    private final String ARRAY = "array";
    private final String ARRAY_ITEMS = "items";
    private final String LOCATION = "location";
    private final String RUN = "run";

    /**
     * Constructor for the Common Workflow Language service
     * @param rdfService A service for handling RDF queries
     * @param cwlTool Handles cwltool integration
     * @param singleFileSizeLimit The file size limit for single files
     */
    @Autowired
    public CWLService(RDFService rdfService,
                      CWLTool cwlTool,
                      @Value("${singleFileSizeLimit}") int singleFileSizeLimit) {
        this.rdfService = rdfService;
        this.cwlTool = cwlTool;
        this.singleFileSizeLimit = singleFileSizeLimit;
    }

    /**
     * Gets whether a file is packed using schema salad
     * @param workflowFile The file to be parsed
     * @return Whether the file is packed
     */
    public boolean isPacked(File workflowFile) throws IOException {
        if (workflowFile.length() > singleFileSizeLimit) {
            return false;
        }
        String fileContent = readFileToString(workflowFile);
        return fileContent.contains("$graph");
    }

    /**
     * Gets a list of workflows from a packed CWL file
     * @param packedFile The packed CWL file
     * @return The list of workflow overviews
     */
    public List<WorkflowOverview> getWorkflowOverviewsFromPacked(File packedFile) throws IOException {
        if (packedFile.length() <= singleFileSizeLimit) {
            List<WorkflowOverview> overviews = new ArrayList<>();

            JsonNode packedJson = yamlPathToJson(packedFile.toPath());

            if (packedJson.has(DOC_GRAPH)) {
                for (JsonNode jsonNode : packedJson.get(DOC_GRAPH)) {
                    if (extractProcess(jsonNode) == CWLProcess.WORKFLOW) {
                        WorkflowOverview overview = new WorkflowOverview(jsonNode.get(ID).asText(),
                                extractLabel(jsonNode), extractDoc(jsonNode));
                        overviews.add(overview);
                    }
                }
            } else {
                throw new IOException("The file given was not recognised as a packed CWL file");
            }

            return overviews;

        } else {
            throw new IOException("File '" + packedFile.getName() +  "' is over singleFileSizeLimit - " +
                    FileUtils.byteCountToDisplaySize(packedFile.length()) + "/" +
                    FileUtils.byteCountToDisplaySize(singleFileSizeLimit));
        }
    }

    /**
     * Gets the Workflow object from internal parsing. 
     * Note, the length of the stream is not checked.
     *  
     * @param workflowStream The workflow stream to be parsed
     * @param packedWorkflowId The ID of the workflow object if the file is packed. <code>null</code> means the workflow is not expected to be packecd, while "" means the first workflow found is used, packed or non-packed.
     * @param defaultLabel Label to give workflow if not set
     * @return The constructed workflow object
     */
    public Workflow parseWorkflowNative(InputStream workflowStream, String packedWorkflowId, String defaultLabel) throws IOException {
        // Parse file as yaml
        JsonNode cwlFile = yamlStreamToJson(workflowStream);

        // Check packed workflow occurs
        boolean found = false;
        if (packedWorkflowId != null) {
            if (cwlFile.has(DOC_GRAPH)) {
                for (JsonNode jsonNode : cwlFile.get(DOC_GRAPH)) {
                    if (extractProcess(jsonNode) == CWLProcess.WORKFLOW) {
                        String currentId = jsonNode.get(ID).asText();
                        if (currentId.startsWith("#")) {
                            currentId = currentId.substring(1);
                        }
                        if (packedWorkflowId.isEmpty() || currentId.equals(packedWorkflowId)) {
                            cwlFile = jsonNode;
                            found = true;
                            break;
                        }
                    }
                }
            }
            if (!found && ! packedWorkflowId.isEmpty()) throw new WorkflowNotFoundException();
        }
        if (! found && extractProcess(cwlFile) != CWLProcess.WORKFLOW) {
        	// Check the current json node is a workflow
        	found = true;
        }
        if (! found) {
            throw new WorkflowNotFoundException();
        }

        // Use filename for label if there is no defined one
        String label = extractLabel(cwlFile);
        if (label == null) {
            label = defaultLabel;
        }

        // Construct the rest of the workflow model
        Workflow workflowModel = new Workflow(label, extractDoc(cwlFile), getInputs(cwlFile),
                getOutputs(cwlFile), getSteps(cwlFile));

        workflowModel.setCwltoolVersion(cwlTool.getVersion());

        // Generate DOT graph
        StringWriter graphWriter = new StringWriter();
        ModelDotWriter dotWriter = new ModelDotWriter(graphWriter);
        try {
            dotWriter.writeGraph(workflowModel);
            workflowModel.setVisualisationDot(graphWriter.toString());
        } catch (IOException ex) {
            logger.error("Failed to create DOT graph for workflow: " + ex.getMessage());
        }

        return workflowModel;


    }

    
    /**
     * Gets the Workflow object from internal parsing.
     * The size of the workflow file must be below the configured 
     * singleFileSizeLimit in the constructor/spring config.
     * 
     * @param workflowFile The workflow file to be parsed
     * @param packedWorkflowId The ID of the workflow object if the file is packed
     * @return The constructed workflow object
     */
    public Workflow parseWorkflowNative(Path workflowFile, String packedWorkflowId) throws IOException {

        // Check file size limit before parsing
        long fileSizeBytes = Files.size(workflowFile);
        if (fileSizeBytes <= singleFileSizeLimit) {
        	try (InputStream in = Files.newInputStream(workflowFile)) {
        		return parseWorkflowNative(in, packedWorkflowId, 
        				workflowFile.getName(workflowFile.getNameCount()-1).toString());
        	}
        } else {
            throw new IOException("File '" + workflowFile.getFileName() +  "' is over singleFileSizeLimit - " +
                    FileUtils.byteCountToDisplaySize(fileSizeBytes) + "/" +
                    FileUtils.byteCountToDisplaySize(singleFileSizeLimit));
        }

    }

    /**
     * Create a workflow model using cwltool rdf output
     * @param basicModel The basic workflow object created thus far
     * @param workflowFile The workflow file to run cwltool on
     * @return The constructed workflow object
     */
    public Workflow parseWorkflowWithCwltool(Workflow basicModel,
                                             Path workflowFile,
                                             Path workTree) throws CWLValidationException {
        GitDetails gitDetails = basicModel.getRetrievedFrom();
        String latestCommit = basicModel.getLastCommit();
        String packedWorkflowID = gitDetails.getPackedId();

        // Get paths to workflow
        String url = basicModel.getIdentifier();
        String workflowFileURI = workflowFile.toAbsolutePath().toUri().toString();
        URI workTreeUri = workTree.toAbsolutePath().toUri();
		String localPath = workflowFileURI;
        String gitPath = gitDetails.getPath();
        if (packedWorkflowID != null) {
            if (packedWorkflowID.charAt(0) != '#') {
                localPath += "#";
                gitPath += "#";
            }
            localPath += packedWorkflowID;
            gitPath += packedWorkflowID;
        }

        // Get RDF representation from cwltool
        if (!rdfService.graphExists(url)) {
            String rdf = cwlTool.getRDF(localPath);
            // Replace /tmp/123123 with permalink base 
            // NOTE: We do not just replace workflowFileURI, all referenced files will also get rewritten
			rdf = rdf.replace(workTreeUri.toString(),
                    "https://w3id.org/cwl/view/git/" + latestCommit + "/");
            // Workaround for common-workflow-language/cwltool#427
            rdf = rdf.replace("<rdfs:>", "<http://www.w3.org/2000/01/rdf-schema#>");

            // Create a workflow model from RDF representation
            Model model = ModelFactory.createDefaultModel();
            model.read(new ByteArrayInputStream(rdf.getBytes()), null, "TURTLE");

            // Store the model
            rdfService.storeModel(url, model);
        }

        // Base workflow details
        String label = FilenameUtils.getName(url);
        String doc = null;
        ResultSet labelAndDoc = rdfService.getLabelAndDoc(url);
        if (labelAndDoc.hasNext()) {
            QuerySolution labelAndDocSoln = labelAndDoc.nextSolution();
            if (labelAndDocSoln.contains("label")) {
                label = labelAndDocSoln.get("label").toString();
            }
            if (labelAndDocSoln.contains("doc")) {
                doc = labelAndDocSoln.get("doc").toString();
            }
        }

        // Inputs
        Map<String, CWLElement> wfInputs = new HashMap<>();
        ResultSet inputs = rdfService.getInputs(url);
        while (inputs.hasNext()) {
            QuerySolution input = inputs.nextSolution();
            String inputName = rdfService.stepNameFromURI(gitPath, input.get("name").toString());

            CWLElement wfInput = new CWLElement();
            if (input.contains("type")) {
                String type;
                if (input.get("type").toString().equals("https://w3id.org/cwl/salad#array")) {
                    type = typeURIToString(input.get("items").toString()) + "[]";
                } else {
                    type = typeURIToString(input.get("type").toString());
                }
                if (input.contains("null")) {
                    type += " (Optional)";
                }
                wfInput.setType(type);
            }
            if (input.contains("format")) {
                String format = input.get("format").toString();
                setFormat(wfInput, format);
            }
            if (input.contains("label")) {
                wfInput.setLabel(input.get("label").toString());
            }
            if (input.contains("doc")) {
                wfInput.setDoc(input.get("doc").toString());
            }
            wfInputs.put(rdfService.labelFromName(inputName), wfInput);
        }

        // Outputs
        Map<String, CWLElement> wfOutputs = new HashMap<>();
        ResultSet outputs = rdfService.getOutputs(url);
        while (outputs.hasNext()) {
            QuerySolution output = outputs.nextSolution();
            CWLElement wfOutput = new CWLElement();

            String outputName = rdfService.stepNameFromURI(gitPath, output.get("name").toString());
            if (output.contains("type")) {
                String type;
                if (output.get("type").toString().equals("https://w3id.org/cwl/salad#array")) {
                    type = typeURIToString(output.get("items").toString()) + "[]";
                } else {
                    type = typeURIToString(output.get("type").toString());
                }
                if (output.contains("null")) {
                    type += " (Optional)";
                }
                wfOutput.setType(type);
            }

            if (output.contains("src")) {
                wfOutput.addSourceID(rdfService.stepNameFromURI(gitPath,
                        output.get("src").toString()));
            }
            if (output.contains("format")) {
                String format = output.get("format").toString();
                setFormat(wfOutput, format);
            }
            if (output.contains("label")) {
                wfOutput.setLabel(output.get("label").toString());
            }
            if (output.contains("doc")) {
                wfOutput.setDoc(output.get("doc").toString());
            }
            wfOutputs.put(rdfService.labelFromName(outputName), wfOutput);
        }


        // Steps
        Map<String, CWLStep> wfSteps = new HashMap<>();
        ResultSet steps = rdfService.getSteps(url);
        while (steps.hasNext()) {
            QuerySolution step = steps.nextSolution();
            String uri = rdfService.stepNameFromURI(gitPath, step.get("step").toString());
            if (wfSteps.containsKey(uri)) {
                // Already got step details, add extra source ID
                if (step.contains("src")) {
                    CWLElement src = new CWLElement();
                    src.addSourceID(rdfService.stepNameFromURI(gitPath, step.get("src").toString()));
                    wfSteps.get(uri).getSources().put(
                            step.get("stepinput").toString(), src);
                } else if (step.contains("default")) {
                    CWLElement src = new CWLElement();
                    src.setDefaultVal(rdfService.formatDefault(step.get("default").toString()));
                    wfSteps.get(uri).getSources().put(
                            step.get("stepinput").toString(), src);
                }
            } else {
                // Add new step
                CWLStep wfStep = new CWLStep();

                IRI workflowPath = iriFactory.construct(url).resolve("./");
                IRI runPath = iriFactory.construct(step.get("run").asResource().getURI());
                wfStep.setRun(workflowPath.relativize(runPath).toString());
                wfStep.setRunType(rdfService.strToRuntype(step.get("runtype").toString()));

                if (step.contains("src")) {
                    CWLElement src = new CWLElement();
                    src.addSourceID(rdfService.stepNameFromURI(gitPath, step.get("src").toString()));
                    Map<String, CWLElement> srcList = new HashMap<>();
                    srcList.put(rdfService.stepNameFromURI(gitPath,
                            step.get("stepinput").toString()), src);
                    wfStep.setSources(srcList);
                } else if (step.contains("default")) {
                    CWLElement src = new CWLElement();
                    src.setDefaultVal(rdfService.formatDefault(step.get("default").toString()));
                    Map<String, CWLElement> srcList = new HashMap<>();
                    srcList.put(rdfService.stepNameFromURI(gitPath,
                            step.get("stepinput").toString()), src);
                    wfStep.setSources(srcList);
                }
                if (step.contains("label")) {
                    wfStep.setLabel(step.get("label").toString());
                }
                if (step.contains("doc")) {
                    wfStep.setDoc(step.get("doc").toString());
                }
                wfSteps.put(rdfService.labelFromName(uri), wfStep);
            }
        }
        // Try to determine license
        ResultSet licenseResult = rdfService.getLicense(url);
        String licenseLink = null;
        if (licenseResult.hasNext()) {
        	licenseLink = licenseResult.next().get("license").toString();
        } else {
        	// Check for "LICENSE"-like files in root of git repo
        	for (String licenseCandidate : new String[]{"LICENSE", "LICENSE.txt", "LICENSE.md"}) {
        		// FIXME: This might wrongly match lower-case "license.txt" in case-insensitive file systems
        		// but the URL would not work
        		if (Files.isRegularFile(workTree.resolve(licenseCandidate))) {
        			// Link to it by raw URL
        			licenseLink = basicModel.getRetrievedFrom().getRawUrl(null, licenseCandidate);
        		}
			}
        }

        // Docker link
        ResultSet dockerResult = rdfService.getDockerLink(url);
        String dockerLink = null;
        if (dockerResult.hasNext()) {
            QuerySolution docker = dockerResult.nextSolution();
            if (docker.contains("pull")) {
                dockerLink = DockerService.getDockerHubURL(docker.get("pull").toString());
            } else {
                dockerLink = "true";
            }
        }

        // Create workflow model
        Workflow workflowModel = new Workflow(label, doc,
                wfInputs, wfOutputs, wfSteps, 
                dockerLink, licenseLink);

        // Generate DOT graph
        StringWriter graphWriter = new StringWriter();
        RDFDotWriter RDFDotWriter = new RDFDotWriter(graphWriter, rdfService, gitPath);
        try {
            RDFDotWriter.writeGraph(url);
            workflowModel.setVisualisationDot(graphWriter.toString());
        } catch (IOException ex) {
            logger.error("Failed to create DOT graph for workflow: " + ex.getMessage());
        }


        return workflowModel;

    }

    /**
     * Get an overview of a workflow
     * @param file A file, potentially a workflow
     * @return A constructed WorkflowOverview of the workflow
     * @throws IOException Any API errors which may have occurred
     */
    public WorkflowOverview getWorkflowOverview(File file) throws IOException {

        // Get the content of this file from Github
        long fileSizeBytes = file.length();

        // Check file size limit before parsing
        if (fileSizeBytes <= singleFileSizeLimit) {

            // Parse file as yaml
            JsonNode cwlFile = yamlPathToJson(file.toPath());

            // If the CWL file is packed there can be multiple workflows in a file
            int packedCount = 0;
            if (cwlFile.has(DOC_GRAPH)) {
                // Packed CWL, find the first subelement which is a workflow and take it
                for (JsonNode jsonNode : cwlFile.get(DOC_GRAPH)) {
                    if (extractProcess(jsonNode) == CWLProcess.WORKFLOW) {
                        cwlFile = jsonNode;
                        packedCount++;
                    }
                }
                if (packedCount > 1) {
                    return new WorkflowOverview("/" + file.getName(), "Packed file",
                            "contains " + packedCount + " workflows");
                }
            }

            // Can only make an overview if this is a workflow
            if (extractProcess(cwlFile) == CWLProcess.WORKFLOW) {
                // Use filename for label if there is no defined one
                String label = extractLabel(cwlFile);
                if (label == null) {
                    label = file.getName();
                }

                // Return the constructed overview
                return new WorkflowOverview("/" + file.getName(), label, extractDoc(cwlFile));
            } else {
                // Return null if not a workflow file
                return null;
            }
        } else {
            throw new IOException("File '" + file.getName() +  "' is over singleFileSizeLimit - " +
                    FileUtils.byteCountToDisplaySize(fileSizeBytes) + "/" +
                    FileUtils.byteCountToDisplaySize(singleFileSizeLimit));
        }

    }

    /**
     * Set the format for an input or output, handling ontologies
     * @param inputOutput The input or output CWL Element
     * @param format The format URI
     */
    private void setFormat(CWLElement inputOutput, String format) {
        inputOutput.setFormat(format);
        try {
            if (!rdfService.ontPropertyExists(format)) {
                Model ontModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM);
                ontModel.read(format, null, "RDF/XML");
                rdfService.addToOntologies(ontModel);
            }
            String formatLabel = rdfService.getOntLabel(format);
            inputOutput.setType(inputOutput.getType() + " [" + formatLabel + "]");
        } catch (RiotException ex) {
            inputOutput.setType(inputOutput.getType() + " [format]");
        }
    }

    /**
     * Convert RDF URI for a type to a name
     * @param uri The URI for the type
     * @return The human readable name for that type
     */
    private String typeURIToString(String uri) {
        switch (uri) {
            case "http://www.w3.org/2001/XMLSchema#string":
                return "String";
            case "https://w3id.org/cwl/cwl#File":
                return "File";
            case "http://www.w3.org/2001/XMLSchema#boolean":
                return "Boolean";
            case "http://www.w3.org/2001/XMLSchema#int":
                return "Integer";
            case "http://www.w3.org/2001/XMLSchema#double":
                return "Double";
            case "http://www.w3.org/2001/XMLSchema#float":
                return "Float";
            case "http://www.w3.org/2001/XMLSchema#long":
                return "Long";
            case "https://w3id.org/cwl/cwl#Directory":
                return "Directory";
            default:
                return uri;
        }
    }

    /**
     * Converts a yaml String to JsonNode
     * @param path A Path to a file containing the yaml content
     * @return A JsonNode with the content of the document
     * @throws IOException 
     */
    private JsonNode yamlPathToJson(Path path) throws IOException {
        Yaml reader = new Yaml();
        ObjectMapper mapper = new ObjectMapper();
        Path p;
        
        try (InputStream in = Files.newInputStream(path)) {
        	return mapper.valueToTree(reader.load(in));
        }
    }

    
    /**
     * Converts a yaml String to JsonNode
     * @param yamlStream An InputStream containing the yaml content
     * @return A JsonNode with the content of the document
     */
    private JsonNode yamlStreamToJson(InputStream yamlStream) {
        Yaml reader = new Yaml();
        ObjectMapper mapper = new ObjectMapper();
		return mapper.valueToTree(reader.load(yamlStream));
    }

    /**
     * Extract the label from a node
     * @param node The node to have the label extracted from
     * @return The string for the label of the node
     */
    private String extractLabel(JsonNode node) {
        if (node != null && node.has(LABEL)) {
            return node.get(LABEL).asText();
        }
        return null;
    }

    /**
     * Extract the class parameter from a node representing a document
     * @param rootNode The root node of a cwl document
     * @return Which process this document represents
     */
    private CWLProcess extractProcess(JsonNode rootNode) {
        if (rootNode != null) {
            if (rootNode.has(CLASS)) {
                switch(rootNode.get(CLASS).asText()) {
                    case WORKFLOW:
                        return CWLProcess.WORKFLOW;
                    case COMMANDLINETOOL:
                        return CWLProcess.COMMANDLINETOOL;
                    case EXPRESSIONTOOL:
                        return CWLProcess.EXPRESSIONTOOL;
                }
            }
        }
        return null;
    }

    /**
     * Get the steps for a particular document
     * @param cwlDoc The document to get steps for
     * @return A map of step IDs and details related to them
     */
    private Map<String, CWLStep> getSteps(JsonNode cwlDoc) {
        if (cwlDoc != null && cwlDoc.has(STEPS)) {
            Map<String, CWLStep> returnMap = new HashMap<>();

            JsonNode steps = cwlDoc.get(STEPS);
            if (steps.getClass() == ArrayNode.class) {
                // Explicit ID and other fields within each input list
                for (JsonNode step : steps) {
                    CWLStep stepObject = new CWLStep(extractLabel(step), extractDoc(step),
                            extractRun(step), getInputs(step));
                    returnMap.put(extractID(step), stepObject);
                }
            } else if (steps.getClass() == ObjectNode.class) {
                // ID is the key of each object
                Iterator<Map.Entry<String, JsonNode>> iterator = steps.fields();
                while (iterator.hasNext()) {
                    Map.Entry<String, JsonNode> stepNode = iterator.next();
                    JsonNode stepJson = stepNode.getValue();
                    CWLStep stepObject = new CWLStep(extractLabel(stepJson), extractDoc(stepJson),
                            extractRun(stepJson), getInputs(stepJson));
                    returnMap.put(stepNode.getKey(), stepObject);
                }
            }

            return returnMap;
        }
        return null;
    }

    /**
     * Get a the inputs for a particular document
     * @param cwlDoc The document to get inputs for
     * @return A map of input IDs and details related to them
     */
    private Map<String, CWLElement> getInputs(JsonNode cwlDoc) {
        if (cwlDoc != null) {
            if (cwlDoc.has(INPUTS)) {
                // For all version workflow inputs/outputs and draft steps
                return getInputsOutputs(cwlDoc.get(INPUTS));
            } else if (cwlDoc.has(IN)) {
                // For V1.0 steps
                return getStepInputsOutputs(cwlDoc.get(IN));
            }
        }
        return null;
    }

    /**
     * Get the outputs for a particular document
     * @param cwlDoc The document to get outputs for
     * @return A map of output IDs and details related to them
     */
    private Map<String, CWLElement> getOutputs(JsonNode cwlDoc) {
        if (cwlDoc != null) {
            // For all version workflow inputs/outputs and draft steps
            if (cwlDoc.has(OUTPUTS)) {
                return getInputsOutputs(cwlDoc.get(OUTPUTS));
            }
            // Outputs are not gathered for v1 steps
        }
        return null;
    }

    /**
     * Get inputs or outputs from an in or out node
     * @param inOut The in or out node
     * @return A map of input IDs and details related to them
     */
    private Map<String, CWLElement> getStepInputsOutputs(JsonNode inOut) {
        Map<String, CWLElement> returnMap = new HashMap<>();

        if (inOut.getClass() == ArrayNode.class) {
            // array<WorkflowStepInput>
            for (JsonNode inOutNode : inOut) {
                if (inOutNode.getClass() == ObjectNode.class) {
                    CWLElement inputOutput = new CWLElement();
                    List<String> sources = extractSource(inOutNode);
                    if (sources.size() > 0) {
                        for (String source : sources) {
                            inputOutput.addSourceID(stepIDFromSource(source));
                        }
                    } else {
                        inputOutput.setDefaultVal(extractDefault(inOutNode));
                    }
                    returnMap.put(extractID(inOutNode), inputOutput);
                }
            }
        } else if (inOut.getClass() == ObjectNode.class) {
            // map<WorkflowStepInput.id, WorkflowStepInput.source>
            Iterator<Map.Entry<String, JsonNode>> iterator = inOut.fields();
            while (iterator.hasNext()) {
                Map.Entry<String, JsonNode> inOutNode = iterator.next();
                CWLElement inputOutput = new CWLElement();
                if (inOutNode.getValue().getClass() == ObjectNode.class) {
                    JsonNode properties = inOutNode.getValue();
                    if (properties.has(SOURCE)) {
                        inputOutput.addSourceID(stepIDFromSource(properties.get(SOURCE).asText()));
                    } else {
                        inputOutput.setDefaultVal(extractDefault(properties));
                    }
                } else if (inOutNode.getValue().getClass() == ArrayNode.class) {
                    for (JsonNode key : inOutNode.getValue()) {
                        inputOutput.addSourceID(stepIDFromSource(key.asText()));
                    }
                } else {
                    inputOutput.addSourceID(stepIDFromSource(inOutNode.getValue().asText()));
                }
                returnMap.put(inOutNode.getKey(), inputOutput);
            }
        }

        return returnMap;
    }

    /**
     * Get inputs or outputs from an inputs or outputs node
     * @param inputsOutputs The inputs or outputs node
     * @return A map of input IDs and details related to them
     */
    private Map<String, CWLElement> getInputsOutputs(JsonNode inputsOutputs) {
        Map<String, CWLElement> returnMap = new HashMap<>();

        if (inputsOutputs.getClass() == ArrayNode.class) {
            // Explicit ID and other fields within each list
            for (JsonNode inputOutput : inputsOutputs) {
                String id = inputOutput.get(ID).asText();
                if (id.charAt(0) == '#') {
                    id = id.substring(1);
                }
                returnMap.put(id, getDetails(inputOutput));
            }
        } else if (inputsOutputs.getClass() == ObjectNode.class) {
            // ID is the key of each object
            Iterator<Map.Entry<String, JsonNode>> iterator = inputsOutputs.fields();
            while (iterator.hasNext()) {
                Map.Entry<String, JsonNode> inputOutputNode = iterator.next();
                returnMap.put(inputOutputNode.getKey(), getDetails(inputOutputNode.getValue()));
            }
        }

        return returnMap;
    }

    /**
     * Gets the details of an input or output
     * @param inputOutput The node of the particular input or output
     * @return An CWLElement object with the label, doc and type extracted
     */
    private CWLElement getDetails(JsonNode inputOutput) {
        if (inputOutput != null) {
            CWLElement details = new CWLElement();

            // Shorthand notation "id: type" - no label/doc/other params
            if (inputOutput.getClass() == TextNode.class) {
                details.setType(inputOutput.asText());
            } else {
                details.setLabel(extractLabel(inputOutput));
                details.setDoc(extractDoc(inputOutput));
                extractSource(inputOutput).forEach(details::addSourceID);
                details.setDefaultVal(extractDefault(inputOutput));

                // Type is only for inputs
                if (inputOutput.has(TYPE)) {
                    details.setType(extractTypes(inputOutput.get(TYPE)));
                }
            }

            return details;
        }
        return null;
    }

    /**
     * Extract the id from a node
     * @param node The node to have the id extracted from
     * @return The string for the id of the node
     */
    private String extractID(JsonNode node) {
        if (node != null && node.has(ID)) {
            String id = node.get(ID).asText();
            if (id.startsWith("#")) {
                return id.substring(1);
            }
            return id;
        }
        return null;
    }

    /**
     * Extract the default value from a node
     * @param node The node to have the label extracted from
     * @return The string for the default value of the node
     */
    private String extractDefault(JsonNode node) {
        if (node != null && node.has(DEFAULT)) {
            if (node.get(DEFAULT).has(LOCATION)) {
                return node.get(DEFAULT).get(LOCATION).asText();
            } else {
                return "\\\"" + node.get(DEFAULT).asText() + "\\\"";
            }
        }
        return null;
    }

    /**
     * Extract the source or outputSource from a node
     * @param node The node to have the sources extracted from
     * @return A list of strings for the sources
     */
    private List<String> extractSource(JsonNode node) {
        if (node != null) {
            List<String> sources = new ArrayList<String>();
            JsonNode sourceNode = null;

            // outputSource and source treated the same
            if (node.has(OUTPUT_SOURCE)) {
                sourceNode = node.get(OUTPUT_SOURCE);
            } else if (node.has(SOURCE)) {
                sourceNode = node.get(SOURCE);
            }

            if (sourceNode != null) {
                // Single source
                if (sourceNode.getClass() == TextNode.class) {
                    sources.add(stepIDFromSource(sourceNode.asText()));
                }
                // Can be an array of multiple sources
                if (sourceNode.getClass() == ArrayNode.class) {
                    for (JsonNode source : sourceNode) {
                        sources.add(stepIDFromSource(source.asText()));
                    }
                }
            }

            return sources;
        }
        return null;
    }

    /**
     * Gets just the step ID from source of format 'stepID</ or .>outputID'
     * @param source The source
     * @return The step ID
     */
    private String stepIDFromSource(String source) {
        if (source != null && source.length() > 0) {
            // Strip leading # if it exists
            if (source.charAt(0) == '#') {
                source = source.substring(1);
            }

            // Draft 3/V1 notation is 'stepID/outputID'
            int slashSplit = source.indexOf("/");
            if (slashSplit != -1) {
                source = source.substring(0, slashSplit);
            } else {
                // Draft 2 notation was 'stepID.outputID'
                int dotSplit = source.indexOf(".");
                if (dotSplit != -1) {
                    source = source.substring(0, dotSplit);
                }
            }
        }
        return source;
    }

    /**
     * Extract the doc or description from a node
     * @param node The node to have the doc/description extracted from
     * @return The string for the doc/description of the node
     */
    private String extractDoc(JsonNode node) {
        if (node != null) {
            if (node.has(DOC)) {
                return node.get(DOC).asText();
            } else if (node.has(DESCRIPTION)) {
                // This is to support older standards of cwl which use description instead of doc
                return node.get(DESCRIPTION).asText();
            }
        }
        return null;
    }

    /**
     * Extract the types from a node representing inputs or outputs
     * @param typeNode The root node representing an input or output
     * @return A string with the types listed
     */
    private String extractTypes(JsonNode typeNode) {
        if (typeNode != null) {
            if (typeNode.getClass() == TextNode.class) {
                // Single type
                return typeNode.asText();
            } else if (typeNode.getClass() == ArrayNode.class) {
                // Multiple types, build a string to represent them
                StringBuilder typeDetails = new StringBuilder();
                boolean optional = false;
                for (JsonNode type : typeNode) {
                    if (type.getClass() == TextNode.class) {
                        // This is a simple type
                        if (type.asText().equals("null")) {
                            // null as a type means this field is optional
                            optional = true;
                        } else {
                            // Add a simple type to the string
                            typeDetails.append(type.asText());
                            typeDetails.append(", ");
                        }
                    } else if (typeNode.getClass() == ArrayNode.class) {
                        // This is a verbose type with sub-fields broken down into type: and other params
                        if (type.get(TYPE).asText().equals(ARRAY)) {
                            typeDetails.append(type.get(ARRAY_ITEMS).asText());
                            typeDetails.append("[], ");
                        } else {
                            typeDetails.append(type.get(TYPE).asText());
                        }
                    }
                }

                // Trim off excessive separators
                if (typeDetails.length() > 1) {
                    typeDetails.setLength(typeDetails.length() - 2);
                }

                // Add optional if null was included in the multiple types
                if (optional) typeDetails.append("?");

                // Set the type to the constructed string
                return typeDetails.toString();

            } else if (typeNode.getClass() == ObjectNode.class) {
                // Type: array and items:
                if (typeNode.has(ARRAY_ITEMS)) {
                    return typeNode.get(ARRAY_ITEMS).asText() + "[]";
                }
            }
        }
        return null;
    }

    /**
     * Extract the run parameter from a node representing a step
     * @param stepNode The root node of a step
     * @return A string with the run parameter if it exists
     */
    private String extractRun(JsonNode stepNode) {
        if (stepNode != null) {
            if (stepNode.has(RUN)) {
                return stepNode.get(RUN).asText();
            }
        }
        return null;
    }

}
