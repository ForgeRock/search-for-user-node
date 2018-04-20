/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2018 ForgeRock AS.
 */
// simon.moffatt@forgerock.com - searches for a user in the datastore

package org.forgerock.openam.auth.nodes;

import com.google.inject.assistedinject.Assisted;
import com.sun.identity.shared.debug.Debug;
import org.forgerock.guava.common.collect.ImmutableList;
import org.forgerock.json.JsonValue;
import org.forgerock.openam.annotations.sm.Attribute;
import org.forgerock.openam.auth.node.api.*;
import javax.inject.Inject;
import static org.forgerock.openam.auth.node.api.SharedStateConstants.REALM;
import static org.forgerock.openam.auth.node.api.SharedStateConstants.USERNAME;
import org.forgerock.openam.core.CoreWrapper;
import com.sun.identity.idm.*;

import java.util.*;

import org.forgerock.util.i18n.PreferredLocales;

import com.sun.identity.idm.IdUtils;


@Node.Metadata(outcomeProvider = SearchForUserNode.OutcomeProvider.class,
        configClass = SearchForUserNode.Config.class)
public class SearchForUserNode implements Node {

    private final static String DEBUG_FILE = "SearchForUserNode";
    protected Debug debug = Debug.getInstance(DEBUG_FILE);
    private final CoreWrapper coreWrapper;

    /**
     * Configuration for the node.
     */
    public interface Config {

        /**
         * A map of property name to value.
         * @return a map of properties.
         */
        @Attribute(order = 100)
        default String sharedStateAttribute() {
            return USERNAME;
        };

        //Toggle as to whether UA is stored in the clear or SHA256 hashed for privacy
        @Attribute(order = 200)
        default String datastoreAttribute() {
            return USERNAME;
        }

    }

    private final Config config;

    /**
     * Create the node.
     * @param config The service config.
     * @throws NodeProcessException If the configuration was not valid.
     */
    @Inject
    public SearchForUserNode(@Assisted Config config, CoreWrapper coreWrapper) throws NodeProcessException {
        this.config = config;
        this.coreWrapper = coreWrapper;
    }

    @Override
    public Action process(TreeContext context) throws NodeProcessException {
   
        debug.message("[" + DEBUG_FILE + "]: " + "Starting");
        AMIdentity userIdentity = null;

        //Pull out the user object
        if (config.datastoreAttribute() == USERNAME && config.sharedStateAttribute() == USERNAME){
            userIdentity = coreWrapper.getIdentity(context.sharedState.get(USERNAME).asString(),context.sharedState.get(REALM).asString());
        } else {
            Set<String> userSearchAttributes = new HashSet<>();
            userSearchAttributes.add(config.datastoreAttribute());
            debug.message("[" + DEBUG_FILE + "]: " + "Datastore attribute: {}", config.datastoreAttribute());
            debug.message("[" + DEBUG_FILE + "]: " + "Shared state attribute: {}, and value: {}", config.sharedStateAttribute(), context.sharedState.get(config.sharedStateAttribute()).asString());
            userIdentity = IdUtils.getIdentity(context.sharedState.get(config.sharedStateAttribute()).asString(), context.sharedState.get(REALM).asString(),userSearchAttributes);

        }

        try {

                if (userIdentity == null) {

                    debug.error("[" + DEBUG_FILE + "]: " + "Unable to find user");
                    return goTo("notFound").build();

                } else {

                    debug.message("[" + DEBUG_FILE + "]: " + "user found: {}", userIdentity.getName());
                    //ensure username is set in sharedstate
                    context.sharedState.put(USERNAME, userIdentity.getName());
                    return goTo("found").build();

                }


            } catch (Exception e) {

                debug.error("[" + DEBUG_FILE + "]: " + "Node exception", e);
                return goTo("notFound").build();
            }
    }

    private Action.ActionBuilder goTo(String outcome) {
        return Action.goTo(outcome);
    }

    static final class OutcomeProvider implements org.forgerock.openam.auth.node.api.OutcomeProvider {
        private static final String BUNDLE = SearchForUserNode.class.getName().replace(".", "/");

        @Override
        public List<Outcome> getOutcomes(PreferredLocales locales, JsonValue nodeAttributes) {
            ResourceBundle bundle = locales.getBundleInPreferredLocale(BUNDLE, OutcomeProvider.class.getClassLoader());
            return ImmutableList.of(
                    new Outcome( "found", bundle.getString("found")),
                    new Outcome("notFound", bundle.getString("notFound")));
        }
    }
}
