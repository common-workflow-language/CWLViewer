package org.commonwl.view.workflow;

import org.commonwl.view.git.GitDetails;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;

import java.util.Date;
import java.util.List;

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
    @Query("{tempRepresentation.retrievedFrom: ?0}")
    void deleteByRetrievedFrom(GitDetails retrievedFrom);

    /**
     * Deletes all queued workflows with date retrieved on older or equal to the Date argument passed.
     * @param retrievedOn Date of when the queued workflow was retrieved
     * @return The number of queued workflows deleted
     */
    Long deleteByTempRepresentation_RetrievedOnLessThanEqual(Date retrievedOn);


    /**
     * Finds and returns all queued workflows with date retrieved on older or equal to the Date argument passed.
     * @param retrievedOn Details of where the queued workflow is from
     * @return A list of queued workflows
     */
    List<QueuedWorkflow> findByTempRepresentation_RetrievedOnLessThanEqual(Date retrievedOn);


}
