package com.laker.postman.service.collections;

import com.laker.postman.collection.model.CollectionDocument;
import com.laker.postman.collection.model.CollectionNode;
import com.laker.postman.collection.model.RequestGroup;
import com.laker.postman.http.request.HttpRequestFactory;
import com.laker.postman.request.model.HttpFormData;
import com.laker.postman.request.model.HttpFormUrlencoded;
import com.laker.postman.request.model.HttpHeader;
import com.laker.postman.request.model.HttpParam;
import com.laker.postman.request.model.HttpRequestItem;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.List;

import static com.laker.postman.request.defaults.HttpRequestDefaults.APPLICATION_JSON;
import static com.laker.postman.request.defaults.HttpRequestDefaults.CONTENT_TYPE;
import static com.laker.postman.request.model.RequestBodyTypes.BODY_TYPE_RAW;

@UtilityClass
public class DefaultCollectionDocumentFactory {
    private static final String BASIC_HTTP_EXAMPLES_GROUP_NAME = "Basic HTTP Examples";
    private static final String SCRIPT_EXAMPLES_EN_GROUP_NAME = "Script Examples (EN)";
    private static final String SCRIPT_EXAMPLES_ZH_GROUP_NAME = "脚本示例（中文）";

    public static final String HTTPBIN_BASE_URL = "https://httpbin.org";
    public static final String HTTPBIN_POST_URL = HTTPBIN_BASE_URL + "/post";

    public CollectionDocument create() {
        // 默认集合是领域数据，不直接创建 Swing 节点；这样 CLI、导入导出和未来 JavaFX 都能复用同一份示例数据。
        return new CollectionDocument(List.of(
                createBasicHttpExamples(),
                createEnglishScriptExamples(),
                createChineseScriptExamples()
        ));
    }

    private CollectionNode createEnglishScriptExamples() {
        RequestGroup group = new RequestGroup(SCRIPT_EXAMPLES_EN_GROUP_NAME);
        group.setDescription("""
                Run requests 1 to 6 in order for the variable workflow examples.
                Requests 7 and 8 are stream-oriented checks for WebSocket and SSE callback scripts.
                Run request 1 before requests 7 and 8 so they can reuse the prepared environment values.
                
                Request 1 sets environment variables in script.
                Request 2 reuses them in query params, headers, body, and test script.
                Request 2 also creates execution-scoped variables for the current run.
                Request 3 creates an orderId for the current run and stores it in pm.variables.
                Request 4 reuses that orderId across params, headers, URL path, and test script.
                Request 5 extracts an orderId from the response body and stores it in pm.variables.
                Request 6 reuses that extracted orderId in the next request.
                Requests 7 and 8 verify that both pm.environment and pm.variables remain available in WebSocket and SSE callbacks.
                
                Scope:
                - pm.environment: shared across requests and can be reused later
                - pm.variables: shared within the current run context and reset after the run ends
                - pm.iterationData: current CSV/JSON row in a data-driven run
                - Authorization priority: script-defined Authorization > existing Authorization header > auth tab generated Authorization
                - Removing Authorization in pre-script does not disable auth tab; if no Authorization remains, the auth tab configuration is applied at send time
                """);
        CollectionNode groupNode = CollectionNode.group(group);

        HttpRequestItem setup = HttpRequestFactory.createDefaultRequest();
        setup.setName("1. Setup Variables");
        setup.setDescription("Creates shared environment variables and one execution-scoped variable in the pre-request script.");
        setup.setMethod("GET");
        setup.setUrl("{{baseUrl}}/anything/setup");
        setup.setHeadersList(withExtraHeaders(setup,
                new HttpHeader(true, "X-Setup-Trace", "{{setupTraceId}}")));
        setup.setParamsList(List.of(
                new HttpParam(true, "tenant", "{{tenantId}}")
        ));
        setup.setPrescript("""
                pm.environment.set('baseUrl', 'https://httpbin.org');
                pm.environment.set('tenantId', 'team-alpha');
                pm.environment.set('userId', 'user-1001');
                pm.environment.set('keyword', 'easy-postman');
                
                pm.variables.set('setupTraceId', 'setup-' + Date.now());
                console.log('Environment ready:', JSON.stringify(pm.environment.toObject()));
                """);
        setup.setPostscript("""
                var jsonData = pm.response.json();
                pm.test('Setup request is successful', function () {
                    pm.response.to.have.status(200);
                    pm.expect(jsonData.args.tenant).to.eql(pm.environment.get('tenantId'));
                });
                """);
        addRequestChild(groupNode, setup);

        HttpRequestItem reuse = HttpRequestFactory.createDefaultRequest();
        reuse.setName("2. Reuse Variables");
        reuse.setDescription("Reuses environment variables in params, headers, body, and script. Execution-scoped variables are created in this run and reused immediately.");
        reuse.setMethod("POST");
        reuse.setUrl(HTTPBIN_POST_URL);
        reuse.setBodyType(BODY_TYPE_RAW);
        reuse.setHeadersList(withExtraHeaders(reuse,
                new HttpHeader(true, CONTENT_TYPE, APPLICATION_JSON),
                new HttpHeader(true, "X-Tenant-Id", "{{tenantId}}"),
                new HttpHeader(true, "X-Trace-Id", "{{traceId}}")));
        reuse.setParamsList(List.of(
                new HttpParam(true, "keyword", "{{keyword}}"),
                new HttpParam(true, "trace", "{{traceId}}")
        ));
        reuse.setBody("""
                {
                  "tenantId": "{{tenantId}}",
                  "userId": "{{userId}}",
                  "traceId": "{{traceId}}",
                  "note": "{{note}}"
                }""");
        reuse.setPrescript("""
                pm.variables.set('traceId', 'trace-' + Date.now());
                pm.variables.set('note', 'execution variables work in body, header, and params');
                
                pm.request.headers.upsert({
                    key: 'X-Script-Flag',
                    value: 'ready'
                });
                """);
        reuse.setPostscript("""
                var jsonData = pm.response.json();
                pm.test('Reuse request is successful', function () {
                    pm.response.to.have.status(200);
                });
                pm.test('Environment variables are reused', function () {
                    pm.expect(jsonData.json.tenantId).to.eql(pm.environment.get('tenantId'));
                    pm.expect(jsonData.json.userId).to.eql(pm.environment.get('userId'));
                    pm.expect(jsonData.args.keyword).to.eql(pm.environment.get('keyword'));
                });
                pm.test('Execution variables are reused in the current run', function () {
                    pm.expect(jsonData.args.trace).to.eql(pm.variables.get('traceId'));
                    pm.expect(jsonData.json.traceId).to.eql(pm.variables.get('traceId'));
                    pm.expect(jsonData.json.note).to.eql(pm.variables.get('note'));
                    pm.expect(jsonData.headers['X-Trace-Id']).to.eql(pm.variables.get('traceId'));
                });
                console.log('traceId =', pm.variables.get('traceId'));
                """);
        addRequestChild(groupNode, reuse);

        HttpRequestItem createOrder = HttpRequestFactory.createDefaultRequest();
        createOrder.setName("3. Create Order Context");
        createOrder.setDescription("Generates or accepts an orderId for the current run, echoes it through httpbin, and stores it in pm.variables for the next request.");
        createOrder.setMethod("POST");
        createOrder.setUrl(HTTPBIN_POST_URL);
        createOrder.setBodyType(BODY_TYPE_RAW);
        createOrder.setHeadersList(withExtraHeaders(createOrder,
                new HttpHeader(true, CONTENT_TYPE, APPLICATION_JSON),
                new HttpHeader(true, "X-Order-Id", "{{pendingOrderId}}")));
        createOrder.setParamsList(List.of(
                new HttpParam(true, "stage", "create-order"),
                new HttpParam(true, "pendingOrderId", "{{pendingOrderId}}")
        ));
        createOrder.setBody("""
                {
                  "orderId": "{{pendingOrderId}}",
                  "tenantId": "{{tenantId}}",
                  "source": "default-script-example"
                }""");
        createOrder.setPrescript("""
                var incomingOrderId = pm.iterationData.get('orderId');
                var pendingOrderId = incomingOrderId || ('order-' + Date.now());
                pm.variables.set('pendingOrderId', pendingOrderId);
                console.log('pending orderId =', pendingOrderId);
                """);
        createOrder.setPostscript("""
                var jsonData = pm.response.json();
                var orderId = jsonData.json.orderId;
                pm.variables.set('orderId', orderId);
                pm.test('Order context request is successful', function () {
                    pm.response.to.have.status(200);
                });
                pm.test('orderId is stored for the next request', function () {
                    pm.expect(orderId).to.eql(pm.variables.get('pendingOrderId'));
                    pm.expect(pm.variables.get('orderId')).to.eql(orderId);
                });
                """);
        addRequestChild(groupNode, createOrder);

        HttpRequestItem reuseOrder = HttpRequestFactory.createDefaultRequest();
        reuseOrder.setName("4. Reuse OrderId Across Requests");
        reuseOrder.setDescription("Consumes the orderId created by request 3. This is the default two-request chain for Functional and Performance testing.");
        reuseOrder.setMethod("GET");
        reuseOrder.setUrl(HTTPBIN_BASE_URL + "/anything/orders/{{orderId}}");
        reuseOrder.setHeadersList(withExtraHeaders(reuseOrder,
                new HttpHeader(true, "X-Order-Id", "{{orderId}}")));
        reuseOrder.setParamsList(List.of(
                new HttpParam(true, "orderId", "{{orderId}}"),
                new HttpParam(true, "tenantId", "{{tenantId}}")
        ));
        reuseOrder.setPostscript("""
                var jsonData = pm.response.json();
                pm.test('Reused orderId request is successful', function () {
                    pm.response.to.have.status(200);
                });
                pm.test('orderId passes through to the next request', function () {
                    pm.expect(jsonData.args.orderId).to.eql(pm.variables.get('orderId'));
                    pm.expect(jsonData.headers['X-Order-Id']).to.eql(pm.variables.get('orderId'));
                    pm.expect(jsonData.url).to.include('/orders/' + pm.variables.get('orderId'));
                });
                console.log('reused orderId =', pm.variables.get('orderId'));
                """);
        addRequestChild(groupNode, reuseOrder);

        HttpRequestItem extractOrder = HttpRequestFactory.createDefaultRequest();
        extractOrder.setName("5. Extract OrderId From Response");
        extractOrder.setDescription("Simulates creating an order, then extracts the echoed orderId from the response body into pm.variables for the next request.");
        extractOrder.setMethod("POST");
        extractOrder.setUrl(HTTPBIN_POST_URL);
        extractOrder.setBodyType(BODY_TYPE_RAW);
        extractOrder.setHeadersList(withExtraHeaders(extractOrder,
                new HttpHeader(true, CONTENT_TYPE, APPLICATION_JSON)));
        extractOrder.setBody("""
                {
                  "clientOrderId": "{{responseOrderCandidate}}",
                  "tenantId": "{{tenantId}}",
                  "scenario": "extract-from-response"
                }""");
        extractOrder.setPrescript("""
                pm.variables.set('responseOrderCandidate', 'resp-order-' + Date.now());
                console.log('response order candidate =', pm.variables.get('responseOrderCandidate'));
                """);
        extractOrder.setPostscript("""
                var jsonData = pm.response.json();
                var extractedOrderId = jsonData.json.clientOrderId;
                pm.variables.set('responseOrderId', extractedOrderId);
                pm.test('Response extraction request is successful', function () {
                    pm.response.to.have.status(200);
                });
                pm.test('orderId is extracted from response JSON', function () {
                    pm.expect(extractedOrderId).to.eql(pm.variables.get('responseOrderCandidate'));
                    pm.expect(pm.variables.get('responseOrderId')).to.eql(extractedOrderId);
                });
                """);
        addRequestChild(groupNode, extractOrder);

        HttpRequestItem reuseExtractedOrder = HttpRequestFactory.createDefaultRequest();
        reuseExtractedOrder.setName("6. Reuse Extracted OrderId");
        reuseExtractedOrder.setDescription("Consumes the orderId extracted by request 5. Use this pair when you want a response-body extraction workflow.");
        reuseExtractedOrder.setMethod("GET");
        reuseExtractedOrder.setUrl(HTTPBIN_BASE_URL + "/anything/orders/extracted/{{responseOrderId}}");
        reuseExtractedOrder.setHeadersList(withExtraHeaders(reuseExtractedOrder,
                new HttpHeader(true, "X-Extracted-Order-Id", "{{responseOrderId}}")));
        reuseExtractedOrder.setParamsList(List.of(
                new HttpParam(true, "extractedOrderId", "{{responseOrderId}}"),
                new HttpParam(true, "source", "response-body")
        ));
        reuseExtractedOrder.setPostscript("""
                var jsonData = pm.response.json();
                pm.test('Reused extracted orderId request is successful', function () {
                    pm.response.to.have.status(200);
                });
                pm.test('Extracted orderId passes through to the next request', function () {
                    pm.expect(jsonData.args.extractedOrderId).to.eql(pm.variables.get('responseOrderId'));
                    pm.expect(jsonData.headers['X-Extracted-Order-Id']).to.eql(pm.variables.get('responseOrderId'));
                    pm.expect(jsonData.url).to.include('/orders/extracted/' + pm.variables.get('responseOrderId'));
                });
                console.log('reused extracted orderId =', pm.variables.get('responseOrderId'));
                """);
        addRequestChild(groupNode, reuseExtractedOrder);

        HttpRequestItem websocket = HttpRequestFactory.createDefaultWebSocketRequest();
        websocket.setName("7. WebSocket Workflow Check");
        websocket.setDescription("Run request 1 first. Then click Connect and send the default message once if needed. The callback-thread checks focus on whether pm.environment and pm.variables stay available after WebSocket messages arrive.");
        websocket.setBodyType(BODY_TYPE_RAW);
        websocket.setBody("""
                {
                  "type": "context-check",
                  "source": "easy-postman-websocket-example",
                  "message": "send-once-after-connect"
                }""");
        websocket.setPrescript("""
                pm.variables.set('streamTraceId', 'ws-' + Date.now());
                pm.variables.set('streamScenario', 'websocket-context');
                pm.variables.set('streamTenantId', pm.environment.get('tenantId') || '');
                console.log('WebSocket traceId =', pm.variables.get('streamTraceId'));
                console.log('tenantId =', pm.environment.get('tenantId'));
                console.log('Connect first, then send the default message once.');
                """);
        websocket.setPostscript("""
                var messageText = pm.response.text() || '';
                pm.variables.set('lastWebSocketMessage', messageText);
                pm.test('WebSocket keeps execution variables across message callbacks', function () {
                    pm.expect(pm.variables.get('streamTraceId')).to.exist();
                    pm.expect(pm.variables.get('streamScenario')).to.eql('websocket-context');
                    pm.expect(pm.variables.get('streamTenantId')).to.eql(pm.environment.get('tenantId'));
                });
                pm.test('WebSocket callback can still read environment values', function () {
                    pm.expect(pm.environment.get('tenantId')).to.exist();
                    pm.expect(pm.environment.get('userId')).to.exist();
                });
                pm.test('WebSocket callback receives non-empty text', function () {
                    pm.expect(messageText.length > 0).to.eql(true);
                });
                console.log('last websocket message =', messageText);
                """);
        addRequestChild(groupNode, websocket);

        HttpRequestItem sse = HttpRequestFactory.createDefaultEventStreamRequest();
        sse.setName("8. SSE Workflow Check");
        sse.setDescription("Run request 1 first. Then open the stream and wait for a few events. Each SSE event verifies that both environment values and execution variables remain available.");
        sse.setHeadersList(withExtraHeaders(sse,
                new HttpHeader(true, "X-Tenant-Id", "{{tenantId}}")));
        sse.setPrescript("""
                pm.variables.set('sseTraceId', 'sse-' + Date.now());
                pm.variables.set('sseScenario', 'sse-context');
                pm.variables.set('sseEventCount', '0');
                pm.variables.set('sseTenantId', pm.environment.get('tenantId') || '');
                console.log('SSE traceId =', pm.variables.get('sseTraceId'));
                console.log('Open the stream and wait for a few events.');
                """);
        sse.setPostscript("""
                var eventText = pm.response.text() || '';
                var count = parseInt(pm.variables.get('sseEventCount') || '0', 10) + 1;
                pm.variables.set('sseEventCount', String(count));
                pm.variables.set('lastSseEvent', eventText);
                pm.test('SSE keeps execution variables across event callbacks', function () {
                    pm.expect(pm.variables.get('sseTraceId')).to.exist();
                    pm.expect(pm.variables.get('sseScenario')).to.eql('sse-context');
                    pm.expect(pm.variables.get('sseTenantId')).to.eql(pm.environment.get('tenantId'));
                });
                pm.test('SSE callback can still read environment values', function () {
                    pm.expect(pm.environment.get('tenantId')).to.exist();
                    pm.expect(pm.environment.get('userId')).to.exist();
                });
                pm.test('SSE callback receives event text', function () {
                    pm.expect(eventText.length > 0).to.eql(true);
                });
                console.log('sse event #' + count + ', traceId = ' + pm.variables.get('sseTraceId'));
                """);
        addRequestChild(groupNode, sse);
        return groupNode;
    }

    private CollectionNode createChineseScriptExamples() {
        RequestGroup group = new RequestGroup(SCRIPT_EXAMPLES_ZH_GROUP_NAME);
        group.setDescription("""
                变量流程示例建议按请求 1 到 6 的顺序执行。
                请求 7 和请求 8 用来验证 WebSocket / SSE 回调脚本里的运行变量上下文。
                执行请求 7、8 前，建议先执行请求 1，这样它们会复用请求 1 准备好的环境变量。
                
                请求 1 在脚本里设置环境变量。
                请求 2 在请求参数、请求头、请求体和测试脚本里复用这些变量。
                请求 2 还会创建当前运行上下文内可复用的运行变量。
                请求 3 会生成当前运行使用的 orderId，并写入 pm.variables。
                请求 4 会把这个 orderId 继续透传到 URL、参数、请求头和测试脚本中。
                请求 5 会从响应体中提取 orderId，并写入 pm.variables。
                请求 6 会继续复用这个从响应体提取出的 orderId。
                请求 7、8 用来验证 WebSocket / SSE 回调线程里仍然能持续读取 pm.environment 和 pm.variables。
                
                作用域说明：
                - pm.environment：可跨请求复用，后续请求也能继续使用
                - pm.variables：在当前运行上下文内共享，运行结束后自动清空
                - pm.iterationData：数据驱动运行时的当前 CSV/JSON 行
                - Authorization 优先级：脚本显式写入的 Authorization > 已有 Authorization 请求头 > auth tab 自动生成的 Authorization
                - 在 pre-script 中删除 Authorization 不表示禁用 auth tab；如果最终没有 Authorization，发送时仍会按 auth tab 配置补齐
                """);
        CollectionNode groupNode = CollectionNode.group(group);

        HttpRequestItem setup = HttpRequestFactory.createDefaultRequest();
        setup.setName("1. 设置变量");
        setup.setDescription("在前置脚本中设置共享环境变量，并演示一个运行变量。");
        setup.setMethod("GET");
        setup.setUrl("{{baseUrl}}/anything/setup-cn");
        setup.setHeadersList(withExtraHeaders(setup,
                new HttpHeader(true, "X-Setup-Trace", "{{setupTraceId}}")));
        setup.setParamsList(List.of(
                new HttpParam(true, "tenant", "{{tenantId}}")
        ));
        setup.setPrescript("""
                pm.environment.set('baseUrl', 'https://httpbin.org');
                pm.environment.set('tenantId', 'team-cn');
                pm.environment.set('userId', 'user-cn-1001');
                pm.environment.set('keyword', 'easy-postman-cn');
                
                pm.variables.set('setupTraceId', 'setup-cn-' + Date.now());
                console.log('环境变量已准备好:', JSON.stringify(pm.environment.toObject()));
                """);
        setup.setPostscript("""
                var jsonData = pm.response.json();
                pm.test('设置变量请求成功', function () {
                    pm.response.to.have.status(200);
                    pm.expect(jsonData.args.tenant).to.eql(pm.environment.get('tenantId'));
                });
                """);
        addRequestChild(groupNode, setup);

        HttpRequestItem reuse = HttpRequestFactory.createDefaultRequest();
        reuse.setName("2. 复用变量");
        reuse.setDescription("在请求参数、请求头、请求体和脚本里复用环境变量；同时创建并使用当前运行上下文的运行变量。");
        reuse.setMethod("POST");
        reuse.setUrl(HTTPBIN_POST_URL);
        reuse.setBodyType(BODY_TYPE_RAW);
        reuse.setHeadersList(withExtraHeaders(reuse,
                new HttpHeader(true, CONTENT_TYPE, APPLICATION_JSON),
                new HttpHeader(true, "X-Tenant-Id", "{{tenantId}}"),
                new HttpHeader(true, "X-Trace-Id", "{{traceId}}")));
        reuse.setParamsList(List.of(
                new HttpParam(true, "keyword", "{{keyword}}"),
                new HttpParam(true, "trace", "{{traceId}}")
        ));
        reuse.setBody("""
                {
                  "tenantId": "{{tenantId}}",
                  "userId": "{{userId}}",
                  "traceId": "{{traceId}}",
                  "note": "{{note}}"
                }""");
        reuse.setPrescript("""
                pm.variables.set('traceId', 'trace-cn-' + Date.now());
                pm.variables.set('note', '运行变量可用于请求体、请求头和请求参数');
                
                pm.request.headers.upsert({
                    key: 'X-Script-Flag',
                    value: 'ready-cn'
                });
                """);
        reuse.setPostscript("""
                var jsonData = pm.response.json();
                pm.test('复用变量请求成功', function () {
                    pm.response.to.have.status(200);
                });
                pm.test('环境变量复用成功', function () {
                    pm.expect(jsonData.json.tenantId).to.eql(pm.environment.get('tenantId'));
                    pm.expect(jsonData.json.userId).to.eql(pm.environment.get('userId'));
                    pm.expect(jsonData.args.keyword).to.eql(pm.environment.get('keyword'));
                });
                pm.test('运行变量在当前运行中复用成功', function () {
                    pm.expect(jsonData.args.trace).to.eql(pm.variables.get('traceId'));
                    pm.expect(jsonData.json.traceId).to.eql(pm.variables.get('traceId'));
                    pm.expect(jsonData.json.note).to.eql(pm.variables.get('note'));
                    pm.expect(jsonData.headers['X-Trace-Id']).to.eql(pm.variables.get('traceId'));
                });
                console.log('当前 traceId =', pm.variables.get('traceId'));
                """);
        addRequestChild(groupNode, reuse);

        HttpRequestItem createOrder = HttpRequestFactory.createDefaultRequest();
        createOrder.setName("3. 生成订单上下文");
        createOrder.setDescription("为当前运行生成或接收一个 orderId，通过 httpbin 回显后写入 pm.variables，供下一个请求继续复用。");
        createOrder.setMethod("POST");
        createOrder.setUrl(HTTPBIN_POST_URL);
        createOrder.setBodyType(BODY_TYPE_RAW);
        createOrder.setHeadersList(withExtraHeaders(createOrder,
                new HttpHeader(true, CONTENT_TYPE, APPLICATION_JSON),
                new HttpHeader(true, "X-Order-Id", "{{pendingOrderId}}")));
        createOrder.setParamsList(List.of(
                new HttpParam(true, "stage", "create-order"),
                new HttpParam(true, "pendingOrderId", "{{pendingOrderId}}")
        ));
        createOrder.setBody("""
                {
                  "orderId": "{{pendingOrderId}}",
                  "tenantId": "{{tenantId}}",
                  "source": "default-script-example-cn"
                }""");
        createOrder.setPrescript("""
                var incomingOrderId = pm.iterationData.get('orderId');
                var pendingOrderId = incomingOrderId || ('order-' + Date.now());
                pm.variables.set('pendingOrderId', pendingOrderId);
                console.log('待透传 orderId =', pendingOrderId);
                """);
        createOrder.setPostscript("""
                var jsonData = pm.response.json();
                var orderId = jsonData.json.orderId;
                pm.variables.set('orderId', orderId);
                pm.test('订单上下文请求成功', function () {
                    pm.response.to.have.status(200);
                });
                pm.test('orderId 已写入下一请求可用的运行变量', function () {
                    pm.expect(orderId).to.eql(pm.variables.get('pendingOrderId'));
                    pm.expect(pm.variables.get('orderId')).to.eql(orderId);
                });
                """);
        addRequestChild(groupNode, createOrder);

        HttpRequestItem reuseOrder = HttpRequestFactory.createDefaultRequest();
        reuseOrder.setName("4. 跨请求复用 OrderId");
        reuseOrder.setDescription("消费请求 3 生成的 orderId。这是默认可直接用于 Functional 和 Performance 的两请求串联示例。");
        reuseOrder.setMethod("GET");
        reuseOrder.setUrl(HTTPBIN_BASE_URL + "/anything/orders/{{orderId}}");
        reuseOrder.setHeadersList(withExtraHeaders(reuseOrder,
                new HttpHeader(true, "X-Order-Id", "{{orderId}}")));
        reuseOrder.setParamsList(List.of(
                new HttpParam(true, "orderId", "{{orderId}}"),
                new HttpParam(true, "tenantId", "{{tenantId}}")
        ));
        reuseOrder.setPostscript("""
                var jsonData = pm.response.json();
                pm.test('复用 orderId 请求成功', function () {
                    pm.response.to.have.status(200);
                });
                pm.test('orderId 已成功透传到下一个请求', function () {
                    pm.expect(jsonData.args.orderId).to.eql(pm.variables.get('orderId'));
                    pm.expect(jsonData.headers['X-Order-Id']).to.eql(pm.variables.get('orderId'));
                    pm.expect(jsonData.url).to.include('/orders/' + pm.variables.get('orderId'));
                });
                console.log('当前复用的 orderId =', pm.variables.get('orderId'));
                """);
        addRequestChild(groupNode, reuseOrder);

        HttpRequestItem extractOrder = HttpRequestFactory.createDefaultRequest();
        extractOrder.setName("5. 从响应提取 OrderId");
        extractOrder.setDescription("模拟创建订单后，从响应体里提取回显的 orderId，写入 pm.variables，供下一个请求复用。");
        extractOrder.setMethod("POST");
        extractOrder.setUrl(HTTPBIN_POST_URL);
        extractOrder.setBodyType(BODY_TYPE_RAW);
        extractOrder.setHeadersList(withExtraHeaders(extractOrder,
                new HttpHeader(true, CONTENT_TYPE, APPLICATION_JSON)));
        extractOrder.setBody("""
                {
                  "clientOrderId": "{{responseOrderCandidate}}",
                  "tenantId": "{{tenantId}}",
                  "scenario": "extract-from-response"
                }""");
        extractOrder.setPrescript("""
                pm.variables.set('responseOrderCandidate', 'resp-order-' + Date.now());
                console.log('待从响应提取的 orderId =', pm.variables.get('responseOrderCandidate'));
                """);
        extractOrder.setPostscript("""
                var jsonData = pm.response.json();
                var extractedOrderId = jsonData.json.clientOrderId;
                pm.variables.set('responseOrderId', extractedOrderId);
                pm.test('响应提取请求成功', function () {
                    pm.response.to.have.status(200);
                });
                pm.test('orderId 已从响应 JSON 中提取', function () {
                    pm.expect(extractedOrderId).to.eql(pm.variables.get('responseOrderCandidate'));
                    pm.expect(pm.variables.get('responseOrderId')).to.eql(extractedOrderId);
                });
                """);
        addRequestChild(groupNode, extractOrder);

        HttpRequestItem reuseExtractedOrder = HttpRequestFactory.createDefaultRequest();
        reuseExtractedOrder.setName("6. 复用提取出的 OrderId");
        reuseExtractedOrder.setDescription("消费请求 5 从响应体里提取的 orderId。适合验证“提取响应字段再传给下一请求”的流程。");
        reuseExtractedOrder.setMethod("GET");
        reuseExtractedOrder.setUrl(HTTPBIN_BASE_URL + "/anything/orders/extracted/{{responseOrderId}}");
        reuseExtractedOrder.setHeadersList(withExtraHeaders(reuseExtractedOrder,
                new HttpHeader(true, "X-Extracted-Order-Id", "{{responseOrderId}}")));
        reuseExtractedOrder.setParamsList(List.of(
                new HttpParam(true, "extractedOrderId", "{{responseOrderId}}"),
                new HttpParam(true, "source", "response-body")
        ));
        reuseExtractedOrder.setPostscript("""
                var jsonData = pm.response.json();
                pm.test('复用提取出的 orderId 请求成功', function () {
                    pm.response.to.have.status(200);
                });
                pm.test('提取出的 orderId 已透传到下一个请求', function () {
                    pm.expect(jsonData.args.extractedOrderId).to.eql(pm.variables.get('responseOrderId'));
                    pm.expect(jsonData.headers['X-Extracted-Order-Id']).to.eql(pm.variables.get('responseOrderId'));
                    pm.expect(jsonData.url).to.include('/orders/extracted/' + pm.variables.get('responseOrderId'));
                });
                console.log('当前复用的提取 orderId =', pm.variables.get('responseOrderId'));
                """);
        addRequestChild(groupNode, reuseExtractedOrder);

        HttpRequestItem websocket = HttpRequestFactory.createDefaultWebSocketRequest();
        websocket.setName("7. WebSocket 流程校验");
        websocket.setDescription("建议先执行请求 1。然后点击连接；如果需要，再发送一次默认消息体。这个示例重点校验 WebSocket 回调线程里仍能读取 pm.environment 和 pm.variables，而不是依赖请求体里的占位符替换。");
        websocket.setBodyType(BODY_TYPE_RAW);
        websocket.setBody("""
                {
                  "type": "context-check",
                  "source": "easy-postman-websocket-example",
                  "message": "send-once-after-connect"
                }""");
        websocket.setPrescript("""
                pm.variables.set('streamTraceId', 'ws-cn-' + Date.now());
                pm.variables.set('streamScenario', 'websocket-context');
                pm.variables.set('streamTenantId', pm.environment.get('tenantId') || '');
                console.log('WebSocket traceId =', pm.variables.get('streamTraceId'));
                console.log('tenantId =', pm.environment.get('tenantId'));
                console.log('请先点击连接，再发送一次默认消息体。');
                """);
        websocket.setPostscript("""
                var messageText = pm.response.text() || '';
                pm.variables.set('lastWebSocketMessage', messageText);
                pm.test('WebSocket 回调线程里仍能读取运行变量', function () {
                    pm.expect(pm.variables.get('streamTraceId')).to.exist();
                    pm.expect(pm.variables.get('streamScenario')).to.eql('websocket-context');
                    pm.expect(pm.variables.get('streamTenantId')).to.eql(pm.environment.get('tenantId'));
                });
                pm.test('WebSocket 回调线程里仍能读取环境变量', function () {
                    pm.expect(pm.environment.get('tenantId')).to.exist();
                    pm.expect(pm.environment.get('userId')).to.exist();
                });
                pm.test('WebSocket 回调收到非空文本', function () {
                    pm.expect(messageText.length > 0).to.eql(true);
                });
                console.log('最近一条 WebSocket 消息 =', messageText);
                """);
        addRequestChild(groupNode, websocket);

        HttpRequestItem sse = HttpRequestFactory.createDefaultEventStreamRequest();
        sse.setName("8. SSE 流程校验");
        sse.setDescription("建议先执行请求 1。然后打开 SSE 流并等待几条事件。每条事件都会校验环境变量和运行变量都没有丢失。");
        sse.setHeadersList(withExtraHeaders(sse,
                new HttpHeader(true, "X-Tenant-Id", "{{tenantId}}")));
        sse.setPrescript("""
                pm.variables.set('sseTraceId', 'sse-cn-' + Date.now());
                pm.variables.set('sseScenario', 'sse-context');
                pm.variables.set('sseEventCount', '0');
                pm.variables.set('sseTenantId', pm.environment.get('tenantId') || '');
                console.log('SSE traceId =', pm.variables.get('sseTraceId'));
                console.log('请打开流并等待几条事件。');
                """);
        sse.setPostscript("""
                var eventText = pm.response.text() || '';
                var count = parseInt(pm.variables.get('sseEventCount') || '0', 10) + 1;
                pm.variables.set('sseEventCount', String(count));
                pm.variables.set('lastSseEvent', eventText);
                pm.test('SSE 事件回调里仍能读取运行变量', function () {
                    pm.expect(pm.variables.get('sseTraceId')).to.exist();
                    pm.expect(pm.variables.get('sseScenario')).to.eql('sse-context');
                    pm.expect(pm.variables.get('sseTenantId')).to.eql(pm.environment.get('tenantId'));
                });
                pm.test('SSE 事件回调里仍能读取环境变量', function () {
                    pm.expect(pm.environment.get('tenantId')).to.exist();
                    pm.expect(pm.environment.get('userId')).to.exist();
                });
                pm.test('SSE 回调收到了事件文本', function () {
                    pm.expect(eventText.length > 0).to.eql(true);
                });
                console.log('第 ' + count + ' 条 SSE 事件, traceId = ' + pm.variables.get('sseTraceId'));
                """);
        addRequestChild(groupNode, sse);
        return groupNode;
    }

    private CollectionNode createBasicHttpExamples() {
        RequestGroup defaultGroup = new RequestGroup(BASIC_HTTP_EXAMPLES_GROUP_NAME);
        defaultGroup.setDescription("""
                General-purpose examples for common request types.
                These requests are independent from the official script examples.
                """);
        CollectionNode defaultGroupNode = CollectionNode.group(defaultGroup);

        HttpRequestItem getExample = HttpRequestFactory.createDefaultRequest();
        getExample.setName("GET Example");
        getExample.setMethod("GET");
        getExample.setUrl(HTTPBIN_BASE_URL + "/get?q=easytools&lang=en&page=1&size=10&sort=desc&filter=active");
        getExample.setParamsList(List.of(
                new HttpParam(true, "q", "easytools"),
                new HttpParam(true, "lang", "en"),
                new HttpParam(true, "page", "1"),
                new HttpParam(true, "size", "10"),
                new HttpParam(true, "sort", "desc"),
                new HttpParam(true, "filter", "active")
        ));
        getExample.setPostscript("""
                pm.test('JSON value check', function () {
                    var jsonData = pm.response.json();
                    pm.expect(jsonData.args.size).to.eql("10");
                });
                """);
        addRequestChild(defaultGroupNode, getExample);

        HttpRequestItem postJson = HttpRequestFactory.createDefaultRequest();
        postJson.setName("POST JSON Example");
        postJson.setMethod("POST");
        postJson.setUrl(HTTPBIN_POST_URL);
        postJson.setBodyType(BODY_TYPE_RAW);
        postJson.setHeadersList(withExtraHeaders(postJson,
                new HttpHeader(true, CONTENT_TYPE, APPLICATION_JSON)));
        postJson.setBody("""
                {
                  "key1": "value1",
                  "key2": "value2",
                  "key3": "value3"
                }""");
        postJson.setPostscript("""
                pm.test('JSON value check', function () {
                    var jsonData = pm.response.json();
                    pm.expect(jsonData.json.key1).to.eql("value1");
                });
                """);
        addRequestChild(defaultGroupNode, postJson);

        HttpRequestItem postFormData = HttpRequestFactory.createDefaultRequest();
        postFormData.setName("POST form-data Example");
        postFormData.setMethod("POST");
        postFormData.setUrl(HTTPBIN_POST_URL);
        postFormData.setHeadersList(withExtraHeaders(postFormData,
                new HttpHeader(true, CONTENT_TYPE, "multipart/form-data")));
        postFormData.setFormDataList(List.of(
                new HttpFormData(true, "key1", HttpFormData.TYPE_TEXT, "value1"),
                new HttpFormData(true, "key2", HttpFormData.TYPE_TEXT, "value2")
        ));
        postFormData.setPostscript("""
                pm.test('Form-data value check', function () {
                    var jsonData = pm.response.json();
                    pm.expect(jsonData.form.key1).to.eql("value1");
                });
                """);
        addRequestChild(defaultGroupNode, postFormData);

        HttpRequestItem postUrl = HttpRequestFactory.createDefaultRequest();
        postUrl.setName("POST x-www-form-urlencoded Example");
        postUrl.setMethod("POST");
        postUrl.setUrl(HTTPBIN_POST_URL);
        postUrl.setHeadersList(withExtraHeaders(postUrl,
                new HttpHeader(true, CONTENT_TYPE, "application/x-www-form-urlencoded")));
        postUrl.setUrlencodedList(List.of(
                new HttpFormUrlencoded(true, "key1", "value1"),
                new HttpFormUrlencoded(true, "key2", "value2")
        ));
        postUrl.setPostscript("""
                pm.test('URL encoded value check', function () {
                    var jsonData = pm.response.json();
                    pm.expect(jsonData.form.key1).to.eql("value1");
                });
                """);
        addRequestChild(defaultGroupNode, postUrl);

        HttpRequestItem put = HttpRequestFactory.createDefaultRequest();
        put.setName("PUT Example");
        put.setMethod("PUT");
        put.setUrl(HTTPBIN_BASE_URL + "/put");
        put.setBodyType(BODY_TYPE_RAW);
        put.setHeadersList(withExtraHeaders(put,
                new HttpHeader(true, CONTENT_TYPE, APPLICATION_JSON)));
        put.setBody("{\"update\":true}");
        addRequestChild(defaultGroupNode, put);

        HttpRequestItem delete = HttpRequestFactory.createDefaultRequest();
        delete.setName("DELETE Example");
        delete.setMethod("DELETE");
        delete.setUrl(HTTPBIN_BASE_URL + "/delete");
        addRequestChild(defaultGroupNode, delete);

        HttpRequestItem redirect = HttpRequestFactory.createDefaultRedirectRequest();
        redirect.setName("Redirect Example");
        addRequestChild(defaultGroupNode, redirect);

        HttpRequestItem websocket = HttpRequestFactory.createDefaultWebSocketRequest();
        websocket.setName("WebSocket Example");
        addRequestChild(defaultGroupNode, websocket);

        HttpRequestItem sse = HttpRequestFactory.createDefaultEventStreamRequest();
        addRequestChild(defaultGroupNode, sse);
        return defaultGroupNode;
    }

    private List<HttpHeader> withExtraHeaders(HttpRequestItem item, HttpHeader... extraHeaders) {
        List<HttpHeader> headers = new ArrayList<>(item.getHeadersList());
        headers.addAll(List.of(extraHeaders));
        return headers;
    }

    private void addRequestChild(CollectionNode parentNode, HttpRequestItem item) {
        parentNode.addChild(CollectionNode.request(item));
    }
}
