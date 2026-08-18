package com.laker.postman.mock.app;

import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;

public record MockCollectionChoice(String id, String name, int requestCount, int configuredResponseCount) {
    @Override
    public String toString() {
        return name + "  " + I18nUtil.getMessage(
                MessageKeys.MOCK_SERVER_COLLECTION_SUMMARY, requestCount, configuredResponseCount);
    }
}
