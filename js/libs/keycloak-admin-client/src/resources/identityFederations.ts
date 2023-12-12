import type { KeycloakAdminClient } from "../client.js";
import type IdentityFederationRepresentation from "../defs/identityFederationRepresentation.js";
import Resource from "./resource.js";

export interface PaginatedQuery {
  first?: number;
  max?: number;
}

export interface IdentityFederationsQuery extends PaginatedQuery {
  search?: string;
}

export class IdentityFederations extends Resource<{ realm?: string }> {
  /**
   * Identity Federation
   * https://www.keycloak.org/docs-api/11.0/rest-api/#_identity_providers_resource
   */

  public find = this.makeRequest<any>({
    method: "GET",
    path: "/instances",
  });

  public findOne = this.makeRequest<
    { internalId: string },
    IdentityFederationRepresentation | undefined
  >({
    method: "GET",
    path: "/instances/{internalId}",
    urlParamKeys: ["internalId"],
    catchNotFound: true,
  });

  public update = this.makeUpdateRequest<
    { internalId: string },
    IdentityFederationRepresentation,
    void
  >({
    method: "PUT",
    path: "/instances/{internalId}",
    urlParamKeys: ["internalId"],
  });

  public create = this.makeRequest<
    IdentityFederationRepresentation,
    { id: string }
  >({
    method: "POST",
    path: "/instances",
    returnResourceIdInLocationHeader: { field: "id" },
  });

  public del = this.makeRequest<{ id: string }, void>({
    method: "DELETE",
    path: "/instances/{id}",
    urlParamKeys: ["id"],
  });

  constructor(client: KeycloakAdminClient) {
    super(client, {
      path: "/admin/realms/{realm}/saml-federations",
      getUrlParams: () => ({
        realm: client.realmName,
      }),
      getBaseUrl: () => client.baseUrl,
    });
  }
}
