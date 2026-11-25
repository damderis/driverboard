package local.driveboard;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class CardColorTest {
    @Test
    public void cardsWithoutAColourDefaultToGrey() {
        for (BoardLayout.Card card : BoardLayout.defaults().cards) {
            assertEquals(card.type, "grey", card.options.get("color"));
        }
        BoardLayout parsed = BoardLayout.fromJson("{\"version\":2,\"cards\":[{\"id\":\"p\",\"type\":\"prayer\","
                + "\"col\":0,\"row\":0,\"w\":8,\"h\":3,\"options\":{\"view\":\"full\"}}]}");
        assertEquals("grey", parsed.cards.get(0).options.get("color"));
    }
}
