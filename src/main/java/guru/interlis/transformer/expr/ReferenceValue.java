package guru.interlis.transformer.expr;

import guru.interlis.transformer.io.FormatIdResolver;
import guru.interlis.transformer.mapping.plan.InputBinding;
import guru.interlis.transformer.model.TypeSystemFacade;

import ch.interlis.ili2c.metamodel.Model;

public record ReferenceValue(
        String targetClass, String oid, String basketId, String sourceAlias, String sourceAttribute) implements Value {
    public boolean isTransferScoped(InputBinding input, TypeSystemFacade.ReferenceInfo role) {
        return basketId == null
                && role != null
                && role.external()
                && input != null
                && FormatIdResolver.isXtf(input.format())
                && input.transferDescription() != null
                && Model.ILI2_4.equals(
                        input.transferDescription().getLastModel().getIliVersion());
    }

    @Override
    public String asText() {
        return oid;
    }

    @Override
    public Object toNative() {
        return oid;
    }
}
