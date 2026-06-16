package guru.interlis.transformer.model;

import ch.interlis.ili2c.metamodel.AssociationDef;
import ch.interlis.ili2c.metamodel.RoleDef;

public record ResolvedRole(
        RoleDef role, AssociationDef association, String destinationClass, long minCardinality, long maxCardinality) {}
