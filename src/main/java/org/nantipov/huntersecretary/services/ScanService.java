package org.nantipov.huntersecretary.services;

import com.google.api.client.auth.oauth2.TokenResponse;
import lombok.extern.slf4j.Slf4j;
import org.nantipov.huntersecretary.domain.entiry.RegisteredUser;
import org.nantipov.huntersecretary.repository.RegisteredUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.stream.StreamSupport;

@Slf4j
@Service
public class ScanService {

    private final RegisteredUserRepository registeredUserRepository;
    private final GoogleAuthService googleAuthService;
    private final GmailService gmailService;

    @Autowired
    public ScanService(RegisteredUserRepository registeredUserRepository,
                       GoogleAuthService googleAuthService,
                       GmailService gmailService) {
        this.registeredUserRepository = registeredUserRepository;
        this.googleAuthService = googleAuthService;
        this.gmailService = gmailService;
    }

    @Scheduled(fixedDelayString = "${hunter-secretary.scanner.period}")
    public void scan() {
        StreamSupport.stream(registeredUserRepository.findAll().spliterator(), false)
                     .filter(user ->
                                     Objects.nonNull(user.getTokenResponse()) &&
                                     LocalDateTime.now().isBefore(user.getExpiresIn())
                     )
                     .map(RegisteredUser::getTokenResponse)
                     .map(googleAuthService::getTokenResponse)
                     .map(TokenResponse::getAccessToken)
                     .forEach(gmailService::processMail);
    }
}
