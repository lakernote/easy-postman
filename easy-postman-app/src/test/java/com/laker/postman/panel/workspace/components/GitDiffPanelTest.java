package com.laker.postman.panel.workspace.components;

import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class GitDiffPanelTest {

    @Test
    public void loadedChangesShouldAutoOpenFirstDiffWithoutBlockingEdt() throws IOException {
        String source = Files.readString(gitDiffPanelSource());
        String displayChanges = methodBody(source, "private void displayChanges", "\n    private void loadDiff");

        assertTrue(displayChanges.contains("changeList.setSelectedIndex(0);"),
                "Opening the Git changes panel should preview the first file for a useful initial experience.");
        assertFalse(displayChanges.contains("renderDiff(diff)"),
                "The first-file preview must still be built through the async diff worker.");
    }

    @Test
    public void constructorShouldDeferInitialChangeLoadingUntilAfterPanelIsShown() throws IOException {
        String source = Files.readString(gitDiffPanelSource());
        String constructor = methodBody(source, "public GitDiffPanel(Workspace workspace)", "\n    private void initUI");

        assertTrue(constructor.contains("SwingUtilities.invokeLater(this::loadChanges);"));
        assertFalse(constructor.contains("\n        loadChanges();"));
    }

    @Test
    public void selectedDiffShouldBuildStyledDocumentOffEdt() throws IOException {
        String source = Files.readString(gitDiffPanelSource());
        String loadDiff = methodBody(source, "private void loadDiff", "\n    private void renderPlainMessage");

        assertTrue(loadDiff.contains("new SwingWorker<>()"));
        assertTrue(loadDiff.contains("int generation = ++diffLoadGeneration;"));
        assertTrue(loadDiff.contains("generation != diffLoadGeneration"));
        assertTrue(loadDiff.contains("return DiffLoadResult.diff(createDiffDocument(diff));"));
        assertFalse(loadDiff.contains("renderDiff(diff)"),
                "Diff styling must not insert large documents line-by-line on the EDT.");
    }

    @Test
    public void loadingChangesShouldIgnoreStaleWorkers() throws IOException {
        String source = Files.readString(gitDiffPanelSource());
        String loadChanges = methodBody(source, "private void loadChanges", "\n    private void displayChanges");

        assertTrue(loadChanges.contains("int generation = ++changeLoadGeneration;"));
        assertTrue(loadChanges.contains("diffLoadGeneration++;"));
        assertTrue(loadChanges.contains("generation != changeLoadGeneration"));
    }

    @Test
    public void repeatedSelectionsShouldCancelPreviousDiffWorker() throws IOException {
        String source = Files.readString(gitDiffPanelSource());
        String loadDiff = methodBody(source, "private void loadDiff", "\n    private StyledDocument createDiffDocument");

        assertTrue(loadDiff.contains("cancelWorker(diffWorker);"));
        assertTrue(source.contains("private static void cancelWorker(SwingWorker<?, ?> worker)"));
    }

    @Test
    public void diffDocumentShouldBoundVeryLongJsonLinesBeforeAttachingToTextPane() throws IOException {
        String source = Files.readString(gitDiffPanelSource());
        String createDocument = methodBody(source, "private StyledDocument createDiffDocument", "\n    private void renderPlainMessage");

        assertTrue(source.contains("MAX_DIFF_LINE_CHARS = 8 * 1024"));
        assertTrue(createDocument.contains("line.length() > MAX_DIFF_LINE_CHARS"));
        assertTrue(createDocument.contains("line.substring(0, MAX_DIFF_LINE_CHARS)"));
    }

    @Test
    public void selectFilePromptShouldPointToLeftChangeList() throws IOException {
        Path zhMessages = repositoryRoot().resolve("easy-postman-app/src/main/resources/messages_zh.properties");
        Path enMessages = repositoryRoot().resolve("easy-postman-app/src/main/resources/messages_en.properties");

        assertTrue(Files.readString(zhMessages).contains("git.diff.select_file=请选择左侧文件查看 Diff"));
        assertTrue(Files.readString(enMessages).contains("git.diff.select_file=Select a file on the left to view its diff"));
    }

    @Test
    public void emptyTextDiffMessageShouldExplainChangedFileMayBeSkipped() throws IOException {
        Path zhMessages = repositoryRoot().resolve("easy-postman-app/src/main/resources/messages_zh.properties");
        Path enMessages = repositoryRoot().resolve("easy-postman-app/src/main/resources/messages_en.properties");

        String zh = Files.readString(zhMessages);
        String en = Files.readString(enMessages);
        assertTrue(zh.contains("git.diff.no_text_diff=该文件已变更，但无法显示文本 Diff"));
        assertTrue(zh.contains("文件过大"));
        assertTrue(en.contains("git.diff.no_text_diff=This file has changes, but no textual diff can be shown"));
        assertTrue(en.contains("too large"));
    }

    private static String methodBody(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start + startMarker.length());
        assertTrue(start >= 0, "Missing method start: " + startMarker);
        assertTrue(end > start, "Missing method end: " + endMarker);
        return source.substring(start, end);
    }

    private static Path gitDiffPanelSource() {
        return repositoryRoot().resolve(
                "easy-postman-app/src/main/java/com/laker/postman/panel/workspace/components/GitDiffPanel.java");
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("pom.xml"))
                    && Files.isDirectory(current.resolve("easy-postman-app"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate repository root");
    }
}
