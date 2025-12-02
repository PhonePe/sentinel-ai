package com.phonepe.sentinel.session.history;

import java.util.Optional;

public interface HistoryStore {

    Optional<History> history(String sessionId);

    Optional<History> saveHistory(History history);
}
