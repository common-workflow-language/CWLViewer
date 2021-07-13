package org.commonwl.view.workflow;

import org.apache.jena.base.Sys;
import org.commonwl.view.git.GitDetails;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.Assert.*;

@DataMongoTest
@RunWith(SpringRunner.class)
public class QueuedWorkflowRepositoryTest {

    @Autowired
    QueuedWorkflowRepository repository;

    @Test
    public void deleteQueuedWorkflowByRetrievedFromTest() {

        assertNotNull(repository);

        // create stub queued workflow
        GitDetails gitDetails = new GitDetails("test_repo_url", "test_branch", "test_path");
        gitDetails.setPackedId("test_packedId");

        Workflow workflow = new Workflow();
        workflow.setRetrievedFrom(gitDetails);

        QueuedWorkflow queuedWorkflow = new QueuedWorkflow();
        queuedWorkflow.setTempRepresentation(workflow);

        // save queued workflow
        repository.save(queuedWorkflow);

        // retrieve saved queued workflow by workflow git details
        QueuedWorkflow retrievedQueuedWorkflowAfterSave = repository
                .findByRetrievedFrom(queuedWorkflow.getTempRepresentation().getRetrievedFrom());
        assertNotNull(retrievedQueuedWorkflowAfterSave);


        // delete saved queued workflow by workflow git details
        repository.deleteByTempRepresentation_RetrievedFrom(queuedWorkflow.getTempRepresentation().getRetrievedFrom());

        // retrieve deleted queued workflow by workflow git details
        QueuedWorkflow retrievedQueuedWorkflowAfterDelete = repository
                .findByRetrievedFrom(queuedWorkflow.getTempRepresentation().getRetrievedFrom());
        assertNull(retrievedQueuedWorkflowAfterDelete);

    }

}
