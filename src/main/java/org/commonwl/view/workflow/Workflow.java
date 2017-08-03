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

package org.commonwl.view.workflow;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.commonwl.view.cwl.CWLElement;
import org.commonwl.view.cwl.CWLStep;
import org.commonwl.view.git.GitDetails;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;
import java.util.Map;

/**
 * Representation of a workflow
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(value = {"id", "roBundlePath", "roBundleLink"})
@Document
public class Workflow {

    // ID for database
    @Id
    public String id;

    // Metadata
    @Indexed(unique = true)
    private GitDetails retrievedFrom;
    @DateTimeFormat(pattern="yyyy-MM-dd HH:mm:ss z")
    private Date retrievedOn;

    // The last commit from the branch at the time of fetching
    // Used for caching purposes
    private String lastCommit;

    // If schema salad packed, the workflow ID
    private String packedWorkflowID;

    // A String which represents the path to a RO bundle
    // Path types cannot be stored using Spring Data, unfortunately
    private String roBundlePath;

    // Contents of the workflow
    private String label;
    private String doc;
    private Map<String, CWLElement> inputs;
    private Map<String, CWLElement> outputs;
    private Map<String, CWLStep> steps;

    // Currently only DockerRequirement is parsed for this
    private String dockerLink;

    private String cwltoolVersion = "";

    // DOT graph of the contents
    private String visualisationDot;

    public Workflow() {}

    public Workflow(String label, String doc, Map<String, CWLElement> inputs,
                    Map<String, CWLElement> outputs, Map<String, CWLStep> steps, String dockerLink) {
        this.label = label;
        this.doc = doc;
        this.inputs = inputs;
        this.outputs = outputs;
        this.steps = steps;
        this.dockerLink = dockerLink;
    }

    public Workflow(GitDetails gitDetails) {
        this.retrievedFrom = gitDetails;
    }

    public String getID() { return id; }

    public void setId(String id) {
        this.id = id;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getDoc() {
        return doc;
    }

    public void setDoc(String doc) {
        this.doc = doc;
    }

    public Map<String, CWLElement> getInputs() {
        return inputs;
    }

    public void setInputs(Map<String, CWLElement> inputs) {
        this.inputs = inputs;
    }

    public Map<String, CWLElement> getOutputs() {
        return outputs;
    }

    public void setOutputs(Map<String, CWLElement> outputs) {
        this.outputs = outputs;
    }

    public Map<String, CWLStep> getSteps() {
        return steps;
    }

    public void setSteps(Map<String, CWLStep> steps) {
        this.steps = steps;
    }

    public String getRoBundlePath() {
        return roBundlePath;
    }

    public void setRoBundlePath(String roBundlePath) {
        this.roBundlePath = roBundlePath;
    }

    public GitDetails getRetrievedFrom() {
        return retrievedFrom;
    }

    public void setRetrievedFrom(GitDetails retrievedFrom) {
        this.retrievedFrom = retrievedFrom;
    }

    public Date getRetrievedOn() {
        return retrievedOn;
    }

    public void setRetrievedOn(Date retrievedOn) {
        this.retrievedOn = retrievedOn;
    }

    public String getLastCommit() {
        return lastCommit;
    }

    public void setLastCommit(String lastCommit) {
        this.lastCommit = lastCommit;
    }

    public String getPackedWorkflowID() {
        return packedWorkflowID;
    }

    public void setPackedWorkflowID(String packedWorkflowID) {
        this.packedWorkflowID = packedWorkflowID;
    }

    public String getDockerLink() {
        return dockerLink;
    }

    public void setDockerLink(String dockerLink) {
        this.dockerLink = dockerLink;
    }

    public String getCwltoolVersion() {
        return cwltoolVersion;
    }

    public void setCwltoolVersion(String cwltoolVersion) {
        this.cwltoolVersion = cwltoolVersion;
    }

    public String getVisualisationDot() {
        return visualisationDot;
    }

    public void setVisualisationDot(String visualisationDot) {
        this.visualisationDot = visualisationDot;
    }

    // The following are here for Jackson message converter for the REST API
    // Include links to related resources

    public String getVisualisationXdot() {
        return retrievedFrom.getInternalUrl().replaceFirst("/workflows", "/graph/xdot");
    }

    public String getVisualisationPng() {
        return retrievedFrom.getInternalUrl().replaceFirst("/workflows", "/graph/png");
    }

    public String getVisualisationSvg() {
        return retrievedFrom.getInternalUrl().replaceFirst("/workflows", "/graph/svg");
    }

    public String getRoBundle() {
        if (roBundlePath != null) {
            return getRoBundleLink();
        } else {
            return null;
        }
    }

    public String getRoBundleLink() {
        return retrievedFrom.getInternalUrl().replaceFirst("/workflows", "/robundle");
    }
}
