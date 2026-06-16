package guru.interlis.transformer.model;

import guru.interlis.transformer.mapping.plan.RefPlan;

import ch.interlis.ili2c.metamodel.AssociationDef;
import ch.interlis.ili2c.metamodel.RoleDef;

public final class RoleResolver {

    private final TypeSystemFacade targetTypeSystem;

    public RoleResolver(TypeSystemFacade targetTypeSystem) {
        this.targetTypeSystem = targetTypeSystem;
    }

    public record RoleCardinality(long min, long max) {
        public boolean isRequired() {
            return min > 0;
        }

        public boolean isUnbounded() {
            return max == Long.MAX_VALUE;
        }
    }

    public ResolvedRole requireRole(
            TypeSystemFacade targetTypeSystem, String ownerClass, String roleName, String associationName) {
        if (ownerClass == null || roleName == null) return null;
        TypeSystemFacade.ReferenceInfo ref = targetTypeSystem.resolveReference(ownerClass, roleName);
        if (ref == null) return null;

        AssociationDef association = ref.association();
        if (associationName != null) {
            if (association == null || !associationName.equals(association.getName())) {
                return null;
            }
        }

        RoleDef role = ref.role();
        return new ResolvedRole(role, association, ref.targetClass(), ref.minCardinality(), ref.maxCardinality());
    }

    public String resolveExpectedTargetClass(RefPlan refPlan, String targetClassPath) {
        if (refPlan == null || targetClassPath == null) return null;
        String roleName = refPlan.targetRoleName();
        if (roleName == null) return null;
        return targetTypeSystem.getRoleTargetClass(targetClassPath, roleName);
    }

    public RoleCardinality getTargetRoleCardinality(RefPlan refPlan, String targetClassPath) {
        if (refPlan == null || targetClassPath == null) return new RoleCardinality(0, Long.MAX_VALUE);
        String roleName = refPlan.targetRoleName();
        if (roleName == null) return new RoleCardinality(0, Long.MAX_VALUE);
        long min = targetTypeSystem.getRoleCardinalityMin(targetClassPath, roleName);
        long max = targetTypeSystem.getRoleCardinalityMax(targetClassPath, roleName);
        return new RoleCardinality(min, max);
    }

    public String getAssociationName(RefPlan refPlan, String targetClassPath) {
        if (refPlan == null || targetClassPath == null) return null;
        String roleName = refPlan.targetRoleName();
        if (roleName == null) return null;
        return targetTypeSystem.getRoleAssociation(targetClassPath, roleName);
    }

    public boolean roleExists(String classPath, String roleName) {
        return targetTypeSystem.roleExists(classPath, roleName);
    }
}
