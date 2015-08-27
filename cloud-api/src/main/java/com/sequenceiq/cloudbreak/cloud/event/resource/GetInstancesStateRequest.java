package com.sequenceiq.cloudbreak.cloud.event.resource;

import java.util.List;

import com.sequenceiq.cloudbreak.cloud.event.CloudPlatformRequest;
import com.sequenceiq.cloudbreak.cloud.event.context.CloudContext;
import com.sequenceiq.cloudbreak.cloud.model.CloudCredential;
import com.sequenceiq.cloudbreak.cloud.model.CloudInstance;

public class GetInstancesStateRequest<T> extends CloudPlatformRequest<T> {

    private List<CloudInstance> instances;

    public GetInstancesStateRequest(CloudContext cloudContext, CloudCredential cloudCredential, List<CloudInstance> instances) {
        super(cloudContext, cloudCredential);
        this.instances = instances;
    }

    public List<CloudInstance> getInstances() {
        return instances;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("GetInstancesStateRequest{");
        sb.append(", instances=").append(instances);
        sb.append('}');
        return sb.toString();
    }
}