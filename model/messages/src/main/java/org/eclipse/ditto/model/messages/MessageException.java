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
package org.eclipse.ditto.model.messages;

/**
 * Aggregates all {@link org.eclipse.ditto.model.base.exceptions.DittoRuntimeException}s which are related to a {@link
 * Message}.
 */
public interface MessageException {

    /**
     * Error code prefix of errors related to messages.
     */
    String ERROR_CODE_PREFIX = "messages" + ":";

}
