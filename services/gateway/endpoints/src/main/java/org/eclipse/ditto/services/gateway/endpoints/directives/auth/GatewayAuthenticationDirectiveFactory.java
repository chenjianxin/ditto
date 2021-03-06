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
package org.eclipse.ditto.services.gateway.endpoints.directives.auth;

/**
 * Factory for authentication directives.
 */
public interface GatewayAuthenticationDirectiveFactory {

    /**
     * Builds the {@link GatewayAuthenticationDirective authentication directive} that should be used for the REST api.
     *
     * @return The built {@link GatewayAuthenticationDirective authentication directive}.
     */
    GatewayAuthenticationDirective buildRestApiAuthentication();

    /**
     * Builds the {@link GatewayAuthenticationDirective authentication directive} that should be used for web socket
     * connections.
     *
     * @return The built {@link GatewayAuthenticationDirective authentication directive}.
     */
    GatewayAuthenticationDirective buildWsAuthentication();
}
