package ch.uzh.ifi.hase.soprafs23.service;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.StreamSupport;

import javax.transaction.Transactional;

import ch.uzh.ifi.hase.soprafs23.constant.sse.LobbySseEvent;

import ch.uzh.ifi.hase.soprafs23.rest.dto.RoleGetDTO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import ch.uzh.ifi.hase.soprafs23.entity.User;
import ch.uzh.ifi.hase.soprafs23.logic.lobby.Lobby;
import ch.uzh.ifi.hase.soprafs23.logic.lobby.Player;
import ch.uzh.ifi.hase.soprafs23.rest.logicmapper.LogicDTOMapper;
import ch.uzh.ifi.hase.soprafs23.rest.logicmapper.LogicEntityMapper;
import ch.uzh.ifi.hase.soprafs23.service.helper.EmitterHelper;
import ch.uzh.ifi.hase.soprafs23.service.wrapper.PlayerEmitter;

@Service
@Transactional
public class LobbyService {
    public static final String LOBBYID_PATHVARIABLE = "lobbyId";

    private Map<Long, Lobby> lobbies = new HashMap<>();
    private Map<Long, PlayerEmitter> lobbyEmitterMap = new HashMap<>();

    private Long createLobbyId() {
        Long newId = ThreadLocalRandom.current().nextLong(100000, 999999);
        if (lobbies.containsKey(newId)) {
            return createLobbyId();
        }
        return newId;
    }

    public Lobby createNewLobby(User creator) {
        Player admin = LogicEntityMapper.createPlayerFromUser(creator);
        if(lobbies.values().stream().anyMatch(l -> l.getAdmin().equals(admin))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User already has a lobby");
        }
        Lobby l = new Lobby(createLobbyId(), admin);
        lobbies.put(l.getId(), l);
        return l;
    }

    public Collection<Lobby> getLobbies() {
        return lobbies.values();
    }

    public Lobby getLobbyById(Long lobbyId) {
        if (!lobbies.containsKey(lobbyId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, String.format("Lobby with id %d does not exist", lobbyId));
        }
        return lobbies.get(lobbyId);
    }

    private boolean userInALobby(User user) {
        return lobbies.values().stream().anyMatch(
            l -> StreamSupport.stream(l.getPlayers().spliterator(), false).anyMatch(p->p.getId().equals(user.getId()))
        );
    }

    private boolean userIsInLobby(User user, Lobby lobby) {
        return StreamSupport.stream(lobby.getPlayers().spliterator(), false).anyMatch(p->p.getId().equals(user.getId()));
    }

    public void validateUserIsInLobby(User user, Lobby lobby) {
        if (!userIsInLobby(user, lobby)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User is not part of this lobby");
        }
    }

    public void validateLobbyIsOpen(Lobby lobby) {
        if (!lobby.isOpen()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lobby is closed.");
        }
    }

    public void joinUserToLobby(User user, Lobby lobby) {
        if (lobby.getLobbySize() >= Lobby.MAX_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lobby is already full.");
        }
        if (!lobby.isOpen()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lobby is closed.");
        }
        if (userIsInLobby(user, lobby)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User is already in this lobby.");
        }
        if (userInALobby(user)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User is already in a lobby.");
        }
        lobby.addPlayer(LogicEntityMapper.createPlayerFromUser(user));
    }

    public PlayerEmitter createLobbyPlayerEmitter(Lobby lobby) {
        if (lobbyEmitterMap.containsKey(lobby.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Lobby already has an emitter.");
        }
        PlayerEmitter emitter = new PlayerEmitter();
        lobbyEmitterMap.put(lobby.getId(), emitter);
        return emitter;
    }

    public void joinUserToLobbyPlayerEmitter(PlayerEmitter emitter, User user) {
        if (emitter.containsKey(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User already has a sse emitter.");
        }
        emitter.addPlayerEmitter(user.getId(), PlayerEmitter.createDefaulEmitter());
    }

    public PlayerEmitter getLobbyPlayerEmitter (Lobby lobby) {
        if (!lobbyEmitterMap.containsKey(lobby.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Lobby sse emitter not found.");
        }
        return lobbyEmitterMap.get(lobby.getId());
    }

    public SseEmitter getUserSseEmitter (PlayerEmitter emitter, User user) {
        if (!emitter.containsKey(user.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User sse emitter not found.");
        }
        return emitter.getPlayerEmitter(user.getId());
    }

    public void sendEmitterUpdate(PlayerEmitter emitter, String data, LobbySseEvent eventType) {
        emitter.forAllPlayerEmitters(e -> EmitterHelper.sendEmitterUpdate(e, data, eventType.toString()));
    }

    public void validateUserIsAdmin(User user, Lobby lobby) {
        if (!user.getId().equals(lobby.getAdmin().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the admin may perform this action.");
        }
    }

    public void validateLobbySize(Lobby lobby) {
        if (lobby.getLobbySize() > Lobby.MAX_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lobby has too many players.");
        }
        if (lobby.getLobbySize() < Lobby.MIN_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lobby has not enough players.");
        }
    }

    public Collection<RoleGetDTO> getAllRolesInformation(Lobby lobby) {
        return lobby.getRoles().stream().map(role -> LogicDTOMapper.convertRoleToRoleGetDTO(role)).toList();
    }

    /**
     * @pre user is in lobby
     * @param user
     * @param lobby
     * @return
     */
    public Collection<RoleGetDTO> getOwnRolesInformation(User user, Lobby lobby) {
        Player player = lobby.getPlayerById(user.getId());
        return lobby.getRolesOfPlayer(player).stream().map(role -> LogicDTOMapper.convertRoleToRoleGetDTO(role)).toList();
    }

    /**
     * @pre executing user is admin
     * @param lobby
     */
    public void assignRoles(Lobby lobby) {
        lobby.instantiateRoles();
    }

    public void closeLobby(Lobby lobby) {
        lobby.setOpen(false);
    }
}
