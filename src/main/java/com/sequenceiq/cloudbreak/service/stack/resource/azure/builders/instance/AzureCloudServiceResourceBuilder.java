package com.sequenceiq.cloudbreak.service.stack.resource.azure.builders.instance;

import static com.sequenceiq.cloudbreak.service.stack.connector.azure.AzureStackUtil.ERROR;
import static com.sequenceiq.cloudbreak.service.stack.connector.azure.AzureStackUtil.NAME;
import static com.sequenceiq.cloudbreak.service.stack.connector.azure.AzureStackUtil.SERVICENAME;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Optional;
import com.sequenceiq.cloud.azure.client.AzureClient;
import com.sequenceiq.cloudbreak.domain.AzureCredential;
import com.sequenceiq.cloudbreak.domain.AzureTemplate;
import com.sequenceiq.cloudbreak.domain.Resource;
import com.sequenceiq.cloudbreak.domain.ResourceType;
import com.sequenceiq.cloudbreak.domain.Stack;
import com.sequenceiq.cloudbreak.repository.StackRepository;
import com.sequenceiq.cloudbreak.service.PollingService;
import com.sequenceiq.cloudbreak.service.stack.connector.azure.AzureCloudServiceRemoveCheckerStatus;
import com.sequenceiq.cloudbreak.service.stack.connector.azure.AzureCloudServiceRemoveReadyPollerObject;
import com.sequenceiq.cloudbreak.service.stack.connector.azure.AzureDiskRemoveCheckerStatus;
import com.sequenceiq.cloudbreak.service.stack.connector.azure.AzureDiskRemoveReadyPollerObject;
import com.sequenceiq.cloudbreak.service.stack.resource.azure.AzureSimpleInstanceResourceBuilder;
import com.sequenceiq.cloudbreak.service.stack.resource.azure.model.AzureDeleteContextObject;
import com.sequenceiq.cloudbreak.service.stack.resource.azure.model.AzureDescribeContextObject;
import com.sequenceiq.cloudbreak.service.stack.resource.azure.model.AzureProvisionContextObject;

import groovyx.net.http.HttpResponseDecorator;

@Component
@Order(1)
public class AzureCloudServiceResourceBuilder extends AzureSimpleInstanceResourceBuilder {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private StackRepository stackRepository;

    @Autowired
    private AzureDiskRemoveCheckerStatus azureDiskRemoveCheckerStatus;
    @Autowired
    private PollingService<AzureDiskRemoveReadyPollerObject> azureDiskRemoveReadyPollerObjectPollingService;
    @Autowired
    private AzureCloudServiceRemoveCheckerStatus azureCloudServiceRemoveCheckerStatus;
    @Autowired
    private PollingService<AzureCloudServiceRemoveReadyPollerObject> azureCloudServiceRemoveReadyPollerObjectPollingService;



    @Override
    public List<Resource> create(AzureProvisionContextObject po, int index, List<Resource> resources) throws Exception {
        Stack stack = stackRepository.findById(po.getStackId());
        AzureTemplate azureTemplate = (AzureTemplate) stack.getTemplate();
        AzureCredential azureCredential = (AzureCredential) stack.getCredential();
        String vmName = getVmName(po.filterResourcesByType(ResourceType.AZURE_NETWORK).get(0).getResourceName(), index)
                + String.valueOf(new Date().getTime());
        Map<String, String> props = new HashMap<>();
        props.put(NAME, vmName);
        props.put(DESCRIPTION, azureTemplate.getDescription());
        props.put(AFFINITYGROUP, po.getCommonName());
        AzureClient azureClient = po.getNewAzureClient(azureCredential);
        HttpResponseDecorator cloudServiceResponse = (HttpResponseDecorator) azureClient.createCloudService(props);
        String requestId = (String) azureClient.getRequestId(cloudServiceResponse);
        azureClient.waitUntilComplete(requestId);
        return Arrays.asList(new Resource(ResourceType.AZURE_CLOUD_SERVICE, vmName, stack));
    }

    @Override
    public Boolean delete(Resource resource, AzureDeleteContextObject aDCO) throws Exception {
        Stack stack = stackRepository.findById(aDCO.getStackId());
        AzureCredential credential = (AzureCredential) stack.getCredential();
        AzureCloudServiceRemoveReadyPollerObject azureCloudServiceRemoveReadyPollerObject =
                new AzureCloudServiceRemoveReadyPollerObject(aDCO.getCommonName(), resource.getResourceName(),
                        aDCO.getStackId(), aDCO.getNewAzureClient(credential));
        azureCloudServiceRemoveReadyPollerObjectPollingService
                .pollWithTimeout(azureCloudServiceRemoveCheckerStatus, azureCloudServiceRemoveReadyPollerObject, POLLING_INTERVAL, MAX_POLLING_ATTEMPTS);

        AzureClient azureClient = aDCO.getNewAzureClient(credential);
        JsonNode actualObj = MAPPER.readValue((String) azureClient.getDisks(), JsonNode.class);
        List<String> disks = (List<String>) actualObj.get("Disks").findValues("Disk").get(0).findValuesAsText("Name");
        for (String jsonNode : disks) {
            if (jsonNode.startsWith(String.format("%s-%s-0", resource.getResourceName(), resource.getResourceName()))) {
                AzureDiskRemoveReadyPollerObject azureDiskRemoveReadyPollerObject = new AzureDiskRemoveReadyPollerObject(aDCO.getCommonName(), jsonNode,
                        aDCO.getStackId(), aDCO.getNewAzureClient(credential));
                azureDiskRemoveReadyPollerObjectPollingService
                        .pollWithTimeout(azureDiskRemoveCheckerStatus, azureDiskRemoveReadyPollerObject, POLLING_INTERVAL, MAX_POLLING_ATTEMPTS);
            }
        }
        return true;
    }

    @Override
    public Optional<String> describe(Resource resource, AzureDescribeContextObject aDCO) throws Exception {
        Stack stack = stackRepository.findById(aDCO.getStackId());
        AzureCredential credential = (AzureCredential) stack.getCredential();
        Map<String, String> props = new HashMap<>();
        props.put(SERVICENAME, resource.getResourceName());
        props.put(NAME, resource.getResourceName());
        try {
            AzureClient azureClient = aDCO.getNewAzureClient(credential);
            Object virtualMachine = azureClient.getVirtualMachine(props);
            return Optional.fromNullable(virtualMachine.toString());
        } catch (Exception ex) {
            return Optional.fromNullable(String.format("{\"Deployment\": {%s}}", ERROR));
        }
    }

    @Override
    public ResourceType resourceType() {
        return ResourceType.AZURE_CLOUD_SERVICE;
    }
}
