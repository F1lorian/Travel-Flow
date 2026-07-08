package com.sep.sep_backend.gmailimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sep.sep_backend.entities.Event;
import com.sep.sep_backend.entities.ImportedGmailMessage;
import com.sep.sep_backend.entities.User;
import com.sep.sep_backend.entities.UserEvent;
import com.sep.sep_backend.gmailimport.dto.CreatedEventDTO;
import com.sep.sep_backend.gmailimport.dto.ExtractedEventDTO;
import com.sep.sep_backend.gmailimport.dto.GmailAutoImportResultDTO;
import com.sep.sep_backend.gmailimport.dto.GmailImportRequestDTO;
import com.sep.sep_backend.repos.EventRepository;
import com.sep.sep_backend.repos.ImportedGmailMessageRepository;
import com.sep.sep_backend.repos.TripRepository;
import com.sep.sep_backend.repos.UserEventRepository;
import com.sep.sep_backend.repos.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Tests for the Gmail import orchestration: scanning, de-duplication and event creation. */
@ExtendWith(MockitoExtension.class)
class GmailImportServiceTest {

  @Mock private GmailOAuthService oAuthService;
  @Mock private GmailApiClient gmailApiClient;
  @Mock private EmailEventParser emailEventParser;
  @Mock private EventRepository eventRepository;
  @Mock private UserRepository userRepository;
  @Mock private UserEventRepository userEventRepository;
  @Mock private TripRepository tripRepository;
  @Mock private ImportedGmailMessageRepository importedGmailMessageRepository;

  private GmailImportService service;

  @BeforeEach
  void setUp() {
    service =
        new GmailImportService(
            oAuthService,
            gmailApiClient,
            emailEventParser,
            eventRepository,
            userRepository,
            userEventRepository,
            tripRepository,
            importedGmailMessageRepository,
            20,
            5,
            45);
  }

  @Test
  void scanReturnsEventsSkippingNonEventsAndDuplicates() {
    when(oAuthService.getValidAccessToken(7L)).thenReturn("token");
    when(gmailApiClient.listRecentMessageIds("token", 20)).thenReturn(List.of("a", "b", "c"));
    when(gmailApiClient.getRawMessage(eq("token"), any())).thenReturn(new byte[] {1});

    ExtractedEventDTO concert = event("Concert", LocalDate.of(2026, 6, 1), LocalTime.of(20, 0));
    ExtractedEventDTO duplicate = event("Concert", LocalDate.of(2026, 6, 1), LocalTime.of(20, 0));
    when(emailEventParser.parse(any(), eq("a"))).thenReturn(Optional.of(concert));
    when(emailEventParser.parse(any(), eq("b"))).thenReturn(Optional.empty());
    when(emailEventParser.parse(any(), eq("c"))).thenReturn(Optional.of(duplicate));

    List<ExtractedEventDTO> result = service.scanRecentEmails(7L);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getTitle()).isEqualTo("Concert");
  }

  @Test
  void scanPropagatesRateLimitError() {
    when(oAuthService.getValidAccessToken(7L)).thenReturn("token");
    when(gmailApiClient.listRecentMessageIds("token", 20)).thenReturn(List.of("a"));
    when(gmailApiClient.getRawMessage("token", "a"))
        .thenThrow(new GmailRateLimitException("slow down"));

    assertThatThrownBy(() -> service.scanRecentEmails(7L))
        .isInstanceOf(GmailRateLimitException.class);
  }

  @Test
  void createEventPersistsEventAndAcceptedParticipation() {
    User user = new User();
    user.setId(7L);
    when(userRepository.findById(7L)).thenReturn(Optional.of(user));
    when(eventRepository.save(any(Event.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    GmailImportRequestDTO request = new GmailImportRequestDTO();
    request.setTitle("Dinner");
    request.setDescription("Table for two");
    request.setKosten(42.5);
    request.setDate(LocalDate.of(2026, 6, 1));
    request.setStartTime(LocalTime.of(19, 0));
    request.setEndTime(LocalTime.of(21, 0));

    CreatedEventDTO result = service.createEvent(7L, request);

    ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
    verify(eventRepository).save(eventCaptor.capture());
    Event saved = eventCaptor.getValue();
    assertThat(saved.getName()).isEqualTo("Dinner");
    assertThat(saved.getDescription()).isEqualTo("Table for two");
    assertThat(saved.getKosten()).isEqualTo(42.5);
    assertThat(saved.getStartTime()).isEqualTo(LocalDateTime.of(2026, 6, 1, 19, 0));
    assertThat(saved.getEndTime()).isEqualTo(LocalDateTime.of(2026, 6, 1, 21, 0));

    ArgumentCaptor<UserEvent> participationCaptor = ArgumentCaptor.forClass(UserEvent.class);
    verify(userEventRepository).save(participationCaptor.capture());
    assertThat(participationCaptor.getValue().getStatus())
        .isEqualTo(UserEvent.InviteStatus.ACCEPTED);
    assertThat(result.message()).isEqualTo("Event created successfully");
  }

  @Test
  void createEventDefaultsEndTimeWhenOnlyStartGiven() {
    User user = new User();
    user.setId(7L);
    when(userRepository.findById(7L)).thenReturn(Optional.of(user));
    when(eventRepository.save(any(Event.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    GmailImportRequestDTO request = new GmailImportRequestDTO();
    request.setTitle("Meeting");
    request.setDate(LocalDate.of(2026, 6, 1));
    request.setStartTime(LocalTime.of(9, 0));

    service.createEvent(7L, request);

    ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
    verify(eventRepository).save(eventCaptor.capture());
    assertThat(eventCaptor.getValue().getEndTime()).isEqualTo(LocalDateTime.of(2026, 6, 1, 10, 0));
  }

  @Test
  void createEventFailsWhenDateMissing() {
    GmailImportRequestDTO request = new GmailImportRequestDTO();
    request.setTitle("No date event");

    assertThatThrownBy(() -> service.createEvent(7L, request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("date is required");
    verify(eventRepository, never()).save(any());
  }

  @Test
  void scanAndImportCreatesEventsForConfidentDetectionsAndRecordsThem() {
    when(oAuthService.getValidAccessToken(7L)).thenReturn("token");
    when(gmailApiClient.listRecentMessageIds("token", 20)).thenReturn(List.of("m1"));
    when(gmailApiClient.getRawMessage(eq("token"), any())).thenReturn(new byte[] {1});
    ExtractedEventDTO concert = event("Concert", LocalDate.of(2026, 6, 1), LocalTime.of(20, 0));
    concert.setSourceMessageId("m1");
    when(emailEventParser.parse(any(), eq("m1"))).thenReturn(Optional.of(concert));
    when(importedGmailMessageRepository.existsByUserIdAndMessageId(7L, "m1")).thenReturn(false);
    User user = new User();
    user.setId(7L);
    when(userRepository.findById(7L)).thenReturn(Optional.of(user));
    when(eventRepository.save(any(Event.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    GmailAutoImportResultDTO result = service.scanAndImport(7L);

    assertThat(result.created()).isEqualTo(1);
    assertThat(result.skippedNoDate()).isZero();
    assertThat(result.skippedDuplicate()).isZero();
    verify(eventRepository).save(any(Event.class));

    ArgumentCaptor<ImportedGmailMessage> markerCaptor =
        ArgumentCaptor.forClass(ImportedGmailMessage.class);
    verify(importedGmailMessageRepository).save(markerCaptor.capture());
    assertThat(markerCaptor.getValue().getMessageId()).isEqualTo("m1");
    assertThat(markerCaptor.getValue().getUserId()).isEqualTo(7L);
  }

  @Test
  void scanAndImportSkipsDetectionsWithoutADate() {
    when(oAuthService.getValidAccessToken(7L)).thenReturn("token");
    when(gmailApiClient.listRecentMessageIds("token", 20)).thenReturn(List.of("m1"));
    when(gmailApiClient.getRawMessage(eq("token"), any())).thenReturn(new byte[] {1});
    ExtractedEventDTO noDate = event("Vague mention of a party", null, null);
    noDate.setSourceMessageId("m1");
    when(emailEventParser.parse(any(), eq("m1"))).thenReturn(Optional.of(noDate));

    GmailAutoImportResultDTO result = service.scanAndImport(7L);

    assertThat(result.created()).isZero();
    assertThat(result.skippedNoDate()).isEqualTo(1);
    verify(eventRepository, never()).save(any());
    verify(importedGmailMessageRepository, never()).save(any());
  }

  @Test
  void scanAndImportSkipsAlreadyImportedMessages() {
    when(oAuthService.getValidAccessToken(7L)).thenReturn("token");
    when(gmailApiClient.listRecentMessageIds("token", 20)).thenReturn(List.of("m1"));
    when(gmailApiClient.getRawMessage(eq("token"), any())).thenReturn(new byte[] {1});
    ExtractedEventDTO concert = event("Concert", LocalDate.of(2026, 6, 1), LocalTime.of(20, 0));
    concert.setSourceMessageId("m1");
    when(emailEventParser.parse(any(), eq("m1"))).thenReturn(Optional.of(concert));
    when(importedGmailMessageRepository.existsByUserIdAndMessageId(7L, "m1")).thenReturn(true);

    GmailAutoImportResultDTO result = service.scanAndImport(7L);

    assertThat(result.created()).isZero();
    assertThat(result.skippedDuplicate()).isEqualTo(1);
    verify(eventRepository, never()).save(any());
    verify(importedGmailMessageRepository, never()).save(any());
  }

  private ExtractedEventDTO event(String title, LocalDate date, LocalTime start) {
    ExtractedEventDTO dto = new ExtractedEventDTO();
    dto.setTitle(title);
    dto.setDate(date);
    dto.setStartTime(start);
    return dto;
  }
}
