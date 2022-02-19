package co.featureflags.server.exterior.model;

import com.google.common.base.MoreObjects;

import java.io.Serializable;
import java.util.Objects;

public final class EvalDetail<T> implements Serializable {

    private final T value;

    private final int index;

    private final String reason;

    private EvalDetail(T value, int index, String reason) {
        this.value = value;
        this.index = index;
        this.reason = reason;
    }

    public static <T> EvalDetail<T> from(T value, int index, String reason) {
        return new EvalDetail(value, index, reason);
    }

    public T getValue() {
        return value;
    }

    public int getIndex() {
        return index;
    }

    public String getReason() {
        return reason;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("value", value).add("index", index).add("reason", reason).toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EvalDetail<?> that = (EvalDetail<?>) o;
        return index == that.index && Objects.equals(value, that.value) && Objects.equals(reason, that.reason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, index, reason);
    }
}
