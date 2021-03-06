package com.sequenceiq.cloudbreak.core.flow2.cluster.reset;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.sequenceiq.cloudbreak.api.model.Status;
import com.sequenceiq.cloudbreak.core.flow2.stack.FlowMessageService;
import com.sequenceiq.cloudbreak.core.flow2.stack.Msg;
import com.sequenceiq.cloudbreak.domain.Cluster;
import com.sequenceiq.cloudbreak.domain.Stack;
import com.sequenceiq.cloudbreak.logger.MDCBuilder;
import com.sequenceiq.cloudbreak.repository.StackUpdater;
import com.sequenceiq.cloudbreak.service.cluster.ClusterService;
import com.sequenceiq.cloudbreak.service.cluster.flow.EmailSenderService;

@Service
public class ClusterResetService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClusterResetService.class);
    @Inject
    private FlowMessageService flowMessageService;
    @Inject
    private ClusterService clusterService;
    @Inject
    private StackUpdater stackUpdater;
    @Inject
    private EmailSenderService emailSenderService;

    public void resetCluster(Stack stack, Cluster cluster) {
        MDCBuilder.buildMdcContext(cluster);
        flowMessageService.fireEventAndLog(stack.getId(), Msg.AMBARI_CLUSTER_RESET, Status.UPDATE_IN_PROGRESS.name());
    }

    public void handleResetClusterFailure(Stack stack, String errorReason) {
        Cluster cluster = clusterService.retrieveClusterByStackId(stack.getId());
        MDCBuilder.buildMdcContext(cluster);
        clusterService.updateClusterStatusByStackId(stack.getId(), Status.CREATE_FAILED, errorReason);
        stackUpdater.updateStackStatus(stack.getId(), Status.AVAILABLE);
        flowMessageService.fireEventAndLog(stack.getId(), Msg.AMBARI_CLUSTER_CREATE_FAILED, Status.CREATE_FAILED.name(), errorReason);
        if (cluster.getEmailNeeded()) {
            emailSenderService.sendProvisioningFailureEmail(cluster.getOwner(), cluster.getName());
            flowMessageService.fireEventAndLog(stack.getId(), Msg.AMBARI_CLUSTER_NOTIFICATION_EMAIL, Status.AVAILABLE.name());
        }
    }
}
