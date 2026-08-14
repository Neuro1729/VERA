package com.example.entitlements.request;

public record CompanySignupRequest(
        AdminRegistrationInput admin,
        CompanyRegistrationRequest registration
) {}
