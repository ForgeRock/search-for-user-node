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
import com.iplanet.sso.SSOException;
import com.sun.identity.shared.debug.Debug;
import com.sun.identity.sm.DNMapper;
import org.forgerock.json.JsonValue;
import org.forgerock.openam.annotations.sm.Attribute;
import org.forgerock.openam.auth.node.api.*;
import javax.inject.Inject;
import static com.sun.identity.idm.IdUtils.getAMIdentityRepository;
import static org.forgerock.openam.auth.node.api.SharedStateConstants.REALM;
import static org.forgerock.openam.auth.node.api.SharedStateConstants.USERNAME;
import org.forgerock.openam.core.CoreWrapper;
import com.sun.identity.idm.*;
import java.util.*;
import org.forgerock.openam.utils.CollectionUtils;
import org.forgerock.util.i18n.PreferredLocales;


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
        }

        @Attribute(order = 200)
        default List<String> datastoreAttributes() {
            return Arrays.asList("username");
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

        Set<String> userSearchAttributes = new HashSet<String>(config.datastoreAttributes());

        AMIdentityRepository amIdRepo = getAMIdentityRepository(DNMapper.orgNameToDN(context.sharedState.get(REALM).asString()));
        IdSearchControl idsc = new IdSearchControl();
        idsc.setRecursive(true);
        idsc.setAllReturnAttributes(true);
        Set results = Collections.EMPTY_SET;
        String searchUsername = context.sharedState.get(config.sharedStateAttribute()).asString();

        IdSearchResults searchResults;
        try {
            idsc.setMaxResults(0);
            Map<String, Set<String>> searchAVP = CollectionUtils.toAvPairMap(userSearchAttributes, searchUsername);
            idsc.setSearchModifiers(IdSearchOpModifier.OR, searchAVP);
            searchResults = amIdRepo.searchIdentities(IdType.USER, "*", idsc);

            if (searchResults != null) {
                results = searchResults.getSearchResults();
            }

            if (results == null || results.size() == 0) {
                debug.error("[" + DEBUG_FILE + "]: " + "Unable to find any matching user");
                return Action.goTo("notFound").build();
            }

            if (results.size() != 1) {
                debug.error("[" + DEBUG_FILE + "]: " + "More than one matching user profile found - ambiguous");
                return Action.goTo("ambiguous").build();
            }

            userIdentity = (AMIdentity)results.iterator().next();
        } catch (IdRepoException e) {
            debug.warning("Error searching for user identity");
        } catch (SSOException e) {
            debug.warning("Error searching for user identity");
        }

        debug.message("[" + DEBUG_FILE + "]: " + "user found: {}", userIdentity.getName());
        //ensure username is set in sharedstate
        context.sharedState.put(USERNAME, userIdentity.getName());
        return goTo("found").build();
    }


    private Action.ActionBuilder goTo(String outcome) {
        return Action.goTo(outcome);
    }


    static final class OutcomeProvider implements org.forgerock.openam.auth.node.api.OutcomeProvider {
        private static final String BUNDLE = SearchForUserNode.class.getName().replace(".", "/");

        @Override
        public List<Outcome> getOutcomes(PreferredLocales locales, JsonValue nodeAttributes) {
            ResourceBundle bundle = locales.getBundleInPreferredLocale(BUNDLE, OutcomeProvider.class.getClassLoader());
            List<Outcome> results = new ArrayList<>();
            results.add(new Outcome("found", "Found"));
            results.add(new Outcome("notFound", "Not Found"));
            results.add(new Outcome("ambiguous", "Ambiguous"));
            return Collections.unmodifiableList(results);
        }
    }
}



