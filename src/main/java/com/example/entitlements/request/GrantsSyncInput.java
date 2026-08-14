package com.example.entitlements.request;

import java.util.List;

public record GrantsSyncInput(
        SyncMode mode,
        List<GrantInput> grants
) {}
