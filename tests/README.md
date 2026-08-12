# Tests

The real automated test suite is in `src/test/java` so Maven/Spring Boot discovers it normally.

This folder contains JSON fixtures plus `api-smoke.sh` for a running server.

Run automated JUnit tests:

```bash
./scripts/test-all.sh
```

Run the app in another terminal, then run the API smoke scenario:

```bash
./scripts/run.sh
./tests/api-smoke.sh
```
