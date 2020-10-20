package org.nantipov.huntersecretary.services;

import com.auth0.jwt.JWT;
import com.google.api.client.auth.oauth2.TokenRequest;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeRequestUrl;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleRefreshTokenRequest;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.nantipov.huntersecretary.domain.entiry.RegisteredUser;
import org.nantipov.huntersecretary.repository.RegisteredUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.StreamSupport;
import javax.annotation.PostConstruct;

import static com.google.common.base.Strings.isNullOrEmpty;

@Slf4j
@Service
public class GoogleAuthService {

    private final Scopes scopesConfiguration;
    private final TaskScheduler taskScheduler;
    private final TaskExecutor taskExecutor;
    private final HttpTransport httpTransport;
    private final JsonFactory jsonFactory;
    private final RegisteredUserRepository registeredUserRepository;

    private final GoogleClientSecrets googleClientSecrets;

    @Autowired
    public GoogleAuthService(Scopes scopesConfiguration, TaskScheduler taskScheduler,
                             TaskExecutor taskExecutor, HttpTransport httpTransport,
                             JsonFactory jsonFactory,
                             RegisteredUserRepository registeredUserRepository,
                             @Value("${hunter-secretary.google.client-secret-file}")
                                     Path clientSecretFile) throws IOException {
        this.scopesConfiguration = scopesConfiguration;
        this.taskScheduler = taskScheduler;
        this.taskExecutor = taskExecutor;
        this.httpTransport = httpTransport;
        this.jsonFactory = jsonFactory;
        this.registeredUserRepository = registeredUserRepository;
        this.googleClientSecrets = GoogleClientSecrets.load(jsonFactory, Files.newBufferedReader(clientSecretFile));
    }

    @PostConstruct
    public void loadSavedTokens() {
        loadPreSavedToken();
        scheduleTokensRefresh();
    }

    public void applyAuthCode(String authCode) {
        taskExecutor.execute(() -> obtainTokenByAuthCode(authCode));
    }

    private void obtainTokenByAuthCode(String authCode) {
        log.debug("Obtaining Google token by auth code");
        var request = new GoogleAuthorizationCodeTokenRequest(
                httpTransport,
                jsonFactory,
                googleClientSecrets.getDetails().getClientId(),
                googleClientSecrets.getDetails().getClientSecret(),
                authCode,
                googleClientSecrets.getDetails().getRedirectUris().iterator().next()
        );
        request.setGrantType("authorization_code");
        retrieveToken(request);
    }

    private void retrieveToken(TokenRequest request) {
        try {
            log.debug("Retrieving Google token");
            var tokenResponse = request.execute();
            storeToken(tokenResponse,
                       LocalDateTime.now().plus(tokenResponse.getExpiresInSeconds(), ChronoUnit.SECONDS)
            );
            if (!isNullOrEmpty(tokenResponse.getRefreshToken())) {
                scheduleRefresh(tokenResponse);
            }
        } catch (IOException e) {
            log.error("Could not retrieve Google token", e);
        }
    }

    private void storeToken(TokenResponse tokenResponse, LocalDateTime expiresIn) {
        log.debug("Token {}", tokenResponse);
        var idToken = tokenResponse.get("id_token");
        if (idToken == null) {
            return;
        }
        var jwt = JWT.decode((String) idToken);
        log.debug("JWT {}", jwt.getClaims().toString());
        var emailClaim = jwt.getClaim("email");
        var email = emailClaim.asString();
        if (email == null) {
            return;
        }
        email = email.toLowerCase();
        var user =
                registeredUserRepository.findByEmailAddress(email)
                                        .orElseGet(RegisteredUser::new);
        user.setEmailAddress(email);
        user.setTokenResponse(tokenResponse.toString());
        user.setExpiresIn(expiresIn);
        user = registeredUserRepository.save(user);
        log.debug("User {}", user);
    }

    public String getGoogleAuthURL() {
        var url = new GoogleAuthorizationCodeRequestUrl(
                googleClientSecrets,
                googleClientSecrets.getDetails().getRedirectUris().iterator().next(),
                scopesConfiguration.getScopes()
        );
        url.setAccessType("offline");
        url.set("prompt", "consent");
        return url.toString();
    }

    private void refreshToken(String refreshToken) {
        log.debug("Refreshing Google token");
        var refreshRequest = new GoogleRefreshTokenRequest(
                httpTransport, jsonFactory, refreshToken,
                googleClientSecrets.getDetails().getClientId(),
                googleClientSecrets.getDetails().getClientSecret()
        );
        retrieveToken(refreshRequest);
    }

    public TokenResponse getTokenResponse(String tokenResponseString) {
        try {
            return jsonFactory.fromString(tokenResponseString, TokenResponse.class);
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not parse token response string", e);
        }
    }

    private void loadPreSavedToken() {
        try {
            var reader = Files.newBufferedReader(Paths.get("token-response.json"));
            var tokenResponse = jsonFactory.fromReader(reader, TokenResponse.class);
            storeToken(tokenResponse, LocalDateTime.now().plus(10, ChronoUnit.MINUTES));
        } catch (IOException e) {
            log.warn("Could not load pre-saved token");
        }
    }

    private void scheduleTokensRefresh() {
        StreamSupport.stream(registeredUserRepository.findAll().spliterator(), false)
                     .map(RegisteredUser::getTokenResponse)
                     .map(this::getTokenResponse)
                     .filter(token -> !isNullOrEmpty(token.getRefreshToken()))
                     .forEach(this::scheduleRefresh);
    }

    private void scheduleRefresh(TokenResponse tokenResponse) {
        var scheduleAt = Instant.now()
                                .plus(tokenResponse.getExpiresInSeconds(), ChronoUnit.SECONDS)
                                .minus(2, ChronoUnit.MINUTES);
        log.debug("Schedule token refresh at {}", scheduleAt.toString());
        taskScheduler.schedule(() -> refreshToken(tokenResponse.getRefreshToken()), scheduleAt);
    }

    @Data
    @ConfigurationProperties(prefix = "hunter-secretary.google")
    public static class Scopes {
        List<String> scopes = new ArrayList<>();
    }
}
