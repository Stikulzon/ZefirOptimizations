package ua.zefir.zefiroptimizations.data.predicates;

import net.minecraft.scoreboard.AbstractTeam;
import org.jetbrains.annotations.Nullable;

public class CanBePushedBySnapshot {
    @Nullable
    public final String pusherTeamName;
    public final AbstractTeam.CollisionRule pusherCollisionRule;

    public CanBePushedBySnapshot(@Nullable AbstractTeam pusherTeam, AbstractTeam.CollisionRule pusherCollisionRule) {
        this.pusherTeamName = pusherTeam != null ? pusherTeam.getName() : null;
        this.pusherCollisionRule = pusherCollisionRule;
    }
}