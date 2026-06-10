package guru.interlis.transformer;

import guru.interlis.transformer.engine.BasketRouter;
import guru.interlis.transformer.state.BasketStrategy;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class BasketStrategyTest {

    @Test
    void preserveReturnsSourceBasketId() {
        String result = BasketRouter.determineTargetBasket(
                BasketStrategy.PRESERVE, "src-basket-1", "Topic", "Class");
        assertThat(result).isEqualTo("src-basket-1");
    }

    @Test
    void generateUuidCreatesValidUuid() {
        String result = BasketRouter.determineTargetBasket(
                BasketStrategy.GENERATE_UUID, null, "Topic", "Class");
        assertThat(result).isNotNull();
        assertThatCode(() -> UUID.fromString(result)).doesNotThrowAnyException();
    }

    @Test
    void generateUuidCreatesUniqueIds() {
        String r1 = BasketRouter.determineTargetBasket(
                BasketStrategy.GENERATE_UUID, null, "Topic", "Class");
        String r2 = BasketRouter.determineTargetBasket(
                BasketStrategy.GENERATE_UUID, null, "Topic", "Class");
        assertThat(r1).isNotEqualTo(r2);
    }

    @Test
    void preserveOrGeneratePreservesUuidSourceBasket() {
        String uuid = "550e8400-e29b-41d4-a716-446655440000";
        String result = BasketRouter.determineTargetBasket(
                BasketStrategy.PRESERVE_OR_GENERATE_UUID, uuid, "Topic", "Class");
        assertThat(result).isEqualTo(uuid);
    }

    @Test
    void preserveOrGenerateGeneratesForNonUuidSourceBasket() {
        String result = BasketRouter.determineTargetBasket(
                BasketStrategy.PRESERVE_OR_GENERATE_UUID, "not-a-uuid", "Topic", "Class");
        assertThat(result).isNotEqualTo("not-a-uuid");
        assertThatCode(() -> UUID.fromString(result)).doesNotThrowAnyException();
    }

    @Test
    void byTopicCreatesTopicBasedBasketId() {
        String result = BasketRouter.determineTargetBasket(
                BasketStrategy.BY_TOPIC, null, "MyModel.MyTopic", "Class");
        assertThatCode(() -> UUID.fromString(result)).doesNotThrowAnyException();
    }

    @Test
    void byTopicSameTopicProducesSameBasketId() {
        String r1 = BasketRouter.determineTargetBasket(
                BasketStrategy.BY_TOPIC, null, "A.B", "C1");
        String r2 = BasketRouter.determineTargetBasket(
                BasketStrategy.BY_TOPIC, null, "A.B", "C2");
        assertThat(r1).isEqualTo(r2);
    }

    @Test
    void byTopicDifferentTopicProducesDifferentBasketId() {
        String r1 = BasketRouter.determineTargetBasket(
                BasketStrategy.BY_TOPIC, null, "A.B1", "C");
        String r2 = BasketRouter.determineTargetBasket(
                BasketStrategy.BY_TOPIC, null, "A.B2", "C");
        assertThat(r1).isNotEqualTo(r2);
    }

    @Test
    void expressionReturnsNull() {
        String result = BasketRouter.determineTargetBasket(
                BasketStrategy.EXPRESSION, "src", "Topic", "Class");
        assertThat(result).isNull();
    }
}
