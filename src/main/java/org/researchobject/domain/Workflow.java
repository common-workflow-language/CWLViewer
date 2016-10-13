package org.researchobject.domain;

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

import org.apache.taverna.cwl.utilities.PortDetail;

import java.util.Map;

/**
 * Representation of a workflow
 */
public class Workflow {

    private String label;
    private String doc;
    private Map<String, InputOutput> inputs;
    private Map<String, InputOutput> outputs;

    public Workflow(String label, String doc, Map<String, InputOutput> inputs, Map<String, InputOutput> outputs) {
        this.label = label;
        this.doc = doc;
        this.inputs = inputs;
        this.outputs = outputs;
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

    public Map<String, InputOutput> getInputs() {
        return inputs;
    }

    public void setInputs(Map<String, InputOutput> inputs) {
        this.inputs = inputs;
    }

    public Map<String, InputOutput> getOutputs() {
        return outputs;
    }

    public void setOutputs(Map<String, InputOutput> outputs) {
        this.outputs = outputs;
    }
}
