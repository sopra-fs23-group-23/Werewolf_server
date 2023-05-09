package ch.uzh.ifi.hase.soprafs23.logic.game;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import ch.uzh.ifi.hase.soprafs23.logic.lobby.Lobby;
import ch.uzh.ifi.hase.soprafs23.logic.poll.Poll;
import ch.uzh.ifi.hase.soprafs23.logic.poll.pollcommand.PollCommand;
import ch.uzh.ifi.hase.soprafs23.logic.poll.pollcommand.StageFinishedCommand;
import ch.uzh.ifi.hase.soprafs23.logic.role.Fraction;
import ch.uzh.ifi.hase.soprafs23.logic.role.Role;
import ch.uzh.ifi.hase.soprafs23.logic.role.stagevoter.DayVoter;
import ch.uzh.ifi.hase.soprafs23.logic.role.stagevoter.DoubleNightVoter;
import ch.uzh.ifi.hase.soprafs23.logic.role.stagevoter.FirstDayVoter;
import ch.uzh.ifi.hase.soprafs23.logic.role.stagevoter.FirstNightVoter;
import ch.uzh.ifi.hase.soprafs23.logic.role.stagevoter.NightVoter;
import ch.uzh.ifi.hase.soprafs23.logic.role.stagevoter.StageVoter;

public class Game implements StageObserver{
    private Lobby lobby;
    private Stage currentStage;
    private boolean started = false;
    private Optional<Poll> currentPoll = Optional.empty();
    private Optional<Fraction> winner = Optional.empty();
    private int stageCount = 0;
    private int pollCount = 0;
    private boolean finished = false;
    private List<PollCommand> lastStagePollCommands = new ArrayList<>();
    private List<GameObserver> observers = new ArrayList<>();

    /**
     * @pre lobby.getLobbySize() <= Lobby.MAX_SIZE && lobby.getLobbySize() >= Lobby.MIN_SIZE && lobby roles assigned
     * @param lobby
     */
    public Game(Lobby lobby) {
        assert lobby.getLobbySize() <= Lobby.MAX_SIZE && lobby.getLobbySize() >= Lobby.MIN_SIZE;
        this.lobby = lobby;
    }

    public void addObserver(GameObserver observer) {
        observers.add(observer);
    }

    private Stage calculateNextStage() {
        // special cases
        if (stageCount == 0) {
            // first day
            return new Stage(StageType.Day, getFirstDayVoters());
        }
        if (stageCount == 1) {
            // first night
            return new Stage(StageType.Night, getFirstNightVoters());
        }
        // normal cases
        if (stageCount % 2 == 0) {
            return new Stage(StageType.Day, getDayVoters());
        } else {
            return new Stage(StageType.Night, getNightVoters());
        }
    }

    public void startGame() {
        startNextStage(calculateNextStage());
        started = true;
    }

    public boolean isStarted() {
        return started;
    }

    private void startNextStage(Stage nextStage) {
        stageCount++;
        currentStage = nextStage;
        currentStage.addObserver(this);
        currentStage.startStage();
        observers.forEach(gameObserver -> gameObserver.onNewStage(this));
    }

    public Lobby getLobby() {
        return lobby;
    }

    public Stage getCurrentStage() {
        return currentStage;
    }

    public int getPollCount() {
        return pollCount;
    }

    /**
     * @pre isStarted()
     * @return List<PollCommand> of the current stage
     */
    public List<PollCommand> getCurrentStagePollCommands() {
        return currentStage.getPollCommands();
    }

    /**
     * @pre isStarted()
     * @param pollCommand
     */
    public void removePollCommandFromCurrentStage(PollCommand pollCommand) {
        currentStage.removePollCommand(pollCommand);
    }

    public void addPollCommandToCurrentStage(PollCommand pollCommand) {
        currentStage.addPollCommand(pollCommand);
    }

    public List<PollCommand> getLastStagePollCommands() {
        return lastStagePollCommands;
    }

    public Poll getCurrentPoll() throws IllegalStateException{
        if (currentPoll.isEmpty()) {
            throw new IllegalStateException("No poll is currently running");
        }
        return currentPoll.get();
    }

    public Fraction getWinner() {
        if (winner.isEmpty()) {
            throw new IllegalStateException("Game is not finished yet");
        }
        return winner.get();
    }

    @Override
    public void onStageFinished() {
        List<PollCommand> currentStagePollCommands = new ArrayList<>(currentStage.getPollCommands());
        currentStagePollCommands.stream()
            .filter(StageFinishedCommand.class::isInstance)
            .map(StageFinishedCommand.class::cast)
            .forEach(p->p.executeAfterStageFinished());
        for (Fraction fraction : lobby.getFractions()) {
            if(fraction.hasWon()) {
                finishGame(fraction);
                return;
            }
        }
        lastStagePollCommands = currentStage.getPollCommands();
        startNextStage(calculateNextStage());
    }

    private void finishGame(Fraction winningFraction) {
        winner = Optional.of(winningFraction);
        finished = true;
        observers.stream().forEach(observer->observer.onGameFinished(this));
    }

    public boolean isFinished() {
        return finished;
    }

    public static Queue<Supplier<Optional<Poll>>> getVotersOfType(Collection<Role> roles, Class<? extends StageVoter> stageVoterClass, Function<Role, Supplier<Optional<Poll>>> pollFunction) {
        return roles.stream()
            .filter(stageVoterClass::isInstance)
            .sorted()
            .map(s -> pollFunction.apply(s))
            .collect(Collectors.toCollection(LinkedList::new));
    }

    public static void forVoterRolesOfType(Collection<Role> roles, Class<? extends StageVoter> stageVoterClass, Consumer<? super Role> stageVoterClassAction) {
        // TODO test
        roles.stream()
            .filter(stageVoterClass::isInstance)
            .sorted()
            .forEach(stageVoterClassAction);
    }

    private Queue<Supplier<Optional<Poll>>> getDayVoters() {
        return Game.getVotersOfType(lobby.getRoles(), DayVoter.class, dayVoterRole -> ((DayVoter)dayVoterRole)::createDayPoll);
    }

    private Queue<Supplier<Optional<Poll>>> getFirstDayVoters() {
        return Game.getVotersOfType(lobby.getRoles(), FirstDayVoter.class, firstDayVoterRole -> ((FirstDayVoter)firstDayVoterRole)::createFirstDayPoll);
    }

    private Queue<Supplier<Optional<Poll>>> getNightVoters() {
        Queue<Supplier<Optional<Poll>>> nightVoters = new LinkedList<>();
        Game.forVoterRolesOfType(lobby.getRoles(), NightVoter.class, nightVoterRole -> {
            nightVoters.add(((NightVoter)nightVoterRole)::createNightPoll);
            if (nightVoterRole instanceof DoubleNightVoter) {
                nightVoters.add(((DoubleNightVoter)nightVoterRole)::createSecondNightPoll);
            }
        });
        return nightVoters;
    }

    private Queue<Supplier<Optional<Poll>>> getFirstNightVoters() {
        return Game.getVotersOfType(lobby.getRoles(), FirstNightVoter.class, firstNightVoterRole -> ((FirstNightVoter)firstNightVoterRole)::createFirstNightPoll);
    }

    @Override
    public void onNewPoll(Poll poll) {
        pollCount++;
        currentPoll = Optional.of(poll);
        observers.forEach(o -> o.onNewPoll(this));
    }
    
}
