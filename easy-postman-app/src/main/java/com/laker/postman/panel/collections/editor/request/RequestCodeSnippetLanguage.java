package com.laker.postman.panel.collections.editor.request;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;

@Getter
@RequiredArgsConstructor
enum RequestCodeSnippetLanguage {
    CURL("cURL", SyntaxConstants.SYNTAX_STYLE_UNIX_SHELL),
    JAVA_OKHTTP("Java - OkHttp", SyntaxConstants.SYNTAX_STYLE_JAVA),
    JAVASCRIPT_FETCH("JavaScript - fetch", SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT),
    PYTHON_REQUESTS("Python - requests", SyntaxConstants.SYNTAX_STYLE_PYTHON),
    GO_NET_HTTP("Go - net/http", SyntaxConstants.SYNTAX_STYLE_GO),
    CSHARP_HTTP_CLIENT("C# - HttpClient", SyntaxConstants.SYNTAX_STYLE_CSHARP);

    private final String displayName;
    private final String syntaxStyle;

    @Override
    public String toString() {
        return displayName;
    }
}
