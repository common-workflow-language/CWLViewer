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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.commonwl.view.cwl.CWLService;
import org.commonwl.view.cwl.CWLToolRunner;
import org.commonwl.view.cwl.CWLToolStatus;
import org.commonwl.view.git.GitDetails;
import org.commonwl.view.git.GitSemaphore;
import org.commonwl.view.git.GitService;
import org.commonwl.view.graphviz.GraphVizService;
import org.commonwl.view.researchobject.ROBundleFactory;
import org.commonwl.view.researchobject.ROBundleNotFoundException;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.PathResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class WorkflowService {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final GitService gitService;
    private final CWLService cwlService;
    private final WorkflowRepository workflowRepository;
    private final QueuedWorkflowRepository queuedWorkflowRepository;
    private final ROBundleFactory ROBundleFactory;
    private final GraphVizService graphVizService;
    private final CWLToolRunner cwlToolRunner;
    private final GitSemaphore gitSemaphore;
    private final int cacheDays;

    @Autowired
    public WorkflowService(GitService gitService,
                           CWLService cwlService,
                           WorkflowRepository workflowRepository,
                           QueuedWorkflowRepository queuedWorkflowRepository,
                           ROBundleFactory ROBundleFactory,
                           GraphVizService graphVizService,
                           CWLToolRunner cwlToolRunner,
                           GitSemaphore gitSemaphore,
                           @Value("${cacheDays}") int cacheDays) {
        this.gitService = gitService;
        this.cwlService = cwlService;
        this.workflowRepository = workflowRepository;
        this.queuedWorkflowRepository = queuedWorkflowRepository;
        this.ROBundleFactory = ROBundleFactory;
        this.graphVizService = graphVizService;
        this.cwlToolRunner = cwlToolRunner;
        this.cacheDays = cacheDays;
        this.gitSemaphore = gitSemaphore;
    }

    /**
     * Gets a page of all workflows from the database
     * @param pageable The details of the page to be requested
     * @return The resulting page of the workflow entries
     */
    public Page<Workflow> getPageOfWorkflows(Pageable pageable) {
        return workflowRepository.findAllByOrderByRetrievedOnDesc(pageable);
    }

    /**
     * Gets a page of all workflows from the database
     * @param searchString The string to search for
     * @param pageable The details of the page to be requested
     * @return The resulting page of the workflow entries
     */
    public Page<Workflow> searchPageOfWorkflows(String searchString, Pageable pageable) {
        return workflowRepository.findByLabelContainingOrDocContainingIgnoreCase(searchString, searchString, pageable);
    }

    /**
     * Get a workflow from the database by its ID
     * @param id The ID of the workflow
     * @return The model for the workflow
     */
    public Workflow getWorkflow(String id) {
        return workflowRepository.findById(id).orElse(null);
    }

    /**
     * Get a queued workflow from the database by its ID
     * @param id The ID of the queued workflow
     * @return The model for the queued workflow
     */
    public QueuedWorkflow getQueuedWorkflow(String id) {
        return queuedWorkflowRepository.findById(id).orElse(null);
    }

    /**
     * Get a queued workflow from the database
     * @param githubInfo Github information for the workflow
     * @return The queued workflow model
     */
    public QueuedWorkflow getQueuedWorkflow(GitDetails githubInfo) {
        QueuedWorkflow queued = queuedWorkflowRepository.findByRetrievedFrom(githubInfo);

        // Slash in branch fix
        boolean slashesInPath = true;
        while (queued == null && slashesInPath) {
            GitDetails correctedForSlash = gitService.transferPathToBranch(githubInfo);
            if (correctedForSlash != null) {
                githubInfo = correctedForSlash;
                queued = queuedWorkflowRepository.findByRetrievedFrom(githubInfo);
            } else {
                slashesInPath = false;
            }
        }

        return queued;
    }

    /**
     * Get a workflow from the database, refreshing it if cache has expired
     * @param gitInfo Git information for the workflow
     * @return The workflow model associated with gitInfo
     */
    public Workflow getWorkflow(GitDetails gitInfo) {
        // Check database for existing workflows from this repository
        Workflow workflow = workflowRepository.findByRetrievedFrom(gitInfo);

        // Slash in branch fix
        boolean slashesInPath = true;
        while (workflow == null && slashesInPath) {
            GitDetails correctedForSlash = gitService.transferPathToBranch(gitInfo);
            if (correctedForSlash != null) {
                gitInfo = correctedForSlash;
                workflow = workflowRepository.findByRetrievedFrom(gitInfo);
            } else {
                slashesInPath = false;
            }
        }

        // Cache update
        if (workflow != null) {
            // Delete the existing workflow if the cache has expired
            if (cacheExpired(workflow)) {
                removeWorkflow(workflow);

                // Add the new workflow if it exists
                try {
                    createQueuedWorkflow(workflow.getRetrievedFrom());

                    // Add the old commit for the purposes of permalinks
                    // TODO: Separate concept of commit from branch ref, see #164
                    GitDetails byOldCommitId = workflow.getRetrievedFrom();
                    byOldCommitId.setBranch(workflow.getLastCommit());
                    if (getQueuedWorkflow(byOldCommitId) == null
                            && getWorkflow(byOldCommitId) == null) {
                        createQueuedWorkflow(byOldCommitId);
                    }

                    workflow = null;
                } catch (Exception e) {
                    // Add back the old workflow if it is broken now
                    logger.error("Could not parse updated workflow " + workflow.getID());
                    workflowRepository.save(workflow);
                }
            }
        }

        return workflow;
    }

    /**
     * Get a list of workflows from a directory
     * @param gitInfo The Git directory information
     * @return The list of workflow overviews
     */
    public List<WorkflowOverview> getWorkflowsFromDirectory(GitDetails gitInfo) throws IOException, GitAPIException {
        List<WorkflowOverview> workflowsInDir = new ArrayList<>();
        try {
            boolean safeToAccess = gitSemaphore.acquire(gitInfo.getRepoUrl());
            Git repo = null;
            while (repo == null) {
                try {
                    repo = gitService.getRepository(gitInfo, safeToAccess);
                } catch (RefNotFoundException ex) {
                    // Attempt slashes in branch fix
                    GitDetails correctedForSlash = gitService.transferPathToBranch(gitInfo);
                    if (correctedForSlash != null) {
                        gitInfo = correctedForSlash;
                    } else {
                        throw ex;
                    }
                }
            }

            Path localPath = repo.getRepository().getWorkTree().toPath();
            Path pathToDirectory = localPath.resolve(gitInfo.getPath()).normalize().toAbsolutePath();
            Path root = Paths.get("/").toAbsolutePath();
            if (pathToDirectory.equals(root)) {
                pathToDirectory = localPath;
            } else if (!pathToDirectory.startsWith(localPath.normalize().toAbsolutePath())) {
                // Prevent path traversal attacks
                throw new WorkflowNotFoundException();
            }

            File directory = new File(pathToDirectory.toString());
            if (directory.exists() && directory.isDirectory()) {
                for (final File file : directory.listFiles()) {
                    int eIndex = file.getName().lastIndexOf('.') + 1;
                    if (eIndex > 0) {
                        String extension = file.getName().substring(eIndex);
                        if (extension.equals("cwl")) {
                            WorkflowOverview overview = cwlService.getWorkflowOverview(file);
                            if (overview != null) {
                                workflowsInDir.add(overview);
                            }
                        }
                    }
                }
            }
        } finally {
            gitSemaphore.release(gitInfo.getRepoUrl());
        }
        return workflowsInDir;
    }

    /**
     * Get the RO bundle for a Workflow, triggering re-download if it does not exist
     * @param gitDetails The origin details of the workflow
     * @return The file containing the RO bundle
     * @throws ROBundleNotFoundException If the RO bundle was not found
     */
    public File getROBundle(GitDetails gitDetails) throws ROBundleNotFoundException {
        // Get workflow from database
        Workflow workflow = getWorkflow(gitDetails);

        // If workflow does not exist or the bundle doesn't yet
        if (workflow == null || workflow.getRoBundlePath() == null) {
            throw new ROBundleNotFoundException();
        }

        // 404 error with retry if the file on disk does not exist
        File bundleDownload = new File(workflow.getRoBundlePath());
        if (!bundleDownload.exists()) {
            // Clear current RO bundle link and create a new one (async)
            workflow.setRoBundlePath(null);
            workflowRepository.save(workflow);
            generateROBundle(workflow);
            throw new ROBundleNotFoundException();
        }

        return bundleDownload;
    }

    /**
     * Builds a new queued workflow from Git
     * @param gitInfo Git information for the workflow
     * @return A queued workflow model
     * @throws GitAPIException Git errors
     * @throws WorkflowNotFoundException Workflow was not found within the repository
     * @throws IOException Other file handling exceptions
     */
    public QueuedWorkflow createQueuedWorkflow(GitDetails gitInfo)
            throws GitAPIException, WorkflowNotFoundException, IOException {
        QueuedWorkflow queuedWorkflow;

        try {
            boolean safeToAccess = gitSemaphore.acquire(gitInfo.getRepoUrl());
            Git repo = null;
            while (repo == null) {
                try {
                    repo = gitService.getRepository(gitInfo, safeToAccess);
                } catch (RefNotFoundException ex) {
                    // Attempt slashes in branch fix
                    GitDetails correctedForSlash = gitService.transferPathToBranch(gitInfo);
                    if (correctedForSlash != null) {
                        gitInfo = correctedForSlash;
                    } else {
                        throw ex;
                    }
                }
            }
            Path localPath = repo.getRepository().getWorkTree().toPath();
            String latestCommit = gitService.getCurrentCommitID(repo);

            Path workflowFile = localPath.resolve(gitInfo.getPath()).normalize().toAbsolutePath();
            // Prevent path traversal attacks
            if (!workflowFile.startsWith(localPath.normalize().toAbsolutePath())) {
                throw new WorkflowNotFoundException();
            }

            // Check workflow is readable
            if (!Files.isReadable(workflowFile)) {
                throw new WorkflowNotFoundException();
            }

            // Handling of packed workflows
            String packedWorkflowId = gitInfo.getPackedId();
            if (packedWorkflowId == null) {
                if (cwlService.isPacked(workflowFile.toFile())) {
                    List<WorkflowOverview> overviews = cwlService.getWorkflowOverviewsFromPacked(workflowFile.toFile());
                    if (overviews.size() == 0) {
                        throw new IOException("No workflow was found within the packed CWL file");
                    } else {
                        // Dummy queued workflow object to return the list
                        QueuedWorkflow overviewList = new QueuedWorkflow();
                        overviewList.setWorkflowList(overviews);
                        return overviewList;
                    }
                }
            } else {
                // Packed ID specified but was not found
                if (!cwlService.isPacked(workflowFile.toFile())) {
                    throw new WorkflowNotFoundException();
                }
            }

            Workflow basicModel = cwlService.parseWorkflowNative(workflowFile, packedWorkflowId);

            // Set origin details
            basicModel.setRetrievedOn(new Date());
            basicModel.setRetrievedFrom(gitInfo);
            basicModel.setLastCommit(latestCommit);

            // Save the queued workflow to database
            queuedWorkflow = new QueuedWorkflow();
            queuedWorkflow.setTempRepresentation(basicModel);
            queuedWorkflowRepository.save(queuedWorkflow);

            // ASYNC OPERATIONS
            // Parse with cwltool and update model
            try {
                cwlToolRunner.createWorkflowFromQueued(queuedWorkflow);
            } catch (Exception e) {
                logger.error("Could not update workflow with cwltool", e);
            }

        } finally {
            gitSemaphore.release(gitInfo.getRepoUrl());
        }

        // Return this model to be displayed
        return queuedWorkflow;
    }

    /**
     * Retry the running of cwltool to create a new workflow
     * @param queuedWorkflow The workflow to use to update
     */
    public void retryCwltool(QueuedWorkflow queuedWorkflow) {
        queuedWorkflow.setMessage(null);
        queuedWorkflow.setCwltoolStatus(CWLToolStatus.RUNNING);
        queuedWorkflowRepository.save(queuedWorkflow);
        try {
            cwlToolRunner.createWorkflowFromQueued(queuedWorkflow);
        } catch (Exception e) {
            logger.error("Could not update workflow with cwltool", e);
        }
    }

    /**
     * Find a workflow by commit ID and path
     * @param commitID The commit ID of the workflow
     * @param path The path to the workflow within the repository
     * @return A workflow model with the above two parameters
     */
    public Workflow findByCommitAndPath(String commitID, String path) throws WorkflowNotFoundException {
        return findByCommitAndPath(commitID, path, Optional.empty());
    }

    /**
     * Find a workflow by commit ID and path
     *
     * @param commitID
     *            The commit ID of the workflow
     * @param path
     *            The path to the workflow within the repository
     * @param part
     *            The #part within the workflow, or Optional.empty() for no part, or
     *            <code>null</code> for any part
     * @return A workflow model with the above two parameters
     */
    public Workflow findByCommitAndPath(String commitID, String path, Optional<String> part)
            throws WorkflowNotFoundException, MultipleWorkflowsException {
        List<Workflow> matches = workflowRepository.findByCommitAndPath(commitID, path);
        if (matches == null || matches.isEmpty()) {
            throw new WorkflowNotFoundException();
        } else if (part == null) {
            // any part will do - so we'll pick the first one
            return matches.get(0);
        } else {
            // Multiple matches means either added by both branch and ID
            // Or packed workflow
            for (Workflow workflow : matches) {
                if (Objects.equals(part.orElse(null), workflow.getRetrievedFrom().getPackedId())) {
                    // either both are null; or both are non-null and equal
                    return workflow;
                }
            }
            if (part.isPresent()) {
                // Sorry, don't know about your part!
                throw new WorkflowNotFoundException();
            } else {
                // No part requested, let's show some alternate permalinks,
                // in addition to the raw redirect
                throw new MultipleWorkflowsException(matches);
            }
        }
    }

    /**
     * Get a graph in a particular format and return it
     * @param format The format for the graph file
     * @param gitDetails The Git details of the workflow
     * @return A FileSystemResource representing the graph
     * @throws WorkflowNotFoundException Error getting the workflow or format
     * @throws IOException 
     */
    public PathResource getWorkflowGraph(String format, GitDetails gitDetails)
            throws WorkflowNotFoundException, IOException {
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
        Workflow workflow = getWorkflow(gitDetails);
        if (workflow == null) {
            throw new WorkflowNotFoundException();
        }

        // Generate graph and serve the file
        Path out = graphVizService.getGraphPath(workflow.getID() + "." + extension, workflow.getVisualisationDot(), format);
        return new PathResource(out);
    }

    /**
     * Generates the RO bundle for a Workflow and adds it to the model
     * @param workflow The workflow model to create a Research Object for
     */
    private void generateROBundle(Workflow workflow) {
        try {
            ROBundleFactory.createWorkflowRO(workflow);
        } catch (Exception ex) {
            logger.error("Error creating RO Bundle", ex);
        }
    }

    /**
     * Removes a workflow and its research object bundle
     * @param workflow The workflow to be deleted
     */
    private void removeWorkflow(Workflow workflow) {
        // Delete the Research Object Bundle from disk
        if (workflow.getRoBundlePath() != null) {
            File roBundle = new File(workflow.getRoBundlePath());
            if (roBundle.delete()) {
                logger.debug("Deleted Research Object Bundle");
            } else {
                logger.debug("Failed to delete Research Object Bundle");
            }
        }

        // Delete cached graphviz images if they exist
        graphVizService.deleteCache(workflow.getID());

        // Remove the workflow from the database
        workflowRepository.delete(workflow);

        // Remove any queued repositories pointing to the workflow
        queuedWorkflowRepository.deleteByRetrievedFrom(workflow.getRetrievedFrom());
    }

    /**
     * Check for cache expiration based on time and commit sha
     * @param workflow The cached workflow model
     * @return Whether or not there are new commits
     */
    private boolean cacheExpired(Workflow workflow) {
        // If this is a branch and not added by commit ID
        if (!workflow.getRetrievedFrom().getBranch().equals(workflow.getLastCommit())) {
            try {
                // Calculate expiration
                Calendar expireCal = Calendar.getInstance();
                expireCal.setTime(workflow.getRetrievedOn());
                expireCal.add(Calendar.DATE, cacheDays);
                Date expirationDate = expireCal.getTime();

                // Check cached retrievedOn date
                if (expirationDate.before(new Date())) {
                    // Cache expiry time has elapsed
                    // Check current head of the branch with the cached head
                    logger.info("Time has expired for caching, checking commits...");
                    String currentHead;
                    boolean safeToAccess = gitSemaphore.acquire(workflow.getRetrievedFrom().getRepoUrl());
                    try {
                        Git repo = gitService.getRepository(workflow.getRetrievedFrom(), safeToAccess);
                        currentHead = gitService.getCurrentCommitID(repo);
                    } finally {
                        gitSemaphore.release(workflow.getRetrievedFrom().getRepoUrl());
                    }
                    logger.info("Current: " + workflow.getLastCommit() + ", HEAD: " + currentHead);

                    // Reset date in database if there are still no changes
                    boolean expired = !workflow.getLastCommit().equals(currentHead);
                    if (!expired) {
                        workflow.setRetrievedOn(new Date());
                        workflowRepository.save(workflow);
                    }

                    // Return whether the cache has expired
                    return expired;
                }
            } catch(Exception ex){
                // Default to no expiry if there was an API error
                logger.error("API Error when checking for latest commit ID for caching", ex);
            }
        }
        return false;
    }

    public Optional<String> findRawBaseForCommit(String commitId) {
        for (Workflow w : workflowRepository.findByCommit(commitId)) {
            String potentialRaw = w.getRetrievedFrom().getRawUrl(commitId);
            String path = w.getRetrievedFrom().getPath();
            if (potentialRaw.endsWith(path)) {
                return Optional.of(potentialRaw.replace(path, ""));
            }
        }
        // Not found, or not at GitHub/GitLab
        return Optional.empty();

    }

}
