package ch.uzh.ifi.hase.soprafs23.logic.role.gameroles;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import ch.uzh.ifi.hase.soprafs23.logic.lobby.Player;
import ch.uzh.ifi.hase.soprafs23.logic.lobby.PlayerObserver;
import ch.uzh.ifi.hase.soprafs23.logic.poll.pollcommand.KillPlayerPollCommand;
import ch.uzh.ifi.hase.soprafs23.logic.poll.pollcommand.PollCommand;
import ch.uzh.ifi.hase.soprafs23.logic.role.Fraction;
import ch.uzh.ifi.hase.soprafs23.logic.role.Role;

public class Lover extends Role implements Fraction, PlayerObserver {
    private final Supplier<List<Player>> alivePlayersGetter;
    private final Consumer<PollCommand> pollCommandAdderConsumer;
    private boolean killCommandExecuted = false;

    public Lover(Supplier<List<Player>> alivePlayersGetter, Consumer<PollCommand> pollCommandAdderConsumer) {
        this.alivePlayersGetter = alivePlayersGetter;
        this.pollCommandAdderConsumer = pollCommandAdderConsumer;
    }

    @Override
    public void addPlayer(Player player) {
        player.addObserver(this);
        super.addPlayer(player);
    }

    @Override
    public boolean hasWon() {
        for(Player player : alivePlayersGetter.get()) {
            if(!getPlayers().contains(player)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String getName() {
        return "Lover";
    }

    @Override
    public String getDescription() {
        // TODO Auto-generated method stub
        return "TODO";
    }

    private void killLovers() {
        List<Player> aliveLovers = getPlayers().stream().filter(Player::isAlive).toList();
        for (Player player : aliveLovers) {
            KillPlayerPollCommand killPlayerPollCommand = new KillPlayerPollCommand(player);
            killPlayerPollCommand.execute();
            pollCommandAdderConsumer.accept(killPlayerPollCommand);
        }
    }

    @Override
    public void onPlayerKilled() {
        if (!killCommandExecuted) {
            killCommandExecuted = true;
            killLovers();
        }
    }
    
}
