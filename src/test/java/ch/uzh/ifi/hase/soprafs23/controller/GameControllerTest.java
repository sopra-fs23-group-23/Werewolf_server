package ch.uzh.ifi.hase.soprafs23.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import ch.uzh.ifi.hase.soprafs23.constant.sse.LobbySseEvent;
import ch.uzh.ifi.hase.soprafs23.entity.User;
import ch.uzh.ifi.hase.soprafs23.logic.game.Game;
import ch.uzh.ifi.hase.soprafs23.logic.lobby.Lobby;
import ch.uzh.ifi.hase.soprafs23.service.GameService;
import ch.uzh.ifi.hase.soprafs23.service.LobbyService;
import ch.uzh.ifi.hase.soprafs23.service.UserService;

@WebMvcTest(GameController.class)
public class GameControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LobbyService lobbyService;

    @MockBean
    private UserService userService;

    @MockBean
    private GameService gameService;

    private User user = mock(User.class);
    private Lobby lobby = mock(Lobby.class);
    private Game game = mock(Game.class);
    
    @Test
    void testCreateNewGame() throws Exception {
        SseEmitter emitter = mock(SseEmitter.class);

        Mockito.when(userService.getUserByToken("token")).thenReturn(user);
        Mockito.when(lobbyService.getLobbyById(1l)).thenReturn(lobby);
        Mockito.when(gameService.createNewGame(lobby)).thenReturn(game);
        Mockito.when(lobbyService.getLobbyEmitter(lobby)).thenReturn(emitter);

        MockHttpServletRequestBuilder postRequest = post("/games/1")
            .header(LobbyController.USERAUTH_HEADER, "token");

        mockMvc.perform(postRequest)
            .andExpect(status().isCreated());
        
        verify(lobbyService).validateUserIsAdmin(user, lobby);
        verify(lobbyService).validateLobbySize(lobby);
        verify(lobbyService).assignRoles(lobby);
        verify(gameService).createGameEmitter(game);
        verify(lobbyService).sendEmitterUpdate(emitter, "", LobbySseEvent.game);
    }

    @Test
    void testGetPlayerSseEmitter() throws Exception {
        Mockito.when(userService.getUserByToken("token")).thenReturn(user);
        Mockito.when(lobbyService.getLobbyById(1l)).thenReturn(lobby);
        Mockito.when(gameService.getGame(lobby)).thenReturn(game);

        MockHttpServletRequestBuilder getRequest = get("/games/1/sse/token");

        mockMvc.perform(getRequest)
            .andExpect(status().isOk());

        verify(lobbyService).validateUserIsInLobby(user, lobby);
        verify(gameService).getPlayerSseEmitter(game, user);
    }
}
