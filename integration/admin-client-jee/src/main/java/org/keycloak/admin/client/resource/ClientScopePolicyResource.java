package org.keycloak.admin.client.resource;

import java.util.stream.Stream;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.jboss.resteasy.annotations.cache.NoCache;
import org.keycloak.representations.idm.ClientScopePolicyRepresentation;

public interface ClientScopePolicyResource {

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response createClientScopePolicy(ClientScopePolicyRepresentation rep);

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Stream<ClientScopePolicyRepresentation> getClientScopePolicies();

    @GET
    @Path("{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public ClientScopePolicyRepresentation getClientScopePolicyById(@PathParam("id") String id);

    @PUT
    @Path("{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    public void update(@PathParam("id") String id, ClientScopePolicyRepresentation rep);

    @DELETE
    @Path("{id}")
    public void delete(@PathParam("id") String id);
}
