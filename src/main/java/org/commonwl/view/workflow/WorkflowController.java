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

import org.apache.commons.lang.StringUtils;
import org.commonwl.view.cwl.CWLToolStatus;
import org.commonwl.view.git.GitDetails;
import org.commonwl.view.graphviz.GraphVizService;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.io.File;
import java.io.IOException;

@Controller
public class WorkflowController {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final WorkflowFormValidator workflowFormValidator;
    private final WorkflowService workflowService;
    private final GraphVizService graphVizService;

    /**
     * Autowired constructor to initialise objects used by the controller
     * @param workflowFormValidator Validator to validate the workflow form
     * @param workflowService Builds new Workflow objects
     * @param graphVizService Generates and stores imagess
     */
    @Autowired
    public WorkflowController(WorkflowFormValidator workflowFormValidator,
                              WorkflowService workflowService,
                              GraphVizService graphVizService) {
        this.workflowFormValidator = workflowFormValidator;
        this.workflowService = workflowService;
        this.graphVizService = graphVizService;
    }

    /**
     * List all the workflows in the database, paginated
     * @param model The model for the page
     * @param pageable Pagination for the list of workflows
     * @return The workflows view
     */
    @GetMapping(value="/workflows")
    public String listWorkflows(Model model, @PageableDefault(size = 10) Pageable pageable) {
        model.addAttribute("workflows", workflowService.getPageOfWorkflows(pageable));
        model.addAttribute("pages", pageable);
        return "workflows";
    }

    /**
     * Search all the workflows in the database, paginated
     * @param model The model for the page
     * @param pageable Pagination for the list of workflows
     * @return The workflows view
     */
    @GetMapping(value="/workflows", params="search")
    public String searchWorkflows(Model model,
                                  @PageableDefault(size = 10) Pageable pageable,
                                  @RequestParam(value = "search") String search) {
        model.addAttribute("workflows", workflowService.searchPageOfWorkflows(search, pageable));
        model.addAttribute("pages", pageable);
        model.addAttribute("search", search);
        return "workflows";
    }

    /**
     * Create a new workflow from the given URL in the form
     * @param workflowForm The data submitted from the form
     * @param bindingResult Spring MVC Binding Result object
     * @return The workflow view with new workflow as a model
     */
    @PostMapping("/workflows")
    public ModelAndView createWorkflow(@Valid WorkflowForm workflowForm, BindingResult bindingResult) {

        // Run validator which checks the git URL is valid
        GitDetails gitInfo = workflowFormValidator.validateAndParse(workflowForm, bindingResult);

        if (bindingResult.hasErrors() || gitInfo == null) {
            // Go back to index if there are validation errors
            return new ModelAndView("index");
        } else {
            // Get workflow or create if does not exist
            Workflow workflow = workflowService.getWorkflow(gitInfo);
            if (workflow == null) {
                try {
                    workflow = workflowService.createQueuedWorkflow(gitInfo).getTempRepresentation();
                } catch (GitAPIException ex) {
                    bindingResult.rejectValue("url", "git.retrievalError");
                    logger.error("Git API Error", ex);
                    return new ModelAndView("index");
                } catch (WorkflowNotFoundException ex) {
                    bindingResult.rejectValue("url", "git.pathTraversal");
                    return new ModelAndView("index");
                } catch (Exception ex) {
                    bindingResult.rejectValue("url", "url.parsingError");
                    return new ModelAndView("index");
                }
            }
            gitInfo = workflow.getRetrievedFrom();

            // Redirect to the workflow
            return new ModelAndView("redirect:" + gitInfo.getInternalUrl());
        }
    }

    /**
     * Display a page for a particular workflow from Github or Github format URL
     * @param domain The domain of the hosting site, Github or Gitlab
     * @param owner The owner of the repository
     * @param repoName The name of the repository
     * @param branch The branch of repository
     * @return The workflow view with the workflow as a model
     */
    @GetMapping(value={"/workflows/{domain}.com/{owner}/{repoName}/tree/{branch}/**",
                       "/workflows/{domain}.com/{owner}/{repoName}/blob/{branch}/**"})
    public ModelAndView getWorkflow(@Value("${applicationURL}") String applicationURL,
                                    @PathVariable("domain") String domain,
                                    @PathVariable("owner") String owner,
                                    @PathVariable("repoName") String repoName,
                                    @PathVariable("branch") String branch,
                                    HttpServletRequest request,
                                    RedirectAttributes redirectAttrs) {
        // The wildcard end of the URL is the path
        String path = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        path = extractPath(path, 7);

        // Construct a GitDetails object to search for in the database
        GitDetails gitDetails = getGitDetails(domain, owner, repoName, branch, path);

        // Get workflow
        ModelAndView modelAndView = getWorkflow(gitDetails, redirectAttrs);
        return modelAndView.addObject("appURL", applicationURL);
    }

    /**
     * Display page for a workflow from a generic Git URL
     * @param branch The branch of the repository
     * @return The workflow view with the workflow as a model
     */
    @GetMapping(value="/workflows/**/*.git/{branch}/**")
    public ModelAndView getWorkflowGeneric(@Value("${applicationURL}") String applicationURL,
                                           @PathVariable("branch") String branch,
                                           HttpServletRequest request,
                                           RedirectAttributes redirectAttrs) {
        String path = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        GitDetails gitDetails = getGitDetails(11, path, branch);
        ModelAndView modelAndView = getWorkflow(gitDetails, redirectAttrs);
        return modelAndView.addObject("appURL", applicationURL);
    }

    /**
     * Download the Research Object Bundle for a particular workflow
     * @param domain The domain of the hosting site, Github or Gitlab
     * @param owner The owner of the repository
     * @param repoName The name of the repository
     * @param branch The branch of repository
     */
    @GetMapping(value={"/robundle/{domain}.com/{owner}/{repoName}/tree/{branch}/**",
                       "/robundle/{domain}.com/{owner}/{repoName}/blob/{branch}/**"},
                produces = "application/vnd.wf4ever.robundle+zip")
    @ResponseBody
    public FileSystemResource getROBundle(@PathVariable("domain") String domain,
                                          @PathVariable("owner") String owner,
                                          @PathVariable("repoName") String repoName,
                                          @PathVariable("branch") String branch,
                                          HttpServletRequest request,
                                          HttpServletResponse response) throws IOException {
        String path = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        path = extractPath(path, 7);
        GitDetails gitDetails = getGitDetails(domain, owner, repoName, branch, path);
        File bundleDownload = workflowService.getROBundle(gitDetails);
        response.setHeader("Content-Disposition", "attachment; filename=bundle.zip;");
        return new FileSystemResource(bundleDownload);
    }

    /**
     * Download the Research Object Bundle for a particular workflow
     * @param branch The branch of repository
     */
    @GetMapping(value="/robundle/**/*.git/{branch}/**",
                produces = "application/vnd.wf4ever.robundle+zip")
    @ResponseBody
    public FileSystemResource getROBundleGeneric(@PathVariable("branch") String branch,
                                                 HttpServletRequest request,
                                                 HttpServletResponse response) throws IOException {
        String path = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        GitDetails gitDetails = getGitDetails(10, path, branch);
        File bundleDownload = workflowService.getROBundle(gitDetails);
        response.setHeader("Content-Disposition", "attachment; filename=bundle.zip;");
        return new FileSystemResource(bundleDownload);
    }


    /**
     * Download a generated graph for a workflow in SVG format
     * @param domain The domain of the hosting site, Github or Gitlab
     * @param owner The owner of the repository
     * @param repoName The name of the repository
     * @param branch The branch of repository
     */
    @GetMapping(value={"/graph/svg/{domain}.com/{owner}/{repoName}/tree/{branch}/**",
                       "/graph/svg/{domain}.com/{owner}/{repoName}/blob/{branch}/**"},
                produces="image/svg+xml")
    @ResponseBody
    public FileSystemResource downloadGraphSvg(@PathVariable("domain") String domain,
                                               @PathVariable("owner") String owner,
                                               @PathVariable("repoName") String repoName,
                                               @PathVariable("branch") String branch,
                                               HttpServletRequest request,
                                               HttpServletResponse response) throws IOException {
        String path = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        path = extractPath(path, 8);
        GitDetails gitDetails = getGitDetails(domain, owner, repoName, branch, path);
        return getGraph("svg", gitDetails, response);
    }

    /**
     * Download a generated graph for a workflow in SVG format
     * @param branch The branch of repository
     */
    @GetMapping(value="/graph/svg/**/*.git/{branch}/**",
                produces="image/svg+xml")
    @ResponseBody
    public FileSystemResource downloadGraphSvgGeneric(@PathVariable("branch") String branch,
                                                      HttpServletRequest request,
                                                      HttpServletResponse response) throws IOException {
        String path = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        GitDetails gitDetails = getGitDetails(11, path, branch);
        return getGraph("svg", gitDetails, response);
    }

    /**
     * Download a generated graph for a workflow in PNG format
     * @param domain The domain of the hosting site, Github or Gitlab
     * @param owner The owner of the repository
     * @param repoName The name of the repository
     * @param branch The branch of repository
     */
    @GetMapping(value={"/graph/png/{domain}.com/{owner}/{repoName}/tree/{branch}/**",
                       "/graph/png/{domain}.com/{owner}/{repoName}/blob/{branch}/**"},
                produces="image/png")
    @ResponseBody
    public FileSystemResource downloadGraphPng(@PathVariable("domain") String domain,
                                               @PathVariable("owner") String owner,
                                               @PathVariable("repoName") String repoName,
                                               @PathVariable("branch") String branch,
                                               HttpServletRequest request,
                                               HttpServletResponse response) throws IOException {
        String path = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        path = extractPath(path, 8);
        GitDetails gitDetails = getGitDetails(domain, owner, repoName, branch, path);
        return getGraph("png", gitDetails, response);
    }

    /**
     * Download a generated graph for a workflow in PNG format
     * @param branch The branch of repository
     */
    @GetMapping(value="/graph/png/**/*.git/{branch}/**",
                produces="image/png")
    @ResponseBody
    public FileSystemResource downloadGraphPngGeneric(@PathVariable("branch") String branch,
                                                      HttpServletRequest request,
                                                      HttpServletResponse response) throws IOException {
        String path = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        GitDetails gitDetails = getGitDetails(11, path, branch);
        return getGraph("png", gitDetails, response);
    }

    /**
     * Download a generated graph for a workflow in XDOT format
     * @param domain The domain of the hosting site, Github or Gitlab
     * @param owner The owner of the repository
     * @param repoName The name of the repository
     * @param branch The branch of repository
     */
    @GetMapping(value={"/graph/xdot/{domain}.com/{owner}/{repoName}/tree/{branch}/**",
                       "/graph/xdot/{domain}.com/{owner}/{repoName}/blob/{branch}/**"},
                produces="text/vnd.graphviz")
    @ResponseBody
    public FileSystemResource downloadGraphDot(@PathVariable("domain") String domain,
                                               @PathVariable("owner") String owner,
                                               @PathVariable("repoName") String repoName,
                                               @PathVariable("branch") String branch,
                                               HttpServletRequest request,
                                               HttpServletResponse response) throws IOException {
        String path = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        path = extractPath(path, 8);
        GitDetails gitDetails = getGitDetails(domain, owner, repoName, branch, path);
        return getGraph("xdot", gitDetails, response);
    }

    /**
     * Download a generated graph for a workflow in XDOT format
     * @param branch The branch of repository
     */
    @GetMapping(value="/graph/xdot/**/*.git/{branch}/**",
                produces="text/vnd.graphviz")
    @ResponseBody
    public FileSystemResource downloadGraphDotGeneric(@PathVariable("branch") String branch,
                                                      HttpServletRequest request,
                                                      HttpServletResponse response) throws IOException {
        String path = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        GitDetails gitDetails = getGitDetails(12, path, branch);
        return getGraph("xdot", gitDetails, response);
    }

    /**
     * Get a temporary graph for a pending workflow
     * @param queueID The ID in the queue
     * @return The visualisation image
     */
    @GetMapping(value={"/queue/{queueID}/tempgraph.png"},
            produces = "image/png")
    @ResponseBody
    public FileSystemResource getTempGraphAsPng(@PathVariable("queueID") String queueID,
                                                HttpServletResponse response) throws IOException {
        QueuedWorkflow queued = workflowService.getQueuedWorkflow(queueID);
        if (queued == null) {
            throw new WorkflowNotFoundException();
        }
        File out = graphVizService.getGraph(queued.getId() + ".png",
                queued.getTempRepresentation().getVisualisationDot(), "png");
        response.setHeader("Content-Disposition", "inline; filename=\"graph.png\"");
        return new FileSystemResource(out);
    }


    /**
     * Extract the path from the end of a full request string
     * @param path The full request string path
     * @param startSlashNum The ordinal slash index of the start of the path
     * @return The path from the end
     */
    public static String extractPath(String path, int startSlashNum) {
        int pathStartIndex = StringUtils.ordinalIndexOf(path, "/", startSlashNum);
        if (pathStartIndex > -1 && pathStartIndex < path.length() - 1) {
            return path.substring(pathStartIndex + 1).replaceAll("\\/$", "");
        } else {
            return "/";
        }
    }

    /**
     * Constructs a GitDetails object for Github/Gitlab details
     * @param domain The domain name (always .com)
     * @param owner The owner of the repository
     * @param repoName The name of the repository
     * @param branch The branch of the repository
     * @param path The path within the repository
     * @return A constructed GitDetails object
     */
    public static GitDetails getGitDetails(String domain, String owner, String repoName,
                                           String branch, String path) {
        String repoUrl;
        switch (domain) {
            case "github":
                repoUrl = "https://github.com/" + owner + "/" + repoName + ".git";
                break;
            case "gitlab":
                repoUrl = "https://gitlab.com/" + owner + "/" + repoName + ".git";
                break;
            default:
                throw new WorkflowNotFoundException();
        }
        return new GitDetails(repoUrl, branch, path);
    }

    /**
     * Constructs a GitDetails object for a generic path
     * @param startIndex The start of the repository URL
     * @param path The entire URL path
     * @param branch The branch of the repository
     * @return A constructed GitDetails object
     */
    public static GitDetails getGitDetails(int startIndex, String path, String branch) {
        // The repository URL is the part after startIndex and up to and including .git
        String repoUrl = path.substring(startIndex);
        int extensionIndex = repoUrl.indexOf(".git");
        if (extensionIndex == -1) {
            throw new WorkflowNotFoundException();
        }
        repoUrl = "https://" + repoUrl.substring(0, extensionIndex + 4);

        // The path is after the branch
        int slashAfterBranch = path.indexOf("/", path.indexOf(branch));
        if (slashAfterBranch == -1 || slashAfterBranch == path.length()) {
            throw new WorkflowNotFoundException();
        }
        path = path.substring(slashAfterBranch + 1);

        // Construct GitDetails object for this workflow
        return new GitDetails(repoUrl, branch, path);
    }

    /**
     * Get a workflow from Git Details, creating if it does not exist
     * @param gitDetails The details of the Git repository
     * @param redirectAttrs Error attributes for redirect
     * @return The model and view to be returned by the controller
     */
    private ModelAndView getWorkflow(GitDetails gitDetails, RedirectAttributes redirectAttrs) {
        // Get workflow
        QueuedWorkflow queued = null;
        Workflow workflowModel = workflowService.getWorkflow(gitDetails);
        if (workflowModel == null) {
            // Check if already queued
            queued = workflowService.getQueuedWorkflow(gitDetails);
            if (queued == null) {
                // Validation
                WorkflowForm workflowForm = new WorkflowForm(gitDetails.getUrl());
                BeanPropertyBindingResult errors = new BeanPropertyBindingResult(workflowForm, "errors");
                workflowFormValidator.validateAndParse(workflowForm, errors);
                if (!errors.hasErrors()) {
                    try {
                        queued = workflowService.createQueuedWorkflow(gitDetails);
                    } catch (GitAPIException ex) {
                        errors.rejectValue("url", "git.retrievalError", "The workflow could not be retrieved from the Git repository using the details given");
                        logger.error("Git API Error", ex);
                    } catch (WorkflowNotFoundException ex) {
                        errors.rejectValue("url", "git.pathTraversal", "The path given did not resolve to a location within the repository");
                    } catch (IOException ex) {
                        errors.rejectValue("url", "url.parsingError", "The workflow could not be parsed from the given URL");
                    }
                }
                // Redirect to main page with errors if they occurred
                if (errors.hasErrors()) {
                    redirectAttrs.addFlashAttribute("errors", errors);
                    return new ModelAndView("redirect:/?url=" + gitDetails.getUrl());
                }
            }
        }

        // Display this model along with the view
        if (queued != null) {
            // Retry creation if there has been an error in cwltool parsing
            if (queued.getCwltoolStatus() == CWLToolStatus.ERROR) {
                workflowService.retryCreate(queued);
            }
            return new ModelAndView("loading", "queued", queued);
        } else {
            return new ModelAndView("workflow", "workflow", workflowModel);
        }
    }

    /**
     * Get a graph in a particular format and return it
     * @param format The format for the graph file
     * @param gitDetails The Git details of the workflow
     * @param response The response object for setting content-disposition header
     * @return A FileSystemResource representing the graph
     * @throws WorkflowNotFoundException Error getting the workflow or format
     */
    private FileSystemResource getGraph(String format, GitDetails gitDetails,
                                        HttpServletResponse response)
            throws WorkflowNotFoundException {
        // Determine file extension from format
        String extension;
        switch (format) {
            case "svg":
            case "png":
                extension = format;
                break;
            case "xdot":
                extension = "dot";
                break;
            default:
                throw new WorkflowNotFoundException();
        }

        // Get workflow
        Workflow workflow = workflowService.getWorkflow(gitDetails);
        if (workflow == null) {
            throw new WorkflowNotFoundException();
        }

        // Generate graph and serve the file
        File out = graphVizService.getGraph(workflow.getID() + "." + extension, workflow.getVisualisationDot(), format);
        response.setHeader("Content-Disposition", "inline; filename=\"graph." + extension + "\"");
        return new FileSystemResource(out);
    }
}
