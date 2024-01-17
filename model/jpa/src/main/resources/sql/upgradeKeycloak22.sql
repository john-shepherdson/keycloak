WITH Base64Update AS (
SELECT
    ipc.value as old_alias,
    ip.provider_alias AS new_alias
  FROM
    identity_provider AS ip
    JOIN identity_provider_config AS ipc ON ip.internal_id = ipc.identity_provider_id
    JOIN identity_provider_mapper AS ipm ON ipm.idp_alias = ipc.value    
    join federation_idps AS fi ON ip.internal_id = fi.identityprovider_id
  WHERE
    ipc."name" ='oldAlias'
)
UPDATE
  identity_provider_mapper
SET
  idp_alias = bu.new_alias
FROM
  Base64Update AS bu
WHERE
  idp_alias = bu.old_alias;
 
 delete from identity_provider_config i where i."name" ='oldAlias';
 