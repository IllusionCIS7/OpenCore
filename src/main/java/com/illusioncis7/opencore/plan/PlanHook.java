package com.illusioncis7.opencore.plan;

import com.djrapitops.plan.capability.CapabilityService;
import com.djrapitops.plan.query.QueryService;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Accesses Plan's Query API if available.
 */
public class PlanHook {
    private QueryService queryService;

    /** Attempt to hook into Plan. */
    public boolean hook() {
        try {
            if (!CapabilityService.getInstance().hasCapability("QUERY_API")) {
                return false;
            }
            queryService = QueryService.getInstance();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isAvailable() {
        return queryService != null;
    }

    /**
     * Fetch UUIDs of players considered active.
     *
     * @param since Duration since the cutoff point
     * @return set of UUIDs
     */
    public Set<UUID> getActivePlayers(Duration since) {
        if (queryService == null) return Collections.emptySet();
        long cutoff = System.currentTimeMillis() - since.toMillis();
        String sql = "SELECT DISTINCT plan_users.uuid FROM plan_users " +
                "JOIN plan_sessions ON plan_users.id=plan_sessions.user_id " +
                "WHERE plan_sessions.session_end>?";
        return queryService.query(sql, stmt -> {
            stmt.setLong(1, cutoff);
            Set<UUID> set = new HashSet<>();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    set.add(UUID.fromString(rs.getString(1)));
                }
            }
            return set;
        });
    }

    /**
     * Fetch the last session end time for a player.
     *
     * @param uuid Player UUID
     * @return Instant of the last seen time or null if unavailable
     */
    public java.time.Instant getLastSeen(UUID uuid) {
        if (queryService == null) return null;
        String sql = "SELECT MAX(plan_sessions.session_end) FROM plan_sessions " +
                "JOIN plan_users ON plan_users.id = plan_sessions.user_id " +
                "WHERE plan_users.uuid = ?";
        return queryService.query(sql, stmt -> {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    long ts = rs.getLong(1);
                    if (ts > 0) {
                        return java.time.Instant.ofEpochMilli(ts);
                    }
                }
            }
            return null;
        });
    }
}
