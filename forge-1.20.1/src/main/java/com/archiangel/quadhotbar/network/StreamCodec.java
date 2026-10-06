package com.archiangel.quadhotbar.network;

import java.util.function.BiFunction;
import java.util.function.Function;

/** Small codec contract shared by the port's packet definitions. */
public interface StreamCodec<B, T> {
    T decode(B buffer);

    void encode(B buffer, T value);

    static <B, V, T> StreamCodec<B, T> composite(
            StreamCodec<B, V> value, Function<T, V> getter, Function<V, T> factory) {
        return new StreamCodec<>() {
            public T decode(B buffer) {
                return factory.apply(value.decode(buffer));
            }

            public void encode(B buffer, T packet) {
                value.encode(buffer, getter.apply(packet));
            }
        };
    }

    static <B, V, W, T> StreamCodec<B, T> composite(
            StreamCodec<B, V> first,
            Function<T, V> firstGetter,
            StreamCodec<B, W> second,
            Function<T, W> secondGetter,
            BiFunction<V, W, T> factory) {
        return new StreamCodec<>() {
            public T decode(B buffer) {
                return factory.apply(first.decode(buffer), second.decode(buffer));
            }

            public void encode(B buffer, T packet) {
                first.encode(buffer, firstGetter.apply(packet));
                second.encode(buffer, secondGetter.apply(packet));
            }
        };
    }
}
