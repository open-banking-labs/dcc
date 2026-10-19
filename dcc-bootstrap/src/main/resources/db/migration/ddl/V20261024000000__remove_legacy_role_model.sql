-- ADR 0005: the legacy DCC_ADMIN / DCC_USER role model is removed without a compatibility
-- layer. Retire any existing mappings so switching to dcc.security.resolver-mode=database
-- cannot resurrect those permissions. New rows can no longer be written: the names are absent
-- from the shipped role bundles and the platform grant path rejects unknown roles.

update role_permission
   set deleted_at = now(),
       deleted_by = 'system:migration',
       deletion_reason = 'legacy role model removed (ADR 0005)'
 where role in ('DCC_ADMIN', 'DCC_USER')
   and deleted_at is null;

update role_assignment
   set deleted_at = now(),
       deleted_by = 'system:migration',
       deletion_reason = 'legacy role model removed (ADR 0005)'
 where role in ('DCC_ADMIN', 'DCC_USER')
   and deleted_at is null;
