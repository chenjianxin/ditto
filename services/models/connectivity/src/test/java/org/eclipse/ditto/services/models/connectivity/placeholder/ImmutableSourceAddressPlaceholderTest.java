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
package org.eclipse.ditto.services.models.connectivity.placeholder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.junit.Test;

/**
 * Tests {@link ImmutableSourceAddressPlaceholder}.
 */
public class ImmutableSourceAddressPlaceholderTest {

    private static final String SOME_MQTT_TOPIC = "some/mqtt/topic";

    @Test
    public void testImmutability() {
        assertInstancesOf(ImmutableSourceAddressPlaceholder.class, areImmutable());
    }

    @Test
    public void testReplaceTopic() {
        assertThat(ImmutableSourceAddressPlaceholder.INSTANCE.apply(SOME_MQTT_TOPIC, "address")).contains(SOME_MQTT_TOPIC);
    }

    @Test
    public void testResultIsEmptyForUnknownPlaceholder() {
        assertThat(ImmutableSourceAddressPlaceholder.INSTANCE.apply(SOME_MQTT_TOPIC, "invalid")).isEmpty();
    }
}
