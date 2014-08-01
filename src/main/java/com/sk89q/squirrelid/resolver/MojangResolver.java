/*
 * SquirrelID, a UUID library for Minecraft
 * Copyright (C) sk89q <http://www.sk89q.com>
 * Copyright (C) SquirrelID team and contributors
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.sk89q.squirrelid.resolver;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.Iterables;
import com.sk89q.squirrelid.Profile;
import com.sk89q.squirrelid.util.HttpRequest;
import com.sk89q.squirrelid.util.UUIDs;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Resolves names in bulk to UUIDs.
 */
public class MojangResolver implements ProfileResolver {

    public static final String MINECRAFT_AGENT = "Minecraft";

    private static final Logger log = Logger.getLogger(MojangResolver.class.getCanonicalName());
    private static final int MAX_NAMES_PER_REQUEST = 100;

    private final URL profilesURL;

    /**
     * Create a new resolver.
     *
     * <p>For Minecraft, use the {@link #MINECRAFT_AGENT} constant. The UUID
     * to name mapping is only available if a user owns the game for the
     * provided "agent," so an incorrect agent may return zero results or
     * incorrect results.</p>
     *
     * @param agent the agent (i.e. the game)
     */
    public MojangResolver(String agent) {
        checkNotNull(agent);
        profilesURL = HttpRequest.url("https://api.mojang.com/profiles/" + agent);
    }

    @Nullable
    @Override
    public Profile findByName(String name) throws IOException, InterruptedException {
        ImmutableList<Profile> profiles = findAllByName(Arrays.asList(name));
        if (!profiles.isEmpty()) {
            return profiles.get(0);
        } else {
            return null;
        }
    }

    @Override
    public void findAllByName(Iterable<String> names, Predicate<Profile> consumer) throws IOException, InterruptedException {
        for (List<String> partition : Iterables.partition(names, MAX_NAMES_PER_REQUEST)) {
            for (Profile profile : query(partition)) {
                consumer.apply(profile);
            }
        }
    }

    @Override
    public ImmutableList<Profile> findAllByName(Iterable<String> names) throws IOException, InterruptedException {
        Builder<Profile> builder = ImmutableList.builder();
        for (List<String> partition : Iterables.partition(names, MAX_NAMES_PER_REQUEST)) {
            builder.addAll(query(partition));
        }
        return builder.build();
    }

    /**
     * Perform a query for profiles without partitioning the queries.
     *
     * @param names an iterable of names
     * @return a list of results
     * @throws IOException thrown on I/O error
     * @throws InterruptedException thrown on interruption
     */
    protected ImmutableList<Profile> query(Iterable<String> names) throws IOException, InterruptedException {
        List<Profile> profiles = new ArrayList<Profile>();

        Object result = HttpRequest
                .post(profilesURL)
                .bodyJson(names)
                .execute()
                .returnContent()
                .asJson();

        if (result instanceof Iterable) {
            for (Object entry : (Iterable) result) {
                Profile profile = decodeResult(entry);
                if (profile != null) {
                    profiles.add(profile);
                }
            }
        }

        return ImmutableList.copyOf(profiles);
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private static Profile decodeResult(Object entry) {
        try {
            if (entry instanceof Map) {
                Map<Object, Object> mapEntry = (Map<Object, Object>) entry;
                Object rawUuid = mapEntry.get("id");
                Object rawName = mapEntry.get("name");

                if (rawUuid != null && rawName != null) {
                    UUID uuid = UUID.fromString(UUIDs.addDashes(String.valueOf(rawUuid)));
                    String name = String.valueOf(rawName);
                    return new Profile(uuid, name);
                }
            }
        } catch (ClassCastException e) {
            log.log(Level.WARNING, "Got invalid value from UUID lookup service", e);
        } catch (IllegalArgumentException e) {
            log.log(Level.WARNING, "Got invalid value from UUID lookup service", e);
        }

        return null;
    }

    /**
     * Create a resolver for Minecraft.
     *
     * @return a UUID resolver
     */
    public static ProfileResolver forMinecraft() {
        return new MojangResolver(MINECRAFT_AGENT);
    }

}
