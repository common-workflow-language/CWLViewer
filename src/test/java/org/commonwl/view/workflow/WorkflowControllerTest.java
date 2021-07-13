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

import static org.hamcrest.core.Is.is;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.commonwl.view.cwl.CWLService;
import org.commonwl.view.git.GitDetails;
import org.commonwl.view.graphviz.GraphVizService;
import org.commonwl.view.researchobject.ROBundleNotFoundException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.api.errors.WrongRepositoryStateException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Matchers;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.PathResource;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

/**
 * Tests the controller for workflow related functionality
 */
@RunWith(SpringRunner.class)
@SpringBootTest
public class WorkflowControllerTest {

    /**
     * Use a temporary directory for testing
     */
    @Rule
    public TemporaryFolder roBundleFolder = new TemporaryFolder();

    /**
     * Get the full list of workflows
     * TODO: Mock the repository and test model attributes
     */
    @Test
    public void getListOfWorkflows() throws Exception {

        // Mock controller/MVC
        WorkflowController workflowController = new WorkflowController(
                Mockito.mock(WorkflowFormValidator.class),
                Mockito.mock(WorkflowService.class),
                Mockito.mock(GraphVizService.class),
                Mockito.mock(CWLService.class));

        // Lots of hassle to make Spring Data Pageable work
        PageableHandlerMethodArgumentResolver pageableArgumentResolver =
                new PageableHandlerMethodArgumentResolver();
        InternalResourceViewResolver viewResolver = new InternalResourceViewResolver();
        viewResolver.setPrefix("/src/main/resources/templates");
        viewResolver.setSuffix(".html");
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(workflowController)
                .setCustomArgumentResolvers(pageableArgumentResolver)
                .setViewResolvers(viewResolver)
                .build();

        // Simple test to check the view
        mockMvc.perform(get("/workflows"))
                .andExpect(status().isOk())
                .andExpect(view().name("workflows"));

    }

    /**
     * Endpoint for main form submission
     */
    @Test
    public void newWorkflowFromGithubURL() throws Exception {

        // Validator pass or fail
        WorkflowFormValidator mockValidator = Mockito.mock(WorkflowFormValidator.class);
        when(mockValidator.validateAndParse(anyObject(), anyObject()))
                .thenReturn(null)
                .thenReturn(new GitDetails("https://github.com/owner/repoName.git", "branch", "path/within"))
                .thenReturn(new GitDetails("https://github.com/owner/repoName.git", "branch", "path/workflow.cwl"));

        // The eventual accepted valid workflow
        Workflow mockWorkflow = Mockito.mock(Workflow.class);
        when(mockWorkflow.getRetrievedFrom())
                .thenReturn(new GitDetails("https://github.com/owner/repoName.git", "branch", "path/workflow.cwl"));
        QueuedWorkflow mockQueuedWorkflow = Mockito.mock(QueuedWorkflow.class);
        when(mockQueuedWorkflow.getTempRepresentation())
                .thenReturn(mockWorkflow);

        // Mock workflow service returning valid workflow
        WorkflowService mockWorkflowService = Mockito.mock(WorkflowService.class);
        when(mockWorkflowService.createQueuedWorkflow(anyObject()))
                .thenThrow(new WorkflowNotFoundException())
                .thenThrow(new WrongRepositoryStateException("Some Error"))
                .thenThrow(new TransportException("No SSH Key"))
                .thenThrow(new IOException())
                .thenReturn(mockQueuedWorkflow);

        // Mock controller/MVC
        WorkflowController workflowController = new WorkflowController(
                mockValidator,
                mockWorkflowService,
                Mockito.mock(GraphVizService.class),
                Mockito.mock(CWLService.class)
                );
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(workflowController)
                .build();

        // Error in validation, go to index to show error
        mockMvc.perform(post("/workflows")
                .param("url", "invalidurl"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"));

        // Valid directory URL redirect
        mockMvc.perform(post("/workflows")
                .param("url", "https://github.com/owner/repoName/blob/branch/path/within"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/workflows/github.com/owner/repoName/blob/branch/path/within"));

        // Invalid workflow URL, go to index to show error
        mockMvc.perform(post("/workflows")
                .param("url", "https://github.com/owner/repoName/blob/branch/path/nonexistant.cwl"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attributeHasFieldErrors("workflowForm", "url"));

        // Git API error
        mockMvc.perform(post("/workflows")
                .param("url", "https://github.com/owner/repoName/blob/branch/path/cantbecloned.cwl"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attributeHasFieldErrors("workflowForm", "url"));

        // Unsupported SSH URL
        mockMvc.perform(post("/workflows")
                .param("url", "ssh://github.com/owner/repoName/blob/branch/path/workflow.cwl"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attributeHasFieldErrors("workflowForm", "url"));

        // Unexpected error
        mockMvc.perform(post("/workflows")
                .param("url", "ssh://github.com/owner/repoName/blob/branch/path/unexpected.cwl"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attributeHasFieldErrors("workflowForm", "url"));

        // Valid workflow URL redirect
        mockMvc.perform(post("/workflows")
                .param("url", "https://github.com/owner/repoName/blob/branch/path/workflow.cwl"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/workflows/github.com/owner/repoName/blob/branch/path/workflow.cwl"));

    }

    /**
     * Displaying workflows
     */
    @Test
    public void directWorkflowURL() throws Exception {

        Workflow mockWorkflow = Mockito.mock(Workflow.class);
        QueuedWorkflow mockQueuedWorkflow = Mockito.mock(QueuedWorkflow.class);
        when(mockQueuedWorkflow.getWorkflowList()).thenReturn(null);

        // Mock service
        WorkflowService mockWorkflowService = Mockito.mock(WorkflowService.class);
        when(mockWorkflowService.getWorkflow(Matchers.<GitDetails>anyObject()))
                .thenReturn(mockWorkflow)
                .thenReturn(null);
        when(mockWorkflowService.createQueuedWorkflow(anyObject()))
                .thenReturn(mockQueuedWorkflow)
                .thenThrow(new WorkflowNotFoundException())
                .thenThrow(new WrongRepositoryStateException("Some Error"))
                .thenThrow(new TransportException("No SSH Key"))
                .thenThrow(new IOException());
        List<WorkflowOverview> listOfTwoOverviews = new ArrayList<>();
        listOfTwoOverviews.add(new WorkflowOverview("/workflow1.cwl", "label", "doc"));
        listOfTwoOverviews.add(new WorkflowOverview("/workflow2.cwl", "label2", "doc2"));
        when(mockWorkflowService.getWorkflowsFromDirectory(anyObject()))
                .thenReturn(listOfTwoOverviews)
                .thenReturn(Collections.singletonList(new WorkflowOverview("/workflow1.cwl", "label", "doc")));

        // Mock controller/MVC
        WorkflowController workflowController = new WorkflowController(
                Mockito.mock(WorkflowFormValidator.class),
                mockWorkflowService,
                Mockito.mock(GraphVizService.class),
                Mockito.mock(CWLService.class));
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(workflowController)
                .build();

        // Workflow already exists in the database
        mockMvc.perform(get("/workflows/github.com/owner/reponame/tree/branch/path/within/workflow.cwl"))
                .andExpect(status().isOk())
                .andExpect(view().name("workflow"))
                .andExpect(model().attribute("workflow", is(mockWorkflow)));

        // Workflow needs to be created, loading page
        mockMvc.perform(get("/workflows/github.com/owner/reponame/tree/branch/path/within/workflow.cwl"))
                .andExpect(status().isOk())
                .andExpect(view().name("loading"))
                .andExpect(model().attribute("queued", is(mockQueuedWorkflow)));

        // Directory URL, select between
        mockMvc.perform(get("/workflows/github.com/owner/reponame/tree/branch/path/within"))
                .andExpect(status().isOk())
                .andExpect(view().name("selectworkflow"))
                .andExpect(model().attributeExists("gitDetails"))
                .andExpect(model().attributeExists("workflowOverviews"));

        // Directory URL with only one workflow redirects
        mockMvc.perform(get("/workflows/github.com/owner/reponame/tree/branch/path/within"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/workflows/github.com/owner/reponame/blob/branch/path/within/workflow1.cwl"));

        // Workflow not found
        mockMvc.perform(get("/workflows/github.com/owner/reponame/tree/branch/path/within/nonexistant.cwl"))
                .andExpect(status().isFound())
                .andExpect(MockMvcResultMatchers.flash().attributeExists("errors"));

        // Git API error
        mockMvc.perform(get("/workflows/github.com/owner/reponame/tree/branch/path/within/cantbecloned.cwl"))
                .andExpect(status().isFound())
                .andExpect(MockMvcResultMatchers.flash().attributeExists("errors"));

        // Submodules with SSH Url
        mockMvc.perform(get("/workflows/github.com/owner/reponame/tree/branch/path/within/submodulewithssh.cwl"))
                .andExpect(status().isFound())
                .andExpect(MockMvcResultMatchers.flash().attributeExists("errors"));

        // Unexpected error
        mockMvc.perform(get("/workflows/github.com/owner/reponame/tree/branch/path/within/badworkflow.cwl"))
                .andExpect(status().isFound())
                .andExpect(MockMvcResultMatchers.flash().attributeExists("errors"));
    }

    /**
     * Endpoint for downloading RO bundle for a workflow
     */
    @Test
    public void downloadROBundle() throws Exception {

        // Mock service to return a bundle file and then throw ROBundleNotFoundException
        WorkflowService mockWorkflowService = Mockito.mock(WorkflowService.class);
        File bundle = roBundleFolder.newFile("bundle.zip").getAbsoluteFile();
        when(mockWorkflowService.getROBundle(anyObject()))
                .thenReturn(bundle)
                .thenReturn(bundle)
                .thenThrow(new ROBundleNotFoundException());

        // Mock controller/MVC
        WorkflowController workflowController = new WorkflowController(
                Mockito.mock(WorkflowFormValidator.class),
                mockWorkflowService,
                Mockito.mock(GraphVizService.class),
                Mockito.mock(CWLService.class));
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(workflowController)
                .build();

        // Bundle exists and can be downloaded
        mockMvc.perform(get("/robundle/github.com/owner/repo/blob/branch/path/to/workflow.cwl"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/vnd.wf4ever.robundle+zip"));

        // Generic git and bundle exists
        mockMvc.perform(get("/robundle/bitbucket.org/owner/repo.git/branch/path/to/workflow.cwl"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/vnd.wf4ever.robundle+zip"));

        // Bundle does not exist, 404 error
        mockMvc.perform(get("/robundle/github.com/owner/repo/blob/branch/path/to/workflow.cwl"))
                .andExpect(status().isNotFound());

    }

    /**
     * Endpoints for downloading GraphViz graph files
     */
    @Test
    public void downloadGraphVizFiles() throws Exception {

        // Mock service to return mock workflow
        WorkflowService mockWorkflowService = Mockito.mock(WorkflowService.class);
        when(mockWorkflowService.getWorkflowGraph(anyString(), anyObject()))
                .thenReturn(new PathResource(Paths.get("src/test/resources/graphviz/testVis.svg")))
                .thenReturn(new PathResource(Paths.get("src/test/resources/graphviz/testVis.png")))
                .thenReturn(new PathResource(Paths.get("src/test/resources/graphviz/testWorkflow.dot")))
                .thenReturn(new PathResource(Paths.get("src/test/resources/graphviz/testVis.svg")))
                .thenReturn(new PathResource(Paths.get("src/test/resources/graphviz/testVis.png")))
                .thenReturn(new PathResource(Paths.get("src/test/resources/graphviz/testWorkflow.dot")))
                .thenThrow(new WorkflowNotFoundException());

        // Mock controller/MVC
        WorkflowController workflowController = new WorkflowController(
                Mockito.mock(WorkflowFormValidator.class),
                mockWorkflowService,
                Mockito.mock(GraphVizService.class),
                Mockito.mock(CWLService.class));
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(workflowController)
                .build();

        // Images exist and can be downloaded
        mockMvc.perform(get("/graph/svg/github.com/owner/repo/blob/branch/path/to/workflow.cwl"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/svg+xml"));
        mockMvc.perform(get("/graph/png/github.com/owner/repo/blob/branch/path/to/workflow.cwl"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"));
        mockMvc.perform(get("/graph/xdot/github.com/owner/repo/blob/branch/path/to/workflow.cwl"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/vnd.graphviz"));

        // Images exist at generic git URLs
        mockMvc.perform(get("/graph/svg/bitbucket.org/owner/repo.git/branch/path/to/workflow.cwl"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/svg+xml"));
        mockMvc.perform(get("/graph/png/bitbucket.org/owner/repo.git/branch/path/to/workflow.cwl"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"));
        mockMvc.perform(get("/graph/xdot/bitbucket.org/owner/repo.git/branch/path/to/workflow.cwl"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/vnd.graphviz"));

        // Images do not exist, 404 error
        mockMvc.perform(get("/graph/png/github.com/owner/repo/blob/branch/path/to/workflow.cwl"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/graph/svg/github.com/owner/repo/blob/branch/path/to/workflow.cwl"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/graph/xdot/github.com/owner/repo/blob/branch/path/to/workflow.cwl"))
                .andExpect(status().isNotFound());

    }

    @Test
    public void downloadGraphSvgFromFile() throws Exception {

        // Mock service to return a bundle file and then throw ROBundleNotFoundException
        WorkflowService mockWorkflowService = Mockito.mock(WorkflowService.class);

        // Mock controller/MVC
        CWLService mockCWLService = Mockito.mock(CWLService.class);
		GraphVizService graphVizService = Mockito.mock(GraphVizService.class);
		
		when(graphVizService.getGraphStream(anyString(), eq("svg")))
				.thenReturn(getClass().getResourceAsStream("/graphviz/testWorkflow.dot"));
		Workflow mockWorkflow = Mockito.mock(Workflow.class);
		when(mockWorkflow.getVisualisationDot()).thenReturn("");// Not actually dot
		when(mockCWLService.parseWorkflowNative(Matchers.any(InputStream.class), eq(""), anyString()))
				.thenReturn(mockWorkflow);
		
		WorkflowController workflowController = new WorkflowController(
                Mockito.mock(WorkflowFormValidator.class),
                mockWorkflowService,
                graphVizService,
                mockCWLService);
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(workflowController)
                .build();
        
        mockMvc.perform(post("/graph/svg"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/svg+xml"));

    }

    @Test
    public void downloadGraphPngFromFile() throws Exception {

        // Mock service to return a bundle file and then throw ROBundleNotFoundException
        WorkflowService mockWorkflowService = Mockito.mock(WorkflowService.class);

        // Mock controller/MVC
        CWLService mockCWLService = Mockito.mock(CWLService.class);
		GraphVizService graphVizService = Mockito.mock(GraphVizService.class);
		
		when(graphVizService.getGraphStream(anyString(), eq("png")))
				.thenReturn(getClass().getResourceAsStream("/graphviz/testWorkflow.dot"));
		Workflow mockWorkflow = Mockito.mock(Workflow.class);
		when(mockWorkflow.getVisualisationDot()).thenReturn("");// Not actually dot
		when(mockCWLService.parseWorkflowNative(Matchers.any(InputStream.class), eq(""), anyString()))
				.thenReturn(mockWorkflow);
		
		WorkflowController workflowController = new WorkflowController(
                Mockito.mock(WorkflowFormValidator.class),
                mockWorkflowService,
                graphVizService,
                mockCWLService);
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(workflowController)
                .build();
        
        mockMvc.perform(post("/graph/png"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"));

    }
    
    
}
