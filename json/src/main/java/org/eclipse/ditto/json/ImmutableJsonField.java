/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.json;

import static java.util.Objects.requireNonNull;

import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * An immutable JSON field implementation. A JSON field is always required to have at least a key and a value.
 */
@Immutable
final class ImmutableJsonField implements JsonField {

    private final JsonKey key;
    private final JsonValue value;
    @Nullable private final JsonFieldDefinition definition;
    @Nullable private String stringRepresentation;

    private ImmutableJsonField(final JsonKey theKey, final JsonValue theValue,
            @Nullable final JsonFieldDefinition theDefinition) {

        key = requireNonNull(theKey, "The JSON key must not be null!");
        value = requireNonNull(theValue, "The JSON value must not be null!");
        definition = theDefinition;
        stringRepresentation = null;
    }

    /**
     * Returns a new instance of {@code ImmutableJsonField} based on the specified key value pair.
     *
     * @param key the key of the field to be created.
     * @param value the value of the field to be created.
     * @return a new JSON field object.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ImmutableJsonField newInstance(final JsonKey key, final JsonValue value) {
        return newInstance(key, value, null);
    }

    /**
     * Returns a new instance of {@code ImmutableJsonField} based on the specified key value pair.
     *
     * @param key the key of the field to be created.
     * @param value the value of the field to be created.
     * @param definition the definition of the field to be created.
     * @return a new JSON field object.
     * @throws NullPointerException if any argument but {@code definition} is {@code null}.
     */
    public static ImmutableJsonField newInstance(final JsonKey key, final JsonValue value,
            @Nullable final JsonFieldDefinition definition) {

        return new ImmutableJsonField(key, value, definition);
    }

    @Override
    public String getKeyName() {
        return key.toString();
    }

    @Override
    public JsonKey getKey() {
        return key;
    }

    @Override
    public JsonValue getValue() {
        return value;
    }

    @Override
    public Optional<JsonFieldDefinition> getDefinition() {
        return Optional.ofNullable(definition);
    }

    @Override
    public boolean isMarkedAs(final JsonFieldMarker fieldMarker, final JsonFieldMarker... furtherFieldMarkers) {
        return null != definition && definition.isMarkedAs(fieldMarker, furtherFieldMarkers);
    }

    @SuppressWarnings({"squid:MethodCyclomaticComplexity", "squid:S1067"})
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableJsonField that = (ImmutableJsonField) o;
        return Objects.equals(key, that.key) && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, value);
    }

    @Override
    public String toString() {
        // keep escaped string as escaping is expensive
        String result = stringRepresentation;
        if (null == result) {
            result = getEscapedKeyName() + ":" + value;
            stringRepresentation = result;
        }
        return result;
    }

    private String getEscapedKeyName() {
        final UnaryOperator<String> javaStringToEscapeJsonString = JavaStringToEscapedJsonString.getInstance();
        return javaStringToEscapeJsonString.apply(key.toString());
    }

}
