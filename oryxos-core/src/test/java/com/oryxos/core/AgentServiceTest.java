package com.oryxos.core;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oryxos.core.profile.AgentLoader;
import com.oryxos.core.profile.Profile;
import com.oryxos.core.profile.ProfileContext;
import com.oryxos.core.profile.ProfileRegistry;
import com.oryxos.core.react.ReActLoop;
import com.oryxos.core.session.Session;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgentServiceTest {

  @Mock private ProfileRegistry profileRegistry;
  @Mock private AgentLoader agentLoader;
  @Mock private ReActLoop reActLoop;
  @Mock private SessionPersistencePort sessionPersistencePort;

  private AgentService agentService;
  private Session session;
  private Profile profile;

  @BeforeEach
  void setUp() {
    agentService =
        new AgentService(profileRegistry, agentLoader, reActLoop, sessionPersistencePort);
    session = new Session("s-1", "test-profile", "cli", "user1");
    profile = new Profile();
    profile.setName("test-profile");
    when(profileRegistry.find("test-profile")).thenReturn(Optional.of(profile));
  }

  @Test
  @DisplayName("处理期间 ProfileContext 可取到当前 Profile")
  void profileContextIsAccessibleDuringProcessing() {
    when(reActLoop.run(any(), anyString(), any(), anyString())).thenReturn("ok");

    agentService.process(session, "hi");

    assertNull(ProfileContext.get(), "ProfileContext must be cleared after processing");
  }

  @Test
  @DisplayName("处理中抛异常_ProfileContext也必须被清掉")
  void profileContextIsClearedAfterException() {
    when(reActLoop.run(any(), anyString(), any(), anyString()))
        .thenThrow(new RuntimeException("boom"));

    assertThrows(RuntimeException.class, () -> agentService.process(session, "hi"));

    assertNull(ProfileContext.get(), "ProfileContext must be cleared even on exception");
  }

  @Test
  @DisplayName("结束后 Session 被持久化")
  void sessionIsPersistedAfterSuccessfulProcessing() {
    when(reActLoop.run(any(), anyString(), any(), anyString())).thenReturn("ok");

    agentService.process(session, "hi");

    verify(sessionPersistencePort).save(session);
  }
}
