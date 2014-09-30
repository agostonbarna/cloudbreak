package com.sequenceiq.cloudbreak.service.cluster;

import java.util.Set;

import com.sequenceiq.cloudbreak.controller.json.HostGroupAdjustmentJson;
import com.sequenceiq.cloudbreak.domain.Cluster;
import com.sequenceiq.cloudbreak.domain.StatusRequest;
import com.sequenceiq.cloudbreak.domain.User;

public interface ClusterService {
    void createCluster(User user, Long stackId, Cluster clusterRequest);

    Cluster retrieveCluster(User user, Long stackId);

    String getClusterJson(String ambariIp, Long stackId);

    void updateHosts(Long stackId, Set<HostGroupAdjustmentJson> hostGroupAdjustments);

    void updateStatus(Long stackId, StatusRequest statusRequest);
}