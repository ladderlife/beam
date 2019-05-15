/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.beam.runners.spark.coders;

import static org.apache.beam.vendor.guava.v20_0.com.google.common.base.Preconditions.checkNotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.apache.beam.runners.spark.translation.ValueAndCoderLazySerializable;
import org.apache.beam.runners.spark.util.ByteArray;
import org.apache.beam.sdk.coders.Coder;
import org.apache.spark.api.java.function.Function;
import org.apache.spark.api.java.function.PairFunction;
import scala.Tuple2;

/** Serialization utility class. */
public final class CoderHelpers {
  private CoderHelpers() {}

  /**
   * Utility method for serializing an object using the specified coder.
   *
   * @param value Value to serialize.
   * @param coder Coder to serialize with.
   * @param <T> type of value that is serialized
   * @return Byte array representing serialized object.
   */
  public static <T> byte[] toByteArray(T value, Coder<T> coder) {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try {
      coder.encode(value, baos);
    } catch (IOException e) {
      throw new IllegalStateException("Error encoding value: " + value, e);
    }
    return baos.toByteArray();
  }

  /**
   * Utility method for serializing a Iterable of values using the specified coder.
   *
   * @param values Values to serialize.
   * @param coder Coder to serialize with.
   * @param <T> type of value that is serialized
   * @return List of bytes representing serialized objects.
   */
  public static <T> List<byte[]> toByteArrays(Iterable<T> values, Coder<T> coder) {
    List<byte[]> res = new ArrayList<>();
    for (T value : values) {
      res.add(toByteArray(value, coder));
    }
    return res;
  }

  /**
   * Utility method for deserializing a byte array using the specified coder.
   *
   * @param serialized bytearray to be deserialized.
   * @param coder Coder to deserialize with.
   * @param <T> Type of object to be returned.
   * @return Deserialized object.
   */
  public static <T> T fromByteArray(byte[] serialized, Coder<T> coder) {
    ByteArrayInputStream bais = new ByteArrayInputStream(serialized);
    try {
      return coder.decode(bais);
    } catch (IOException e) {
      throw new IllegalStateException("Error decoding bytes for coder: " + coder, e);
    }
  }

  /**
   * Utility method for deserializing a Iterable of byte arrays using the specified coder.
   *
   * @param serialized bytearrays to be deserialized.
   * @param coder Coder to deserialize with.
   * @param <T> Type of object to be returned.
   * @return Iterable of deserialized objects.
   */
  public static <T> Iterable<T> fromByteArrays(
      Collection<byte[]> serialized, final Coder<T> coder) {
    return serialized.stream()
        .map(bytes -> fromByteArray(checkNotNull(bytes, "Cannot decode null values."), coder))
        .collect(Collectors.toList());
  }

  /**
   * A function wrapper for converting an object to a lazy serializable.
   *
   * @param coder Coder to (maybe) serialize with.
   * @param <T> The type of the object being serialized
   * @return A function that wraps values in {@link ValueAndCoderLazySerializable} with the provided
   *     coder.
   */
  public static <T> Function<T, ValueAndCoderLazySerializable<T>> toLazyValueAndCoderFunction(
      final Coder<T> coder) {
    return v -> ValueAndCoderLazySerializable.of(v, coder);
  }

  /**
   * A function wrapper for unwrapping a value wrapped with {@link ValueAndCoderLazySerializable}.
   *
   * @param coder The coder that was used (needs to be provided in both serialization and
   *     deserialization).
   * @param <T> The type of the object being deserialized.
   * @return A function that accepts {@link ValueAndCoderLazySerializable} and returns the
   *     deserialized value.
   */
  public static <T> Function<ValueAndCoderLazySerializable<T>, T> fromLazyValueAndCoderFunction(
      final Coder<T> coder) {
    return v -> v.getOrDecode(coder);
  }

  /**
   * Transforms an {@link Iterable} of values to an {@link List} of {@link
   * ValueAndCoderLazySerializable}.
   *
   * @param it The set of intput values.
   * @param coder The coder to use for each value.
   * @param <T> The type of the object being serialized.
   * @return An {@link List} of {@link ValueAndCoderLazySerializable}s holding the values in it.
   */
  public static <T> List<ValueAndCoderLazySerializable<T>> toLazyValueAndCoders(
      final Iterable<T> it, final Coder<T> coder) {
    return StreamSupport.stream(it.spliterator(), false)
        .map(v -> ValueAndCoderLazySerializable.of(v, coder))
        .collect(Collectors.toList());
  }

  public static <T> List<T> fromLazyValueAndCoders(
      final Iterable<ValueAndCoderLazySerializable<T>> it, final Coder<T> coder) {
    return StreamSupport.stream(it.spliterator(), false)
        .map(v -> v.getOrDecode(coder))
        .collect(Collectors.toList());
  }

  /**
   * A function wrapper for converting an object to a bytearray.
   *
   * @param coder Coder to serialize with.
   * @param <T> The type of the object being serialized.
   * @return A function that accepts an object and returns its coder-serialized form.
   */
  public static <T> Function<T, byte[]> toByteFunction(final Coder<T> coder) {
    return t -> toByteArray(t, coder);
  }

  /**
   * A function wrapper for converting a byte array to an object.
   *
   * @param coder Coder to deserialize with.
   * @param <T> The type of the object being deserialized.
   * @return A function that accepts a byte array and returns its corresponding object.
   */
  public static <T> Function<byte[], T> fromByteFunction(final Coder<T> coder) {
    return bytes -> fromByteArray(bytes, coder);
  }

  /**
   * A function wrapper for converting a key-value pair to a byte array key lazy serialized value.
   *
   * @param keyCoder Coder to serialize keys.
   * @param valueCoder Coder to serialize values, if necessary.
   * @param <K> The type of the key being serialized.
   * @param <V> The type of the value being serialized.
   * @return A function that accepts a key-value pair the above.
   */
  public static <K, V>
      PairFunction<Tuple2<K, V>, ByteArray, ValueAndCoderLazySerializable<V>>
          toByteArrayLazyValueFunction(final Coder<K> keyCoder, final Coder<V> valueCoder) {
    return kv ->
        new Tuple2<>(
            new ByteArray(toByteArray(kv._1(), keyCoder)),
            ValueAndCoderLazySerializable.of(kv._2(), valueCoder));
  }

  /**
   * A function wrapper for converting a byte array key lazy value to a key-value pair.
   *
   * @param keyCoder Coder to deserialize keys.
   * @param valueCoder Coder to deserialize values, if necessary.
   * @param <K> The type of the key being deserialized.
   * @param <V> The type of the value being deserialized.
   * @return A function that accepts a pair of a byte array key and a lazy serialized value and
   *     returns a key-value pair.
   */
  public static <K, V>
      PairFunction<Tuple2<ByteArray, ValueAndCoderLazySerializable<V>>, K, V>
          fromByteArrayLazyValueFunction(final Coder<K> keyCoder, final Coder<V> valueCoder) {
    return tuple ->
        new Tuple2<>(
            fromByteArray(tuple._1().getValue(), keyCoder), tuple._2().getOrDecode(valueCoder));
  }

  /**
   * A function wrapper for converting a key-value pair to a byte array pair.
   *
   * @param keyCoder Coder to serialize keys.
   * @param valueCoder Coder to serialize values.
   * @param <K> The type of the key being serialized.
   * @param <V> The type of the value being serialized.
   * @return A function that accepts a key-value pair and returns a pair of byte arrays.
   */
  public static <K, V> PairFunction<Tuple2<K, V>, ByteArray, byte[]> toByteFunction(
      final Coder<K> keyCoder, final Coder<V> valueCoder) {
    return kv ->
        new Tuple2<>(
            new ByteArray(toByteArray(kv._1(), keyCoder)), toByteArray(kv._2(), valueCoder));
  }

  /**
   * A function wrapper for converting a byte array pair to a key-value pair.
   *
   * @param keyCoder Coder to deserialize keys.
   * @param valueCoder Coder to deserialize values.
   * @param <K> The type of the key being deserialized.
   * @param <V> The type of the value being deserialized.
   * @return A function that accepts a pair of byte arrays and returns a key-value pair.
   */
  public static <K, V> PairFunction<Tuple2<ByteArray, byte[]>, K, V> fromByteFunction(
      final Coder<K> keyCoder, final Coder<V> valueCoder) {
    return tuple ->
        new Tuple2<>(
            fromByteArray(tuple._1().getValue(), keyCoder), fromByteArray(tuple._2(), valueCoder));
  }

  /**
   * A function wrapper for converting a byte array key lazy value pair to a key-value pair, where
   * values are {@link Iterable}.
   *
   * @param keyCoder Coder to deserialize keys.
   * @param valueCoder Coder to deserialize values, if necessary.
   * @param <K> The type of the key being deserialized.
   * @param <V> The type of the value being deserialized.
   * @return A function that accepts a pair of byte arrays and returns a key-value pair.
   */
  public static <K, V>
      PairFunction<Tuple2<ByteArray, Iterable<ValueAndCoderLazySerializable<V>>>, K, Iterable<V>>
          fromByteArrayLazyValuedIterableFunction(
              final Coder<K> keyCoder, final Coder<V> valueCoder) {
    return tuple ->
        new Tuple2<>(
            fromByteArray(tuple._1().getValue(), keyCoder),
            StreamSupport.stream(tuple._2().spliterator(), false)
                .map(v -> v.getOrDecode(valueCoder))
                .collect(Collectors.toList()));
  }
}
