import type { AppRouteObject } from "../routes";
import { AddPolicyPageRoute } from "./routes/AddPolicy";
import { ClientScopeRoute } from "./routes/ClientScope";
import { ClientScopesRoute } from "./routes/ClientScopes";
import { EditPolicyPageRoute } from "./routes/EditPolicy";
import { MapperRoute } from "./routes/Mapper";
import { NewClientScopeRoute } from "./routes/NewClientScope";

const routes: AppRouteObject[] = [
  NewClientScopeRoute,
  MapperRoute,
  ClientScopeRoute,
  ClientScopesRoute,
  AddPolicyPageRoute,
  EditPolicyPageRoute,
];

export default routes;
