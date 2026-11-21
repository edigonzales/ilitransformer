# State Store

`TransformationEngine.runTyped(TransformPlan, …)` uses one `StateStore` and one
`ReferenceIndex` for the run. `InMemoryStateStore` keeps source records, target
objects keyed by output, class and OID, and a single list of `DeferredReference`
records. `OidGenerationService` generates target OIDs.

`ReferenceIndex` maps a `SourceObjectKey` (input ID, basket ID, class and OID) to
the generated `TargetReference` values. A target record contains its output ID,
class, OID, actual basket ID and producing rule. Target creation uses the same
basket ID for routing and registration. The index registers the object mapped by
a rule. Additional objects from a create block are emitted separately.

## Reference resolution

- A local reference uses the basket of the source object identified by its
  expression. An explicit reference BID selects that basket. A configured
  target rule constrains the producing rule and can identify a different input.
- An EXTERNAL XTF 2.4 reference carries a transfer ID. The lookup is scoped to
  the applicable input and source type, using the configured target rule when
  present. The basket comes from the matched object.
- A singleton expression such as `#first` requires exactly one target produced
  by the configured rule. Multiple candidates produce an ambiguity error.
- `ReferenceResolutionService` checks target type compatibility and role
  cardinality. An unresolved required mapping reference is an error under every failure
  policy. An optional reference with a supplied but unresolved value produces
  a warning.

ITF writes a single block per topic. There is no output BID, so the rules for
generating or preserving BIDs apply to XTF and do not split ITF topics.
A scalar OID expression in a join requires a unique source basket and input.
Use a bound alias or a modelled reference when the joined records have
different source contexts.

Local references carry the generated target OID. XTF 2.4 uses a unique target
TID for an EXTERNAL reference between baskets in the same output. XTF 2.3
requires REF and BID for this case. IOX 1.24.4 emits EXTREF instead of REF when
a BID is set, so the engine rejects cross-basket 2.3 output before publication.

See the transfer rules in the [INTERLIS 2.3 reference manual, chapter 3.3](https://www.interlis.ch/download/interlis2/ili2-refman_2006-04-13_e.pdf)
and the [INTERLIS 2.4 reference manual, chapter 4.3](https://geostandards-ch.github.io/doc_refhb24/).
