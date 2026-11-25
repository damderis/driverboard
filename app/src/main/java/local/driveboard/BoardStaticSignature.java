package local.driveboard;

/** Unambiguous value key for pixels baked into the board's static layer. */
final class BoardStaticSignature {
    private final String value;

    private BoardStaticSignature(String value) {
        this.value = value;
    }

    static Builder builder() {
        return new Builder();
    }

    @Override public boolean equals(Object other) {
        return other instanceof BoardStaticSignature
                && value.equals(((BoardStaticSignature) other).value);
    }

    @Override public int hashCode() {
        return value.hashCode();
    }

    static final class Builder {
        private final StringBuilder value = new StringBuilder();

        Builder add(String part) {
            if (part == null) {
                value.append("-1:");
            } else {
                value.append(part.length()).append(':').append(part);
            }
            return this;
        }

        Builder add(long part) {
            return add(Long.toString(part));
        }

        Builder add(boolean part) {
            return add(part ? "1" : "0");
        }

        BoardStaticSignature build() {
            return new BoardStaticSignature(value.toString());
        }
    }
}
