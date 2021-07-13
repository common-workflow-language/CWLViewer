package org.commonwl.view;


import org.commonwl.view.workflow.QueuedWorkflowRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Calendar;
import java.util.Date;

/**
 * Scheduler class for recurrent processes.
 */
@Component
public class Scheduler {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final QueuedWorkflowRepository queuedWorkflowRepository;

    @Value("${queuedWorkflowAgeLimitHours}")
    private Integer QUEUED_WORKFLOW_AGE_LIMIT_HOURS;

    @Autowired
    public Scheduler(QueuedWorkflowRepository queuedWorkflowRepository) {
        this.queuedWorkflowRepository = queuedWorkflowRepository;
    }


    /**
     * A Scheduled function to delete old queued workflow entries
     * from the queue. Age is determined by QUEUED_WORKFLOW_AGE_LIMIT_HOURS
     */
    @Scheduled(cron = "${cron.deleteOldQueuedWorkflows}")
    public void removeOldQueuedWorkflowEntries() {
        Calendar calendar = Calendar.getInstance();
        Date now = new Date();
        calendar.setTime(now);

        if (QUEUED_WORKFLOW_AGE_LIMIT_HOURS == null) {
            QUEUED_WORKFLOW_AGE_LIMIT_HOURS = 24;
        }

        // calculate time QUEUED_WORKFLOW_AGE_LIMIT_HOURS before now
        calendar.add(Calendar.HOUR, -QUEUED_WORKFLOW_AGE_LIMIT_HOURS);
        Date removeTime = calendar.getTime();

        logger.info("The time is " + now);
        logger.info("Delete time interval is : OLDER THAN " + QUEUED_WORKFLOW_AGE_LIMIT_HOURS + " HOURS");
        logger.info("Deleting queued workflows older than or equal to " + removeTime);

        logger.info(queuedWorkflowRepository.deleteByTempRepresentation_RetrievedOnLessThanEqual(removeTime)
                + " Old queued workflows removed");
    }
}
