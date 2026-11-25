package local.driveboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

public final class BoardStaticSignatureTest {
    @Test public void identicalVisibleValuesHaveSameSignature() {
        assertEquals(signature("12:40", "in 8 min"), signature("12:40", "in 8 min"));
    }

    @Test public void countdownMinuteChangesSignature() {
        assertNotEquals(signature("12:40", "in 8 min"), signature("12:40", "in 7 min"));
    }

    @Test public void fieldsCannotAliasAtBoundaries() {
        assertNotEquals(BoardStaticSignature.builder().add("ab").add("c").build(),
                BoardStaticSignature.builder().add("a").add("bc").build());
    }

    private static BoardStaticSignature signature(String clock, String countdown) {
        return BoardStaticSignature.builder().add("full").add(clock)
                .add(countdown).add("layout").build();
    }
}
