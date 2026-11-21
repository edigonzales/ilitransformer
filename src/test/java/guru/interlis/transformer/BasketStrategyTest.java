package guru.interlis.transformer;

import static org.assertj.core.api.Assertions.*;

import guru.interlis.transformer.engine.BasketRouter;
import guru.interlis.transformer.mapping.plan.OutputBinding;
import guru.interlis.transformer.state.BasketStrategy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class BasketStrategyTest {
    private final BasketRouter router = new BasketRouter();

    @Test
    void preserveReturnsSourceBasketId() {
        String result = router.determineTargetBasket(BasketStrategy.PRESERVE, "source", "src-basket-1", "Topic", null);
        assertThat(result).isEqualTo("src-basket-1");
    }

    @Test
    void generateUuidCreatesValidUuid() {
        String result = router.determineTargetBasket(BasketStrategy.GENERATE_UUID, "source", null, "Topic", null);
        assertThat(result).isNotNull();
        assertThatCode(() -> UUID.fromString(result)).doesNotThrowAnyException();
    }

    @Test
    void generateUuidGroupsByOutputTopicInputAndSourceBasketWithinOneRun() {
        var strategy = BasketStrategy.GENERATE_UUID;
        var output = new OutputBinding("output", null, null, "xtf", null, null, null);
        var otherOutput = new OutputBinding("other", null, null, "xtf", null, null, null);
        String bid = router.determineTargetBasket(strategy, "source", "b1", "Topic", output);

        assertThat(router.determineTargetBasket(strategy, "source", "b1", "Topic", output))
                .isEqualTo(bid);
        assertThat(router.determineTargetBasket(strategy, "source", "b2", "Topic", output))
                .isNotEqualTo(bid);
        assertThat(router.determineTargetBasket(strategy, "other", "b1", "Topic", output))
                .isNotEqualTo(bid);
        assertThat(router.determineTargetBasket(strategy, "source", "b1", "OtherTopic", output))
                .isNotEqualTo(bid);
        assertThat(router.determineTargetBasket(strategy, "source", "b1", "Topic", otherOutput))
                .isNotEqualTo(bid);
        assertThat(new BasketRouter().determineTargetBasket(strategy, "source", "b1", "Topic", output))
                .isNotEqualTo(bid);
    }

    @Test
    void preserveOrGeneratePreservesUuidSourceBasket() {
        String uuid = "550e8400-e29b-41d4-a716-446655440000";
        String result =
                router.determineTargetBasket(BasketStrategy.PRESERVE_OR_GENERATE_UUID, "source", uuid, "Topic", null);
        assertThat(result).isEqualTo(uuid);
    }

    @Test
    void preserveOrGenerateGeneratesForNonUuidSourceBasket() {
        String result = router.determineTargetBasket(
                BasketStrategy.PRESERVE_OR_GENERATE_UUID, "source", "not-a-uuid", "Topic", null);
        assertThat(result).isNotEqualTo("not-a-uuid");
        assertThat(router.determineTargetBasket(
                        BasketStrategy.PRESERVE_OR_GENERATE_UUID, "source", "not-a-uuid", "Topic", null))
                .isEqualTo(result);
        assertThatCode(() -> UUID.fromString(result)).doesNotThrowAnyException();
    }

    @Test
    void byTopicCreatesTopicBasedBasketId() {
        String result = router.determineTargetBasket(BasketStrategy.BY_TOPIC, "source", null, "MyModel.MyTopic", null);
        assertThatCode(() -> UUID.fromString(result)).doesNotThrowAnyException();
    }

    @Test
    void byTopicSameTopicProducesSameBasketId() {
        String r1 = router.determineTargetBasket(BasketStrategy.BY_TOPIC, "source", null, "A.B", null);
        String r2 = router.determineTargetBasket(BasketStrategy.BY_TOPIC, "source", null, "A.B", null);
        assertThat(r1).isEqualTo(r2);
    }

    @Test
    void byTopicDifferentTopicProducesDifferentBasketId() {
        String r1 = router.determineTargetBasket(BasketStrategy.BY_TOPIC, "source", null, "A.B1", null);
        String r2 = router.determineTargetBasket(BasketStrategy.BY_TOPIC, "source", null, "A.B2", null);
        assertThat(r1).isNotEqualTo(r2);
    }

    @Test
    void expressionReturnsNull() {
        String result = router.determineTargetBasket(BasketStrategy.EXPRESSION, "source", "src", "Topic", null);
        assertThat(result).isNull();
    }
}
