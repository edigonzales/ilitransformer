package guru.interlis.transformer.engine;

import guru.interlis.transformer.mapping.plan.SourcePlan;
import guru.interlis.transformer.state.SourceRecord;

public record BoundSourceObject(SourcePlan sourcePlan, SourceRecord sourceRecord) {}
