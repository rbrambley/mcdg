package com.mcdg.game;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class BuildSessionSnapshot {
    String ownerId;
    String ownerName;
    long createdAtMs;
    long updatedAtMs;
    long ownerLastSeenAtMs;
    boolean paused;
    int holeCount;
    int nextHoleIndex;
    int rebuildHoleIndex;
    long rebuildToken;
    long rebuildTokenExpiresAtMs;
    int rebuildTokenHoleIndex;
    long claimToken;
    long claimTokenExpiresAtMs;
    List<BuiltHoleSnapshot> builtHoles;
    Set<String> dimensionsUsed;

    static BuildSessionSnapshot from(BuildCourseSessionManager.BuildSession session) {
        BuildSessionSnapshot snapshot = new BuildSessionSnapshot();
        snapshot.ownerId = session.ownerId.toString();
        snapshot.ownerName = session.ownerName;
        snapshot.createdAtMs = session.createdAtMs;
        snapshot.updatedAtMs = session.updatedAtMs;
        snapshot.ownerLastSeenAtMs = session.ownerLastSeenAtMs;
        snapshot.paused = session.paused;
        snapshot.holeCount = session.holeCount;
        snapshot.nextHoleIndex = session.nextHoleIndex;
        snapshot.rebuildHoleIndex = session.rebuildHoleIndex;
        snapshot.rebuildToken = session.rebuildToken;
        snapshot.rebuildTokenExpiresAtMs = session.rebuildTokenExpiresAtMs;
        snapshot.rebuildTokenHoleIndex = session.rebuildTokenHoleIndex;
        snapshot.claimToken = session.claimToken;
        snapshot.claimTokenExpiresAtMs = session.claimTokenExpiresAtMs;
        snapshot.builtHoles = new ArrayList<>();
        for (BuildCourseSessionManager.BuiltHole hole : session.builtHoles) {
            snapshot.builtHoles.add(BuiltHoleSnapshot.from(hole));
        }
        snapshot.dimensionsUsed = new HashSet<>(session.dimensionsUsed);
        return snapshot;
    }

    BuildCourseSessionManager.BuildSession toSession() {
        BuildCourseSessionManager.BuildSession restored = new BuildCourseSessionManager.BuildSession();
        restored.ownerId = UUID.fromString(ownerId);
        restored.ownerName = ownerName;
        restored.createdAtMs = createdAtMs;
        restored.updatedAtMs = updatedAtMs;
        restored.ownerLastSeenAtMs = ownerLastSeenAtMs;
        restored.paused = paused;
        restored.holeCount = holeCount;
        restored.nextHoleIndex = nextHoleIndex;
        restored.rebuildHoleIndex = rebuildHoleIndex;
        restored.rebuildToken = rebuildToken;
        restored.rebuildTokenExpiresAtMs = rebuildTokenExpiresAtMs;
        restored.rebuildTokenHoleIndex = rebuildTokenHoleIndex;
        restored.claimToken = claimToken;
        restored.claimTokenExpiresAtMs = claimTokenExpiresAtMs;
        if (builtHoles != null) {
            for (BuiltHoleSnapshot hole : builtHoles) {
                restored.builtHoles.add(hole.toBuiltHole());
            }
        }
        if (dimensionsUsed != null) {
            restored.dimensionsUsed.addAll(dimensionsUsed);
        }
        return restored;
    }
}
