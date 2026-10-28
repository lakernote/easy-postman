package com.laker.postman.request.edit;

import com.laker.postman.request.model.AuthType;
import com.laker.postman.request.model.HttpFormData;
import com.laker.postman.request.model.HttpFormUrlencoded;
import com.laker.postman.request.model.HttpHeader;
import com.laker.postman.request.model.HttpParam;
import com.laker.postman.request.model.HttpRequestProxyPolicy;
import com.laker.postman.request.model.HttpRequestVersions;
import com.laker.postman.request.model.RequestBodyTypes;
import com.laker.postman.request.model.RequestItemProtocolEnum;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class HttpRequestEditorContentSummaryTest {

    @Test
    public void shouldDetectParamsHeadersAuthSettingsAndScripts() {
        HttpRequestEditorDraft draft = HttpRequestEditorDraft.builder()
                .pathVariables(List.of(new HttpParam(true, "userId", "42")))
                .params(List.of(new HttpParam(true, "q", "easy")))
                .headers(List.of(new HttpHeader(true, "Accept", "*/*")))
                .authType(AuthType.BEARER.getConstant())
                .httpVersion(HttpRequestVersions.HTTP_2)
                .prescript("pm.variables.set('x', '1')")
                .build();

        HttpRequestEditorContentSummary summary = HttpRequestEditorContentSummary.from(draft);

        assertTrue(summary.isHasParams());
        assertTrue(summary.isHasHeaders());
        assertTrue(summary.isHasAuth());
        assertTrue(summary.isHasSettings());
        assertTrue(summary.isHasScripts());
    }

    @Test
    public void shouldDetectPathVariablesAsParamsContent() {
        HttpRequestEditorDraft draft = HttpRequestEditorDraft.builder()
                .pathVariables(List.of(new HttpParam(true, "userId", "42")))
                .build();

        HttpRequestEditorContentSummary summary = HttpRequestEditorContentSummary.from(draft);

        assertTrue(summary.isHasParams());
    }

    @Test
    public void shouldTreatInheritedDefaultsAsNoAuthOrSettingsContent() {
        HttpRequestEditorDraft draft = HttpRequestEditorDraft.builder()
                .authType(AuthType.INHERIT.getConstant())
                .cookieJarEnabled(Boolean.TRUE)
                .proxyPolicy(HttpRequestProxyPolicy.DEFAULT)
                .httpVersion(HttpRequestVersions.AUTO)
                .build();

        HttpRequestEditorContentSummary summary = HttpRequestEditorContentSummary.from(draft);

        assertFalse(summary.isHasAuth());
        assertFalse(summary.isHasSettings());
    }

    @Test
    public void shouldDetectExplicitProxyPolicyAsSettingsContent() {
        HttpRequestEditorDraft draft = HttpRequestEditorDraft.builder()
                .proxyPolicy(HttpRequestProxyPolicy.NO_PROXY)
                .build();

        HttpRequestEditorContentSummary summary = HttpRequestEditorContentSummary.from(draft);

        assertTrue(summary.isHasSettings());
    }

    @Test
    public void shouldDetectBodyContentBySelectedBodyTypeForBodyContentProtocols() {
        HttpRequestEditorDraft rawDraft = HttpRequestEditorDraft.builder()
                .protocol(RequestItemProtocolEnum.HTTP)
                .bodyType(RequestBodyTypes.BODY_TYPE_RAW)
                .body(" { } ")
                .build();
        HttpRequestEditorDraft formDraft = HttpRequestEditorDraft.builder()
                .protocol(RequestItemProtocolEnum.HTTP)
                .bodyType(RequestBodyTypes.BODY_TYPE_FORM_DATA)
                .formData(List.of(new HttpFormData(true, "file", HttpFormData.TYPE_FILE, "/tmp/a.txt")))
                .build();
        HttpRequestEditorDraft urlencodedDraft = HttpRequestEditorDraft.builder()
                .protocol(RequestItemProtocolEnum.HTTP)
                .bodyType(RequestBodyTypes.BODY_TYPE_FORM_URLENCODED)
                .urlencoded(List.of(new HttpFormUrlencoded(true, "name", "easy")))
                .build();
        HttpRequestEditorDraft websocketDraft = HttpRequestEditorDraft.builder()
                .protocol(RequestItemProtocolEnum.WEBSOCKET)
                .bodyType(RequestBodyTypes.BODY_TYPE_RAW)
                .body("hello")
                .build();
        HttpRequestEditorDraft sseDraft = HttpRequestEditorDraft.builder()
                .protocol(RequestItemProtocolEnum.HTTP)
                .bodyType(RequestBodyTypes.BODY_TYPE_RAW)
                .body(" {\"stream\":true} ")
                .build();
        HttpRequestEditorDraft savedResponseDraft = HttpRequestEditorDraft.builder()
                .protocol(RequestItemProtocolEnum.SAVED_RESPONSE)
                .bodyType(RequestBodyTypes.BODY_TYPE_RAW)
                .body("ignored")
                .build();

        assertTrue(HttpRequestEditorContentSummary.from(rawDraft).isHasBody());
        assertTrue(HttpRequestEditorContentSummary.from(formDraft).isHasBody());
        assertTrue(HttpRequestEditorContentSummary.from(urlencodedDraft).isHasBody());
        assertTrue(HttpRequestEditorContentSummary.from(sseDraft).isHasBody());
        assertTrue(HttpRequestEditorContentSummary.from(websocketDraft).isHasBody());
        assertFalse(HttpRequestEditorContentSummary.from(savedResponseDraft).isHasBody());
    }
}
