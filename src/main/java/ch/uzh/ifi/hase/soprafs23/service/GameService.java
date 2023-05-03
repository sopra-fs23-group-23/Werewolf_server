package ch.uzh.ifi.hase.soprafs23.service;

import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

import ch.uzh.ifi.hase.soprafs23.logic.lobby.Player;
import ch.uzh.ifi.hase.soprafs23.rest.dto.FractionGetDTO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ch.uzh.ifi.hase.soprafs23.entity.User;
import ch.uzh.ifi.hase.soprafs23.logic.game.Game;
import ch.uzh.ifi.hase.soprafs23.logic.game.GameObserver;
import ch.uzh.ifi.hase.soprafs23.logic.game.Scheduler;
import ch.uzh.ifi.hase.soprafs23.logic.game.StageType;
import ch.uzh.ifi.hase.soprafs23.logic.lobby.Lobby;
import ch.uzh.ifi.hase.soprafs23.logic.poll.Poll;
import ch.uzh.ifi.hase.soprafs23.logic.poll.PollOption;
import ch.uzh.ifi.hase.soprafs23.logic.poll.PollParticipant;
import ch.uzh.ifi.hase.soprafs23.logic.role.gameroles.Villager;
import ch.uzh.ifi.hase.soprafs23.rest.dto.GameGetDTO;
import ch.uzh.ifi.hase.soprafs23.rest.dto.PollGetDTO;
import ch.uzh.ifi.hase.soprafs23.rest.logicmapper.LogicDTOMapper;

@Service
@Transactional
public class GameService implements GameObserver{
    private Map<Long, Game> games = new HashMap<>();

    /**
     * @pre lobby.getLobbySize() <= Lobby.MAX_SIZE && lobby.getLobbySize() >= Lobby.MIN_SIZE && lobby roles assigned
     * @param lobby
     */
    public Game createNewGame(Lobby lobby) {
        assert lobby.getLobbySize() <= Lobby.MAX_SIZE && lobby.getLobbySize() >= Lobby.MIN_SIZE;
        Game game = new Game(lobby);
        game.addObserver(this);
        games.put(lobby.getId(), game);
        return game;
    }

    public Game getGame(Lobby lobby) {
        if (!games.containsKey(lobby.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, String.format("No game found for lobby with id %d", lobby.getId()));
        }
        return games.get(lobby.getId());
    }

    public GameGetDTO toGameGetDTO(Game game) {
        return LogicDTOMapper.convertGameToGameGetDTO(game);
    }

    public void startGame(Game game) {
        game.startGame();
    }

    public void validateGameStarted(Game game) {
        if (!game.isStarted()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Game has not started yet.");
        }
    }

    public Poll getCurrentPoll(Game game) {
        try {
            return game.getCurrentPoll();
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    public boolean isPollParticipant(Poll poll, User user) {
        return poll.getPollParticipants().stream().anyMatch(p->p.getPlayer().getId() == user.getId());
    }

    public void validateParticipant(Poll poll, User user) {
        if (!isPollParticipant(poll, user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User is not participant in this poll.");
        }
    }

    public PollGetDTO toPollGetDTO(Poll poll) {
        return LogicDTOMapper.convertPollToPollGetDTO(poll);
    }

    public PollGetDTO censorPollGetDTO (PollGetDTO pollGetDTO) {
        pollGetDTO.setParticipants(Collections.emptyList());
        pollGetDTO.setPollOptions(Collections.emptyList());
        return pollGetDTO;
    }

    /**
     * @pre validateParticipant
     * @param poll
     * @param user
     * @return
     */
    public PollParticipant getParticipant (Poll poll, User user) {
        return poll.getPollParticipants().stream().filter(p->p.getPlayer().getId() == user.getId()).findFirst().get();
    }

    public PollOption getPollOption(Poll poll, Long pollOptionId) {
        Predicate<? super PollOption> optionFilter = (p->p.getPlayer().getId() == pollOptionId);
        if (!poll.getPollOptions().stream().anyMatch(optionFilter)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Selected option is not a valid option for this poll.");
        }
        return poll.getPollOptions().stream().filter(optionFilter).findFirst().get();
    }

    public void castVote(Poll poll, PollParticipant participant, PollOption option) {
        try {
            poll.castVote(participant, option);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        }
    }

    public void removeVote(Poll poll, PollParticipant participant, PollOption option) {
        try {
            poll.removeVote(participant, option);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        }
    }

    public void validateGameFinished(Game game) {
        if(!game.isFinished()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Game is not finished yet.");
        }
    }

    /**
     * @pre game is finished
     * @param game
     */
    public FractionGetDTO getFractionGetDTO(Game game) {
        assert game.isFinished();
        return LogicDTOMapper.convertFractionToFractionGetDTO(game.getWinner());
    }

    @Override
    public void onNewPoll(Game game) {
        Poll poll = game.getCurrentPoll();
        poll.setScheduledFinish(poll.calculateScheduledFinish(Calendar.getInstance()));
        Scheduler.getInstance().schedule(poll::finish, poll.getDurationSeconds());
    }

    /**
     * @pre hashmap games contains game
     * @param game
     */
    public void removeGame(Game game) {
        assert games.containsKey(game.getLobby().getId());
        games.remove(game.getLobby().getId());
        // TODO this is temporary and might be subject to change
        game.getLobby().dissolve();
    }

    @Override
    public void onGameFinished(Game game) {
        if (games.containsKey(game.getLobby().getId())) {
            Scheduler.getInstance().schedule(()->removeGame(game), 30);
        }
    }

    @Override
    public void onNewStage(Game game) {
        if (game.getCurrentStage().getType() == StageType.Night) {
            Iterable<Player> villagerPlayers = game.getLobby().getPlayersByRole(Villager.class);
            // game.getLobby().getPlayersByRole(Villager.class)
            // kick all villagers
        } else if (game.getCurrentStage().getType() == StageType.Day) {
            // join all villagers
        }
    }
}
