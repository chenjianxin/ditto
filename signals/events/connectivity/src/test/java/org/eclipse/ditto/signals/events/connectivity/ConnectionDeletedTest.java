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
package org.eclipse.ditto.signals.events.connectivity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.events.base.Event;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ConnectionDeleted}.
 */
public final class ConnectionDeletedTest {

    private static final JsonObject KNOWN_JSON = JsonObject.newBuilder()
            .set(Event.JsonFields.TYPE, ConnectionDeleted.TYPE)
            .set(ConnectivityEvent.JsonFields.CONNECTION_ID, TestConstants.ID)
            .build();

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ConnectionDeleted.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ConnectionDeleted.class, areImmutable());
    }

    @Test
    public void createInstanceWithNullConnectionId() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> ConnectionDeleted.of(null, DittoHeaders.empty()))
                .withMessage("The %s must not be null!", "Connection ID")
                .withNoCause();
    }

    @Test
    public void fromJsonReturnsExpected() {
        final ConnectionDeleted expected = ConnectionDeleted.of(TestConstants.ID, DittoHeaders.empty());

        final ConnectionDeleted actual = ConnectionDeleted.fromJson(KNOWN_JSON, DittoHeaders.empty());

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void toJsonReturnsExpected() {
        final JsonObject actual = ConnectionDeleted.of(TestConstants.ID, DittoHeaders.empty()).toJson();

        assertThat(actual).isEqualTo(KNOWN_JSON);
    }

}
