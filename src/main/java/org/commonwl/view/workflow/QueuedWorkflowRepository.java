package org.commonwl.view.workflow;

import org.commonwl.view.git.GitDetails;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;

/**
 * Stores workflows in the queue waiting for cwltool
 */
public interface QueuedWorkflowRepository extends PagingAndSortingRepository<QueuedWorkflow, String> {

    /**
     * Finds a queued workflow based on where it was retrieved from
     * @param retrievedFrom Details of where the queued workflow is from
     * @return The queued workflow
     */
    @Query("{tempRepresentation.retrievedFrom: ?0}")
    QueuedWorkflow findByRetrievedFrom(GitDetails retrievedFrom);

    /**
     * Deletes a queued workflow based on where it was retrieved from
     * @param retrievedFrom Details of where the queued workflow is from
     */
    void deleteByTempRepresentation_RetrievedFrom(GitDetails retrievedFrom);

}
