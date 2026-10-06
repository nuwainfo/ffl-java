package io.fastfilelink.ffl;

final class OptionalArgument<T> {
    private final boolean present;
    private final boolean hasValue;
    private final T value;

    private OptionalArgument(boolean present, boolean hasValue, T value) {
        this.present = present;
        this.hasValue = hasValue;
        this.value = value;
    }

    static <T> OptionalArgument<T> absent() {
        return new OptionalArgument<>(false, false, null);
    }

    static <T> OptionalArgument<T> flag() {
        return new OptionalArgument<>(true, false, null);
    }

    static <T> OptionalArgument<T> value(T value) {
        return new OptionalArgument<>(true, true, value);
    }

    boolean present() {
        return present;
    }

    boolean hasValue() {
        return hasValue;
    }

    T value() {
        return value;
    }
}
