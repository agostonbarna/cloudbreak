package com.sequenceiq.cloudbreak.cloud.gcp.network;

import static com.sequenceiq.cloudbreak.common.type.ResourceType.GCP_FIREWALL_IN;
import static java.util.Arrays.asList;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.model.Firewall;
import com.google.api.services.compute.model.Operation;
import com.sequenceiq.cloudbreak.cloud.context.AuthenticatedContext;
import com.sequenceiq.cloudbreak.cloud.gcp.GcpResourceException;
import com.sequenceiq.cloudbreak.cloud.gcp.context.GcpContext;
import com.sequenceiq.cloudbreak.cloud.gcp.util.GcpStackUtil;
import com.sequenceiq.cloudbreak.cloud.model.CloudResource;
import com.sequenceiq.cloudbreak.cloud.model.CloudResourceStatus;
import com.sequenceiq.cloudbreak.cloud.model.Network;
import com.sequenceiq.cloudbreak.cloud.model.Security;
import com.sequenceiq.cloudbreak.cloud.model.SecurityRule;
import com.sequenceiq.cloudbreak.common.type.ResourceType;

@Service
public class GcpFirewallInResourceBuilder extends AbstractGcpNetworkBuilder {

    private static final int ORDER = 3;

    @Override
    public CloudResource create(GcpContext context, AuthenticatedContext auth, Network network) {
        String resourceName = getResourceNameService().resourceName(resourceType(), context.getName());
        return createNamedResource(resourceType(), resourceName);
    }

    @Override
    public CloudResource build(GcpContext context, AuthenticatedContext auth, Network network, Security security, CloudResource buildableResource)
            throws Exception {
        String projectId = context.getProjectId();

        List<String> sourceRanges = getSourceRanges(security);

        Firewall firewall = new Firewall();
        firewall.setSourceRanges(sourceRanges);

        List<Firewall.Allowed> allowedRules = new ArrayList<>();
        allowedRules.add(new Firewall.Allowed().setIPProtocol("icmp"));

        allowedRules.addAll(createRule(security));

        firewall.setTargetTags(Collections.singletonList(GcpStackUtil.getClusterTag(auth.getCloudContext())));
        firewall.setAllowed(allowedRules);
        firewall.setName(buildableResource.getName());
        firewall.setNetwork(String.format("https://www.googleapis.com/compute/v1/projects/%s/global/networks/%s",
                projectId, context.getParameter(GcpNetworkResourceBuilder.NETWORK_NAME, String.class)));

        Compute.Firewalls.Insert firewallInsert = context.getCompute().firewalls().insert(projectId, firewall);
        try {
            Operation operation = firewallInsert.execute();
            if (operation.getHttpErrorStatusCode() != null) {
                throw new GcpResourceException(operation.getHttpErrorMessage(), resourceType(), buildableResource.getName());
            }
            return createOperationAwareCloudResource(buildableResource, operation);
        } catch (GoogleJsonResponseException e) {
            throw new GcpResourceException(checkException(e), resourceType(), buildableResource.getName());
        }
    }

    @Override
    public CloudResourceStatus update(GcpContext ctx, AuthenticatedContext auth, Network network, Security security, CloudResource resource) {
        String projectId = ctx.getProjectId();
        Compute compute = ctx.getCompute();
        String resourceName = resource.getName();
        try {
            Firewall fireWall = compute.firewalls().get(projectId, resourceName).execute();
            List<String> sourceRanges = getSourceRanges(security);
            fireWall.setSourceRanges(sourceRanges);
            Operation operation = compute.firewalls().update(projectId, resourceName, fireWall).execute();
            CloudResource cloudResource = createOperationAwareCloudResource(resource, operation);
            return checkResources(ctx, auth, Collections.singletonList(cloudResource)).get(0);
        } catch (IOException e) {
            throw new GcpResourceException("Failed to update resource!", GCP_FIREWALL_IN, resourceName, e);
        }
    }

    @Override
    public CloudResource delete(GcpContext context, AuthenticatedContext auth, CloudResource resource, Network network) throws Exception {
        try {
            Operation operation = context.getCompute().firewalls().delete(context.getProjectId(), resource.getName()).execute();
            return createOperationAwareCloudResource(resource, operation);
        } catch (GoogleJsonResponseException e) {
            exceptionHandler(e, resource.getName(), resourceType());
            return null;
        }
    }

    @Override
    public ResourceType resourceType() {
        return ResourceType.GCP_FIREWALL_IN;
    }

    @Override
    public int order() {
        return ORDER;
    }

    private List<String> getSourceRanges(Security security) {
        List<SecurityRule> rules = security.getRules();
        List<String> sourceRanges = new ArrayList<>(rules.size());
        for (SecurityRule securityRule : rules) {
            sourceRanges.add(securityRule.getCidr());
        }
        return sourceRanges;
    }

    private List<Firewall.Allowed> createRule(Security security) {
        List<Firewall.Allowed> rules = new LinkedList<>();
        List<SecurityRule> securityRules = security.getRules();
        for (SecurityRule securityRule : securityRules) {
            Firewall.Allowed rule = new Firewall.Allowed();
            rule.setIPProtocol(securityRule.getProtocol());
            rule.setPorts(asList(securityRule.getPorts()));
            rules.add(rule);
        }
        return rules;
    }
}
