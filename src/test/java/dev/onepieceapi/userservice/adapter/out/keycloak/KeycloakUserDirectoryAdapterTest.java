package dev.onepieceapi.userservice.adapter.out.keycloak;

import dev.onepieceapi.userservice.adapter.out.keycloak.config.KeycloakAdminProperties;
import dev.onepieceapi.userservice.adapter.out.keycloak.config.KeycloakInvitationProperties;
import dev.onepieceapi.userservice.application.exception.EmailAlreadyRegisteredException;
import dev.onepieceapi.userservice.application.exception.EmailDeliveryFailedException;
import dev.onepieceapi.userservice.application.exception.InvitationNotResendableException;
import dev.onepieceapi.userservice.application.exception.LastAdministratorException;
import dev.onepieceapi.userservice.application.exception.LastRoleException;
import dev.onepieceapi.userservice.application.exception.UserNotFoundException;
import dev.onepieceapi.userservice.domain.AccountStatus;
import dev.onepieceapi.userservice.domain.RealmRole;
import dev.onepieceapi.userservice.domain.User;
import dev.onepieceapi.userservice.domain.UserFilter;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.RoleMappingResource;
import org.keycloak.admin.client.resource.RoleResource;
import org.keycloak.admin.client.resource.RoleScopeResource;
import org.keycloak.admin.client.resource.RolesResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.AdminEventRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KeycloakUserDirectoryAdapterTest {

	private static final Instant NOW = Instant.parse("2026-08-25T10:00:00Z");

	private static final String LUFFY_ID = "cccccccc-cccc-cccc-cccc-cccccccccccc";

	private static final String NAMI_ID = "dddddddd-dddd-dddd-dddd-dddddddddddd";

	private static final String INVITED_EMAIL = "usopp@onepiece.local";

	@Mock
	private Keycloak keycloakAdminClient;

	@Mock
	private RealmResource realmResource;

	@Mock
	private UsersResource usersResource;

	@Captor
	private ArgumentCaptor<UserRepresentation> newUserCaptor;

	private KeycloakUserDirectoryAdapter keycloakUserDirectoryAdapter;

	/**
	 * Set by {@link #mockActiveUser} so a test can verify the exact add/remove mock used.
	 */
	private RoleScopeResource namiRoleScope;

	private RoleScopeResource luffyRoleScope;

	@BeforeEach
	void setUp() {
		String clientId = "user-service-admin";
		var adminProperties = new KeycloakAdminProperties("http://keycloak", "onepiece", clientId, "secret",
				Set.of("default-roles-onepiece"));
		var invitationProperties = new KeycloakInvitationProperties("onepiece-proxy", "http://localhost:4180/",
				Duration.ofHours(12));
		ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
		Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
		this.keycloakUserDirectoryAdapter = new KeycloakUserDirectoryAdapter(this.keycloakAdminClient, executor,
				adminProperties, invitationProperties, clock);

		lenient().when(this.keycloakAdminClient.realm("onepiece")).thenReturn(this.realmResource);
		lenient().when(this.realmResource.users()).thenReturn(this.usersResource);
	}

	@Test
	void paginatesTheRealmsOwnUserListNatively() {
		when(this.usersResource.list(20, 10)).thenReturn(List.of());

		List<User> users = this.keycloakUserDirectoryAdapter.findUsers(20, 10, UserFilter.none());

		verify(this.usersResource).list(20, 10);
		assertThat(users).isEmpty();
	}

	@Test
	void injectsEachAccountsOwnRealmRolesConcurrently() {
		UserRepresentation luffy = userWithId(LUFFY_ID);
		UserRepresentation nami = userWithId(NAMI_ID);
		when(this.usersResource.list(0, 10)).thenReturn(List.of(luffy, nami));
		mockRoles(LUFFY_ID, "ADMIN");
		mockRoles(NAMI_ID, "EDITOR", "REVIEWER");

		List<User> users = this.keycloakUserDirectoryAdapter.findUsers(0, 10, UserFilter.none());

		assertThat(users).extracting(User::userId, User::roles)
			.containsExactlyInAnyOrder(tuple(UUID.fromString(LUFFY_ID), List.of("ADMIN")),
					tuple(UUID.fromString(NAMI_ID), List.of("EDITOR", "REVIEWER")));
	}

	@Test
	void filtersOutTheAutoAssignedDefaultRealmRole() {
		UserRepresentation luffy = userWithId(LUFFY_ID);
		when(this.usersResource.list(0, 10)).thenReturn(List.of(luffy));
		mockRoles(LUFFY_ID, "ADMIN", "default-roles-onepiece");

		List<User> users = this.keycloakUserDirectoryAdapter.findUsers(0, 10, UserFilter.none());

		assertThat(users.getFirst().roles()).containsExactly("ADMIN");
	}

	@Test
	void aPendingUserWithNoRecordedInvitationEventStaysPending() {
		UserRepresentation pending = pendingUserWithId(NAMI_ID);
		when(this.usersResource.list(0, 10)).thenReturn(List.of(pending));
		mockRoles(NAMI_ID);
		mockAdminEvents(NAMI_ID, List.of());

		List<User> users = this.keycloakUserDirectoryAdapter.findUsers(0, 10, UserFilter.none());

		assertThat(users.getFirst().status()).isEqualTo(AccountStatus.PENDING);
	}

	@Test
	void aPendingUserWithARecentInvitationEventStaysPending() {
		UserRepresentation pending = pendingUserWithId(NAMI_ID);
		when(this.usersResource.list(0, 10)).thenReturn(List.of(pending));
		mockRoles(NAMI_ID);
		mockAdminEvents(NAMI_ID, List.of(adminEventAt(NOW.minus(Duration.ofHours(1)))));

		List<User> users = this.keycloakUserDirectoryAdapter.findUsers(0, 10, UserFilter.none());

		assertThat(users.getFirst().status()).isEqualTo(AccountStatus.PENDING);
	}

	@Test
	void aPendingUserWithAStaleInvitationEventBecomesExpired() {
		UserRepresentation pending = pendingUserWithId(NAMI_ID);
		when(this.usersResource.list(0, 10)).thenReturn(List.of(pending));
		mockRoles(NAMI_ID);
		mockAdminEvents(NAMI_ID, List.of(adminEventAt(NOW.minus(Duration.ofHours(13)))));

		List<User> users = this.keycloakUserDirectoryAdapter.findUsers(0, 10, UserFilter.none());

		assertThat(users.getFirst().status()).isEqualTo(AccountStatus.INVITATION_EXPIRED);
	}

	@Test
	void anActiveUserNeverTriggersAnAdminEventsLookup() {
		UserRepresentation active = userWithId(LUFFY_ID);
		when(this.usersResource.list(0, 10)).thenReturn(List.of(active));
		mockRoles(LUFFY_ID, "ADMIN");

		List<User> users = this.keycloakUserDirectoryAdapter.findUsers(0, 10, UserFilter.none());

		assertThat(users.getFirst().status()).isEqualTo(AccountStatus.ACTIVE);
		verifyNoAdminEventsCall();
	}

	/**
	 * {@code str()} (not {@code any()}) at the {@code dateFrom}/{@code dateTo} positions:
	 * see {@link #mockAdminEvents}'s javadoc on why a generic matcher is ambiguous there
	 * specifically. Every other position has only one possible type across
	 * {@code RealmResource}'s overloads, so a plain {@code any()} is unambiguous.
	 * {@code w()} (a fresh {@code any()} per call, not a reused value - same reason) and
	 * {@code str()} keep the 12-argument call under the line-length limit.
	 */
	private void verifyNoAdminEventsCall() {
		var mode = verify(this.realmResource, never());
		mode.getAdminEvents(w(), w(), w(), w(), w(), w(), w(), str(), str(), w(), w(), w());
	}

	private static <T> T w() {
		return any();
	}

	private static String str() {
		return anyString();
	}

	@Test
	void countsTheRealmsTotalUsers() {
		when(this.usersResource.count()).thenReturn(37);

		assertThat(this.keycloakUserDirectoryAdapter.countUsers(UserFilter.none())).isEqualTo(37L);
	}

	@Test
	void wrapsAKeycloakFailureWhenListingUsers() {
		when(this.usersResource.list(0, 10)).thenThrow(new RuntimeException("Keycloak unreachable"));

		assertThatThrownBy(() -> this.keycloakUserDirectoryAdapter.findUsers(0, 10, UserFilter.none()))
			.isInstanceOf(KeycloakCommunicationException.class)
			.cause()
			.hasMessage("Keycloak unreachable");
	}

	@Test
	void wrapsAKeycloakFailureWhenCountingUsers() {
		when(this.usersResource.count()).thenThrow(new RuntimeException("Keycloak unreachable"));

		assertThatThrownBy(() -> this.keycloakUserDirectoryAdapter.countUsers(UserFilter.none()))
			.isInstanceOf(KeycloakCommunicationException.class)
			.cause()
			.hasMessage("Keycloak unreachable");
	}

	@Test
	void aQueryFilterNarrowsViaKeycloaksNativeSearch() {
		UserRepresentation nami = userWithUsername(NAMI_ID, "nami");
		when(this.usersResource.search("nam", 0, 500)).thenReturn(List.of(nami));
		mockRoles(NAMI_ID, "EDITOR");

		var filter = new UserFilter("nam", null, null);
		List<User> users = this.keycloakUserDirectoryAdapter.findUsers(0, 10, filter);

		assertThat(users).extracting(User::userId).containsExactly(UUID.fromString(NAMI_ID));
		verify(this.usersResource, never()).list(anyInt(), anyInt());
	}

	@Test
	void aRoleFilterNarrowsViaTheRolesMembershipEndpoint() {
		UserRepresentation luffy = userWithId(LUFFY_ID);
		UserRepresentation nami = userWithId(NAMI_ID);
		var rolesResource = mock(RolesResource.class);
		when(this.realmResource.roles()).thenReturn(rolesResource);
		var roleResource = mock(RoleResource.class);
		when(rolesResource.get("ADMIN")).thenReturn(roleResource);
		when(roleResource.getUserMembers(0, 500)).thenReturn(List.of(luffy));
		mockRoles(LUFFY_ID, "ADMIN");

		var filter = new UserFilter(null, RealmRole.ADMIN, null);
		List<User> users = this.keycloakUserDirectoryAdapter.findUsers(0, 10, filter);

		assertThat(users).extracting(User::userId).containsExactly(UUID.fromString(LUFFY_ID));
		verify(this.usersResource, never()).list(anyInt(), anyInt());
		verify(this.usersResource, never()).search(anyString(), anyInt(), anyInt());
	}

	@Test
	void aStatusFilterFetchesEveryUserUpToTheCapAndFiltersInMemory() {
		UserRepresentation active = userWithId(LUFFY_ID);
		UserRepresentation pending = pendingUserWithId(NAMI_ID);
		when(this.usersResource.list(0, 500)).thenReturn(List.of(active, pending));
		mockRoles(LUFFY_ID, "ADMIN");
		mockRoles(NAMI_ID, "EDITOR");
		mockAdminEvents(NAMI_ID, List.of());

		var filter = new UserFilter(null, null, AccountStatus.PENDING);
		List<User> users = this.keycloakUserDirectoryAdapter.findUsers(0, 10, filter);

		assertThat(users).extracting(User::userId).containsExactly(UUID.fromString(NAMI_ID));
	}

	@Test
	void combinesARoleFilterWithAQueryOnTheNarrowedCandidates() {
		UserRepresentation luffy = userWithUsername(LUFFY_ID, "luffy");
		UserRepresentation nami = userWithUsername(NAMI_ID, "nami");
		var rolesResource = mock(RolesResource.class);
		when(this.realmResource.roles()).thenReturn(rolesResource);
		var roleResource = mock(RoleResource.class);
		when(rolesResource.get("EDITOR")).thenReturn(roleResource);
		when(roleResource.getUserMembers(0, 500)).thenReturn(List.of(luffy, nami));
		mockRoles(LUFFY_ID, "EDITOR");
		mockRoles(NAMI_ID, "EDITOR");

		var filter = new UserFilter("nami", RealmRole.EDITOR, null);
		List<User> users = this.keycloakUserDirectoryAdapter.findUsers(0, 10, filter);

		assertThat(users).extracting(User::userId).containsExactly(UUID.fromString(NAMI_ID));
	}

	@Test
	void countsOnlyTheFilteredMatches() {
		UserRepresentation active = userWithId(LUFFY_ID);
		UserRepresentation pending = pendingUserWithId(NAMI_ID);
		when(this.usersResource.list(0, 500)).thenReturn(List.of(active, pending));
		mockRoles(LUFFY_ID, "ADMIN");
		mockRoles(NAMI_ID, "EDITOR");
		mockAdminEvents(NAMI_ID, List.of());

		var filter = new UserFilter(null, null, AccountStatus.PENDING);
		assertThat(this.keycloakUserDirectoryAdapter.countUsers(filter)).isEqualTo(1L);
	}

	@Test
	void wrapsAKeycloakFailureWhenCreatingTheAccountItself() {
		when(this.usersResource.create(any())).thenThrow(new RuntimeException("Keycloak unreachable"));
		Set<RealmRole> roles = Set.of(RealmRole.EDITOR);

		assertThatThrownBy(() -> this.keycloakUserDirectoryAdapter.inviteUser(INVITED_EMAIL, roles))
			.isInstanceOf(KeycloakCommunicationException.class)
			.hasMessageContaining(INVITED_EMAIL)
			.cause()
			.hasMessage("Keycloak unreachable");
	}

	@Test
	void wrapsAKeycloakFailureWhenRollingBackAnInvitation() {
		var userResource = mock(UserResource.class);
		when(this.usersResource.get(NAMI_ID)).thenReturn(userResource);
		doThrow(new RuntimeException("Keycloak unreachable")).when(userResource).remove();

		assertThatThrownBy(() -> this.keycloakUserDirectoryAdapter.rollbackInvitation(UUID.fromString(NAMI_ID)))
			.isInstanceOf(KeycloakCommunicationException.class)
			.cause()
			.hasMessage("Keycloak unreachable");
	}

	@Test
	void invitesAUserWithNoUsableCredentialAndTheChosenRoles() {
		var response = Response.status(Response.Status.CREATED)
			.location(URI.create("http://keycloak/admin/realms/onepiece/users/" + NAMI_ID))
			.build();
		when(this.usersResource.create(this.newUserCaptor.capture())).thenReturn(response);
		var userResource = mock(UserResource.class);
		when(this.usersResource.get(NAMI_ID)).thenReturn(userResource);
		var roleMappingResource = mock(RoleMappingResource.class);
		var realmRoleScopeResource = mock(RoleScopeResource.class);
		when(userResource.roles()).thenReturn(roleMappingResource);
		when(roleMappingResource.realmLevel()).thenReturn(realmRoleScopeResource);
		var rolesResource = mock(RolesResource.class);
		when(this.realmResource.roles()).thenReturn(rolesResource);
		RoleRepresentation editorRole = mockRoleRepresentation(rolesResource, "EDITOR");
		Set<RealmRole> roles = Set.of(RealmRole.EDITOR);

		User invited = this.keycloakUserDirectoryAdapter.inviteUser(INVITED_EMAIL, roles);

		UserRepresentation createdUser = this.newUserCaptor.getValue();
		assertThat(createdUser.getUsername()).isEqualTo(INVITED_EMAIL);
		assertThat(createdUser.getEmail()).isEqualTo(INVITED_EMAIL);
		assertThat(createdUser.isEnabled()).isTrue();
		assertThat(createdUser.getRequiredActions()).containsExactly("UPDATE_PASSWORD", "UPDATE_PROFILE",
				"VERIFY_EMAIL");
		verify(realmRoleScopeResource).add(List.of(editorRole));
		verify(userResource).executeActionsEmail("onepiece-proxy", "http://localhost:4180/", 43200,
				List.of("UPDATE_PASSWORD", "UPDATE_PROFILE", "VERIFY_EMAIL"));
		assertThat(invited.userId()).isEqualTo(UUID.fromString(NAMI_ID));
		assertThat(invited.email()).isEqualTo(INVITED_EMAIL);
		assertThat(invited.status()).isEqualTo(AccountStatus.PENDING);
		assertThat(invited.createdAt()).isEqualTo(NOW);
		assertThat(invited.roles()).containsExactly("EDITOR");
	}

	@Test
	void rollsBackTheCreatedUserWhenRoleAssignmentFails() {
		var response = Response.status(Response.Status.CREATED)
			.location(URI.create("http://keycloak/admin/realms/onepiece/users/" + NAMI_ID))
			.build();
		when(this.usersResource.create(any())).thenReturn(response);
		var userResource = mock(UserResource.class);
		when(this.usersResource.get(NAMI_ID)).thenReturn(userResource);
		var rolesResource = mock(RolesResource.class);
		when(this.realmResource.roles()).thenReturn(rolesResource);
		when(rolesResource.get("EDITOR")).thenThrow(new RuntimeException("Keycloak unreachable"));
		Set<RealmRole> roles = Set.of(RealmRole.EDITOR);

		assertThatThrownBy(() -> this.keycloakUserDirectoryAdapter.inviteUser(INVITED_EMAIL, roles))
			.isInstanceOf(KeycloakCommunicationException.class)
			.hasMessageContaining(INVITED_EMAIL)
			.cause()
			.hasMessage("Keycloak unreachable");

		verify(userResource).remove();
	}

	@Test
	void rollsBackTheCreatedUserWhenEmailDispatchFails() {
		var response = Response.status(Response.Status.CREATED)
			.location(URI.create("http://keycloak/admin/realms/onepiece/users/" + NAMI_ID))
			.build();
		when(this.usersResource.create(any())).thenReturn(response);
		var userResource = mock(UserResource.class);
		when(this.usersResource.get(NAMI_ID)).thenReturn(userResource);
		var roleMappingResource = mock(RoleMappingResource.class);
		var realmRoleScopeResource = mock(RoleScopeResource.class);
		when(userResource.roles()).thenReturn(roleMappingResource);
		when(roleMappingResource.realmLevel()).thenReturn(realmRoleScopeResource);
		var rolesResource = mock(RolesResource.class);
		when(this.realmResource.roles()).thenReturn(rolesResource);
		mockRoleRepresentation(rolesResource, "EDITOR");
		doThrow(new RuntimeException("550 You can only send testing emails to your own email address"))
			.when(userResource)
			.executeActionsEmail(any(), any(), any(), any());
		Set<RealmRole> roles = Set.of(RealmRole.EDITOR);

		assertThatThrownBy(() -> this.keycloakUserDirectoryAdapter.inviteUser(INVITED_EMAIL, roles))
			.isInstanceOf(EmailDeliveryFailedException.class)
			.hasMessageContaining(INVITED_EMAIL);

		verify(userResource).remove();
	}

	@Test
	void rollbackInvitationRemovesTheKeycloakUser() {
		var userResource = mock(UserResource.class);
		when(this.usersResource.get(NAMI_ID)).thenReturn(userResource);

		this.keycloakUserDirectoryAdapter.rollbackInvitation(UUID.fromString(NAMI_ID));

		verify(userResource).remove();
	}

	@Test
	void refusesToInviteAnAlreadyRegisteredEmail() {
		var response = Response.status(Response.Status.CONFLICT).build();
		when(this.usersResource.create(this.newUserCaptor.capture())).thenReturn(response);
		Set<RealmRole> roles = Set.of(RealmRole.ADMIN);

		assertThatThrownBy(() -> this.keycloakUserDirectoryAdapter.inviteUser(INVITED_EMAIL, roles))
			.isInstanceOf(EmailAlreadyRegisteredException.class);

		verify(this.usersResource, never()).get(any());
	}

	@Test
	void resendsTheInvitationEmailForAnExpiredInvitation() {
		var userResource = mockPendingUser(NAMI_ID);
		mockAdminEvents(NAMI_ID, List.of(adminEventAt(NOW.minus(Duration.ofHours(13)))));

		User result = this.keycloakUserDirectoryAdapter.resendInvitation(UUID.fromString(NAMI_ID));

		verify(userResource).executeActionsEmail("onepiece-proxy", "http://localhost:4180/", 43200,
				List.of("UPDATE_PASSWORD", "UPDATE_PROFILE", "VERIFY_EMAIL"));
		// A fresh link was just sent - the returned status reflects that, not the
		// pre-resend INVITATION_EXPIRED it was gated on.
		assertThat(result.status()).isEqualTo(AccountStatus.PENDING);
	}

	@Test
	void refusesToResendWhileTheCurrentInvitationIsStillValid() {
		var userResource = mockPendingUser(NAMI_ID);
		mockAdminEvents(NAMI_ID, List.of(adminEventAt(NOW.minus(Duration.ofHours(1)))));

		assertThatThrownBy(() -> this.keycloakUserDirectoryAdapter.resendInvitation(UUID.fromString(NAMI_ID)))
			.isInstanceOf(InvitationNotResendableException.class);

		verify(userResource, never()).executeActionsEmail(any(), any(), any(), any());
	}

	@Test
	void refusesToResendForAUserThatDoesNotExist() {
		when(this.usersResource.get(NAMI_ID)).thenThrow(new NotFoundException());

		assertThatThrownBy(() -> this.keycloakUserDirectoryAdapter.resendInvitation(UUID.fromString(NAMI_ID)))
			.isInstanceOf(UserNotFoundException.class);
	}

	@Test
	void refusesToResendForAUserThatIsAlreadyActive() {
		var userResource = mockPendingUser(NAMI_ID);
		UserRepresentation representation = userWithId(NAMI_ID);
		representation.setRequiredActions(List.of());
		when(userResource.toRepresentation()).thenReturn(representation);

		assertThatThrownBy(() -> this.keycloakUserDirectoryAdapter.resendInvitation(UUID.fromString(NAMI_ID)))
			.isInstanceOf(InvitationNotResendableException.class);

		verify(userResource, never()).executeActionsEmail(any(), any(), any(), any());
	}

	@Test
	void raisesEmailDeliveryFailedWhenResendingAnInvitationCannotBeSent() {
		var userResource = mockPendingUser(NAMI_ID);
		mockAdminEvents(NAMI_ID, List.of(adminEventAt(NOW.minus(Duration.ofHours(13)))));
		doThrow(new RuntimeException("550 You can only send testing emails to your own email address"))
			.when(userResource)
			.executeActionsEmail(any(), any(), any(), any());

		assertThatThrownBy(() -> this.keycloakUserDirectoryAdapter.resendInvitation(UUID.fromString(NAMI_ID)))
			.isInstanceOf(EmailDeliveryFailedException.class);
	}

	@Test
	void findsASingleUserDirectlyFromKeycloak() {
		mockActiveUser(LUFFY_ID, "ADMIN");

		User user = this.keycloakUserDirectoryAdapter.findUser(UUID.fromString(LUFFY_ID));

		assertThat(user.userId()).isEqualTo(UUID.fromString(LUFFY_ID));
		assertThat(user.roles()).containsExactly("ADMIN");
	}

	@Test
	void findUserFailsForAnUnknownId() {
		when(this.usersResource.get(NAMI_ID)).thenThrow(new NotFoundException());

		assertThatThrownBy(() -> this.keycloakUserDirectoryAdapter.findUser(UUID.fromString(NAMI_ID)))
			.isInstanceOf(UserNotFoundException.class);
	}

	@Test
	void assignsANewRoleToAnExistingUser() {
		mockActiveUser(NAMI_ID, "EDITOR");
		var rolesResource = mock(RolesResource.class);
		when(this.realmResource.roles()).thenReturn(rolesResource);
		RoleRepresentation adminRole = mockRoleRepresentation(rolesResource, "ADMIN");

		User updated = this.keycloakUserDirectoryAdapter.assignRole(UUID.fromString(NAMI_ID), RealmRole.ADMIN);

		verify(this.namiRoleScope).add(List.of(adminRole));
		assertThat(updated.roles()).containsExactlyInAnyOrder("EDITOR", "ADMIN");
	}

	@Test
	void assigningAnAlreadyHeldRoleIsANoOp() {
		mockActiveUser(NAMI_ID, "EDITOR");

		User updated = this.keycloakUserDirectoryAdapter.assignRole(UUID.fromString(NAMI_ID), RealmRole.EDITOR);

		assertThat(updated.roles()).containsExactly("EDITOR");
		verify(this.realmResource, never()).roles();
	}

	@Test
	void assignRoleFailsForAnUnknownUser() {
		when(this.usersResource.get(NAMI_ID)).thenThrow(new NotFoundException());

		UUID namiId = UUID.fromString(NAMI_ID);
		assertThatThrownBy(() -> this.keycloakUserDirectoryAdapter.assignRole(namiId, RealmRole.ADMIN))
			.isInstanceOf(UserNotFoundException.class);
	}

	@Test
	void revokesARoleFromAUserWithMultipleRoles() {
		mockActiveUser(NAMI_ID, "EDITOR", "REVIEWER");
		var rolesResource = mock(RolesResource.class);
		when(this.realmResource.roles()).thenReturn(rolesResource);
		RoleRepresentation reviewerRole = mockRoleRepresentation(rolesResource, "REVIEWER");

		UUID namiId = UUID.fromString(NAMI_ID);
		User updated = this.keycloakUserDirectoryAdapter.revokeRole(namiId, RealmRole.REVIEWER);

		verify(this.namiRoleScope).remove(List.of(reviewerRole));
		assertThat(updated.roles()).containsExactly("EDITOR");
	}

	@Test
	void revokingAnAbsentRoleIsANoOp() {
		mockActiveUser(NAMI_ID, "EDITOR");

		UUID namiId = UUID.fromString(NAMI_ID);
		User updated = this.keycloakUserDirectoryAdapter.revokeRole(namiId, RealmRole.REVIEWER);

		assertThat(updated.roles()).containsExactly("EDITOR");
		verify(this.realmResource, never()).roles();
	}

	@Test
	void refusesToRemoveTheLastRemainingRoleFromAUser() {
		mockActiveUser(NAMI_ID, "EDITOR");

		UUID namiId = UUID.fromString(NAMI_ID);
		assertThatThrownBy(() -> this.keycloakUserDirectoryAdapter.revokeRole(namiId, RealmRole.EDITOR))
			.isInstanceOf(LastRoleException.class);
	}

	@Test
	void refusesToRemoveAdminWhenNoOtherAdminExists() {
		mockActiveUser(LUFFY_ID, "ADMIN", "EDITOR");
		var rolesResource = mock(RolesResource.class);
		when(this.realmResource.roles()).thenReturn(rolesResource);
		var roleResource = mock(RoleResource.class);
		when(rolesResource.get("ADMIN")).thenReturn(roleResource);
		when(roleResource.getUserMembers(0, 2)).thenReturn(List.of(userWithId(LUFFY_ID)));

		UUID luffyId = UUID.fromString(LUFFY_ID);
		assertThatThrownBy(() -> this.keycloakUserDirectoryAdapter.revokeRole(luffyId, RealmRole.ADMIN))
			.isInstanceOf(LastAdministratorException.class);
	}

	/**
	 * Reproduces a real gap caught by {@code AdminUserListingIntegrationTest} against a
	 * real Keycloak: the seeded bootstrap admin holds only the ADMIN role, so both
	 * UF-IDU-15 ("at least one role") and UF-IDU-16 ("at least one ADMIN") technically
	 * apply - the ADMIN-specific rejection must win, not the generic one.
	 */
	@Test
	void refusesToRemoveTheOnlyRoleWhenItIsAlsoTheLastAdminRole() {
		mockActiveUser(LUFFY_ID, "ADMIN");
		var rolesResource = mock(RolesResource.class);
		when(this.realmResource.roles()).thenReturn(rolesResource);
		var roleResource = mock(RoleResource.class);
		when(rolesResource.get("ADMIN")).thenReturn(roleResource);
		when(roleResource.getUserMembers(0, 2)).thenReturn(List.of(userWithId(LUFFY_ID)));

		UUID luffyId = UUID.fromString(LUFFY_ID);
		assertThatThrownBy(() -> this.keycloakUserDirectoryAdapter.revokeRole(luffyId, RealmRole.ADMIN))
			.isInstanceOf(LastAdministratorException.class);
	}

	@Test
	void allowsRemovingAdminWhenAnotherAdminExists() {
		mockActiveUser(LUFFY_ID, "ADMIN", "EDITOR");
		var rolesResource = mock(RolesResource.class);
		when(this.realmResource.roles()).thenReturn(rolesResource);
		var roleResource = mock(RoleResource.class);
		when(rolesResource.get("ADMIN")).thenReturn(roleResource);
		when(roleResource.getUserMembers(0, 2)).thenReturn(List.of(userWithId(LUFFY_ID), userWithId(NAMI_ID)));
		RoleRepresentation adminRole = new RoleRepresentation("ADMIN", null, false);
		when(roleResource.toRepresentation()).thenReturn(adminRole);

		User updated = this.keycloakUserDirectoryAdapter.revokeRole(UUID.fromString(LUFFY_ID), RealmRole.ADMIN);

		verify(this.luffyRoleScope).remove(List.of(adminRole));
		assertThat(updated.roles()).containsExactly("EDITOR");
	}

	@Test
	void revokeRoleFailsForAnUnknownUser() {
		when(this.usersResource.get(NAMI_ID)).thenThrow(new NotFoundException());

		UUID namiId = UUID.fromString(NAMI_ID);
		assertThatThrownBy(() -> this.keycloakUserDirectoryAdapter.revokeRole(namiId, RealmRole.EDITOR))
			.isInstanceOf(UserNotFoundException.class);
	}

	@Test
	void revokesAccessAndLogsOutTheAccount() {
		var userResource = mockActiveUser(NAMI_ID, "EDITOR");

		User updated = this.keycloakUserDirectoryAdapter.revokeAccess(UUID.fromString(NAMI_ID));

		var representationCaptor = ArgumentCaptor.forClass(UserRepresentation.class);
		verify(userResource).update(representationCaptor.capture());
		assertThat(representationCaptor.getValue().isEnabled()).isFalse();
		verify(userResource).logout();
		assertThat(updated.status()).isEqualTo(AccountStatus.DISABLED);
	}

	@Test
	void revokingAnAlreadyDisabledAccountIsANoOp() {
		var userResource = mockActiveUser(NAMI_ID, "EDITOR");
		UserRepresentation disabled = userWithId(NAMI_ID);
		disabled.setEnabled(false);
		when(userResource.toRepresentation()).thenReturn(disabled);

		User updated = this.keycloakUserDirectoryAdapter.revokeAccess(UUID.fromString(NAMI_ID));

		assertThat(updated.status()).isEqualTo(AccountStatus.DISABLED);
		verify(userResource, never()).update(any());
		verify(userResource, never()).logout();
	}

	@Test
	void refusesToRevokeAccessFromTheOnlyAdministrator() {
		mockActiveUser(LUFFY_ID, "ADMIN");
		var rolesResource = mock(RolesResource.class);
		when(this.realmResource.roles()).thenReturn(rolesResource);
		var roleResource = mock(RoleResource.class);
		when(rolesResource.get("ADMIN")).thenReturn(roleResource);
		when(roleResource.getUserMembers(0, 2)).thenReturn(List.of(userWithId(LUFFY_ID)));

		assertThatThrownBy(() -> this.keycloakUserDirectoryAdapter.revokeAccess(UUID.fromString(LUFFY_ID)))
			.isInstanceOf(LastAdministratorException.class);
	}

	@Test
	void allowsRevokingAccessFromAnAdministratorWhenAnotherAdminExists() {
		var userResource = mockActiveUser(LUFFY_ID, "ADMIN");
		var rolesResource = mock(RolesResource.class);
		when(this.realmResource.roles()).thenReturn(rolesResource);
		var roleResource = mock(RoleResource.class);
		when(rolesResource.get("ADMIN")).thenReturn(roleResource);
		when(roleResource.getUserMembers(0, 2)).thenReturn(List.of(userWithId(LUFFY_ID), userWithId(NAMI_ID)));

		User updated = this.keycloakUserDirectoryAdapter.revokeAccess(UUID.fromString(LUFFY_ID));

		assertThat(updated.status()).isEqualTo(AccountStatus.DISABLED);
		verify(userResource).logout();
	}

	@Test
	void revokeAccessFailsForAnUnknownUser() {
		when(this.usersResource.get(NAMI_ID)).thenThrow(new NotFoundException());

		assertThatThrownBy(() -> this.keycloakUserDirectoryAdapter.revokeAccess(UUID.fromString(NAMI_ID)))
			.isInstanceOf(UserNotFoundException.class);
	}

	@Test
	void reactivatesADisabledAccount() {
		var userResource = mock(UserResource.class);
		when(this.usersResource.get(NAMI_ID)).thenReturn(userResource);
		UserRepresentation disabled = userWithId(NAMI_ID);
		disabled.setEnabled(false);
		// toRepresentation() is fetched fresh once per call (loadUser's initial status
		// check, setEnabled's own read-modify-write, and the reload afterwards) rather
		// than a single instance reused throughout - the last stubbed value is repeated
		// for every call from the third onwards, carrying setEnabled's mutation into the
		// reload that determines the returned status.
		when(userResource.toRepresentation()).thenReturn(disabled, userWithId(NAMI_ID));
		var roleMappingResource = mock(RoleMappingResource.class);
		var realmRoleScopeResource = mock(RoleScopeResource.class);
		when(userResource.roles()).thenReturn(roleMappingResource);
		when(roleMappingResource.realmLevel()).thenReturn(realmRoleScopeResource);
		when(realmRoleScopeResource.listAll()).thenReturn(List.of());

		User updated = this.keycloakUserDirectoryAdapter.reactivate(UUID.fromString(NAMI_ID));

		var representationCaptor = ArgumentCaptor.forClass(UserRepresentation.class);
		verify(userResource).update(representationCaptor.capture());
		assertThat(representationCaptor.getValue().isEnabled()).isTrue();
		assertThat(updated.status()).isEqualTo(AccountStatus.ACTIVE);
	}

	@Test
	void reactivatingAnAlreadyActiveAccountIsANoOp() {
		var userResource = mockActiveUser(NAMI_ID, "EDITOR");

		User updated = this.keycloakUserDirectoryAdapter.reactivate(UUID.fromString(NAMI_ID));

		assertThat(updated.status()).isEqualTo(AccountStatus.ACTIVE);
		verify(userResource, never()).update(any());
	}

	@Test
	void reactivateFailsForAnUnknownUser() {
		when(this.usersResource.get(NAMI_ID)).thenThrow(new NotFoundException());

		assertThatThrownBy(() -> this.keycloakUserDirectoryAdapter.reactivate(UUID.fromString(NAMI_ID)))
			.isInstanceOf(UserNotFoundException.class);
	}

	@Test
	void listsEachRealmRolesClientRoleCompositesAsItsPermissions() {
		var rolesResource = mock(RolesResource.class);
		when(this.realmResource.roles()).thenReturn(rolesResource);
		mockClientRoleComposites(rolesResource, "ADMIN", "users:read", "audit:read");
		mockClientRoleComposites(rolesResource, "REVIEWER", "docs:read", "docs:review");
		mockClientRoleComposites(rolesResource, "EDITOR", "docs:read", "docs:write");

		Map<RealmRole, List<String>> permissions = this.keycloakUserDirectoryAdapter.listRolePermissions();

		assertThat(permissions).containsEntry(RealmRole.ADMIN, List.of("audit:read", "users:read"))
			.containsEntry(RealmRole.REVIEWER, List.of("docs:read", "docs:review"))
			.containsEntry(RealmRole.EDITOR, List.of("docs:read", "docs:write"));
	}

	@Test
	void ignoresARealmRoleCompositeWhenListingPermissions() {
		var rolesResource = mock(RolesResource.class);
		when(this.realmResource.roles()).thenReturn(rolesResource);
		var roleResource = mock(RoleResource.class);
		when(rolesResource.get(anyString())).thenReturn(roleResource);
		var realmComposite = new RoleRepresentation("default-roles-onepiece", null, true);
		realmComposite.setClientRole(false);
		when(roleResource.getRoleComposites()).thenReturn(Set.of(realmComposite));

		Map<RealmRole, List<String>> permissions = this.keycloakUserDirectoryAdapter.listRolePermissions();

		assertThat(permissions.get(RealmRole.ADMIN)).isEmpty();
	}

	/**
	 * Stubs a full {@code UsersResource.get(keycloakId)} chain for an ACTIVE user holding
	 * exactly {@code roleNames} - {@code listAll()} for the role fetch every
	 * {@code loadUser} call makes, and {@code realmLevel()} itself exposed via
	 * {@link #namiRoleScope}/{@link #luffyRoleScope} so a test can verify the
	 * {@code add}/{@code remove} call made against that same mock, rather than a second,
	 * unstubbed one.
	 */
	private UserResource mockActiveUser(String keycloakId, String... roleNames) {
		var userResource = mock(UserResource.class);
		when(this.usersResource.get(keycloakId)).thenReturn(userResource);
		when(userResource.toRepresentation()).thenReturn(userWithId(keycloakId));
		var roleMappingResource = mock(RoleMappingResource.class);
		var realmRoleScopeResource = mock(RoleScopeResource.class);
		when(userResource.roles()).thenReturn(roleMappingResource);
		when(roleMappingResource.realmLevel()).thenReturn(realmRoleScopeResource);
		List<RoleRepresentation> roles = List.of(roleNames)
			.stream()
			.map(name -> new RoleRepresentation(name, null, false))
			.toList();
		when(realmRoleScopeResource.listAll()).thenReturn(roles);
		if (keycloakId.equals(NAMI_ID)) {
			this.namiRoleScope = realmRoleScopeResource;
		}
		else {
			this.luffyRoleScope = realmRoleScopeResource;
		}
		return userResource;
	}

	private UserResource mockPendingUser(String keycloakId) {
		var userResource = mock(UserResource.class);
		when(this.usersResource.get(keycloakId)).thenReturn(userResource);
		when(userResource.toRepresentation()).thenReturn(pendingUserWithId(keycloakId));
		var roleMappingResource = mock(RoleMappingResource.class);
		var realmRoleScopeResource = mock(RoleScopeResource.class);
		when(userResource.roles()).thenReturn(roleMappingResource);
		when(roleMappingResource.realmLevel()).thenReturn(realmRoleScopeResource);
		when(realmRoleScopeResource.listAll()).thenReturn(List.of());
		return userResource;
	}

	/**
	 * Stubs the admin-events lookup {@code isInvitationExpired} performs for
	 * {@code keycloakId}. {@code <String>isNull()} at the {@code dateFrom}/{@code dateTo}
	 * positions pins the overload with {@code String} there - {@code RealmResource} also
	 * has one taking {@code long}/{@code long}, and a bare {@code isNull()} would be
	 * ambiguous between the two. Each matcher is called inline, right where its value is
	 * used - Mockito registers a matcher when the method producing it runs, not where its
	 * (always {@code null}) return value ends up, so precomputing any of these into a
	 * local variable beforehand would register it too early and misalign every matcher
	 * after it.
	 */
	private void mockAdminEvents(String keycloakId, List<AdminEventRepresentation> events) {
		String resourcePath = "users/" + keycloakId + "/execute-actions-email";
		when(this.realmResource.getAdminEvents(eq(List.of("ACTION")), isNull(), isNull(), isNull(), isNull(),
				eq(resourcePath), isNull(), noDate(), noDate(), eq(0), eq(1), eq("desc")))
			.thenReturn(events);
	}

	/**
	 * Inline shorthand for {@code ArgumentMatchers.<String>isNull()} - must still be
	 * called fresh at each {@code dateFrom}/{@code dateTo} position (see
	 * {@link #mockAdminEvents}'s javadoc), just under a shorter name.
	 */
	private static String noDate() {
		return ArgumentMatchers.<String>isNull();
	}

	private static AdminEventRepresentation adminEventAt(Instant time) {
		var event = new AdminEventRepresentation();
		event.setTime(time.toEpochMilli());
		return event;
	}

	private static UserRepresentation pendingUserWithId(String keycloakId) {
		UserRepresentation user = userWithId(keycloakId);
		user.setRequiredActions(List.of("UPDATE_PASSWORD", "UPDATE_PROFILE", "VERIFY_EMAIL"));
		return user;
	}

	private RoleRepresentation mockRoleRepresentation(RolesResource rolesResource, String roleName) {
		var roleResource = mock(RoleResource.class);
		var representation = new RoleRepresentation(roleName, null, false);
		when(rolesResource.get(roleName)).thenReturn(roleResource);
		when(roleResource.toRepresentation()).thenReturn(representation);
		return representation;
	}

	private void mockClientRoleComposites(RolesResource rolesResource, String roleName, String... permissionNames) {
		var roleResource = mock(RoleResource.class);
		when(rolesResource.get(roleName)).thenReturn(roleResource);
		Set<RoleRepresentation> composites = Arrays.stream(permissionNames).map(name -> {
			var representation = new RoleRepresentation(name, null, false);
			representation.setClientRole(true);
			return representation;
		}).collect(Collectors.toSet());
		when(roleResource.getRoleComposites()).thenReturn(composites);
	}

	private void mockRoles(String keycloakId, String... roleNames) {
		var userResource = mock(UserResource.class);
		var roleMappingResource = mock(RoleMappingResource.class);
		var realmRoleScopeResource = mock(RoleScopeResource.class);
		when(this.usersResource.get(keycloakId)).thenReturn(userResource);
		when(userResource.roles()).thenReturn(roleMappingResource);
		when(roleMappingResource.realmLevel()).thenReturn(realmRoleScopeResource);
		List<RoleRepresentation> roles = List.of(roleNames)
			.stream()
			.map(name -> new RoleRepresentation(name, null, false))
			.toList();
		when(realmRoleScopeResource.listAll()).thenReturn(roles);
	}

	private static UserRepresentation userWithId(String keycloakId) {
		UserRepresentation user = new UserRepresentation();
		user.setId(keycloakId);
		user.setEmail(keycloakId + "@onepiece.local");
		user.setEnabled(true);
		return user;
	}

	/**
	 * Like {@link #userWithId}, but with a real username - needed wherever a query filter
	 * matches on it.
	 */
	private static UserRepresentation userWithUsername(String keycloakId, String username) {
		UserRepresentation user = userWithId(keycloakId);
		user.setUsername(username);
		user.setEmail(username + "@onepiece.local");
		return user;
	}

}
