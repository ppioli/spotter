import com.intellij.diff.actions.CompareClipboardWithSelectionAction;
import com.intellij.diff.actions.CompareFileWithEditorAction;
import com.intellij.diff.actions.impl.MutableDiffRequestChain;
import com.intellij.diff.chains.DiffRequestChain;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.util.DiffUserDataKeys;
import com.intellij.diff.util.Side;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


/*
 * Heavily based on CompareFileWithEditorAction
 */
public class ShowDiffAction extends BaseShowDiffAction {

    @Override
    protected boolean isAvailable(@NotNull AnActionEvent e) {

        VirtualFile selectedFile = getSelectedFile(e);
        if (selectedFile == null) {
            return false;
        }
        // TODO Remove duplicated content
        VirtualFile currentFile = selectedFile.getParent().findFileByRelativePath( "__mismatch__/"+selectedFile.getName());
        if (currentFile == null) {
            return false;
        }

        return true;
    }

    private static @Nullable VirtualFile getSelectedFile(@NotNull AnActionEvent e) {
        VirtualFile[] array = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
        if (array == null || array.length != 1 || array[0].isDirectory()) {
            return null;
        }

        return array[0];
    }

    private static boolean canCompare(@NotNull VirtualFile file1, @NotNull VirtualFile file2) {
        return !file1.equals(file2) && hasContent(file1) && hasContent(file2);
    }

    @Override
    protected @NotNull DiffRequestChain getDiffRequestChain(@NotNull AnActionEvent e) {
        Project project = e.getProject();

        VirtualFile selectedFile = getSelectedFile(e);
        assert selectedFile != null;
        VirtualFile currentFile = selectedFile.getParent().findFileByRelativePath( "__mismatch__/"+selectedFile.getName());
        assert currentFile != null;

        MutableDiffRequestChain chain = createMutableChainFromFiles(project, selectedFile, currentFile);

        DiffContent editorContent = chain.getContent2();
        if (editorContent instanceof DocumentContent) {
            Editor editor = EditorFactory.getInstance().editors(((DocumentContent)editorContent).getDocument()).findFirst().orElse(null);
            if (editor != null) {
                int currentLine = editor.getCaretModel().getLogicalPosition().line;
                chain.putRequestUserData(DiffUserDataKeys.SCROLL_TO_LINE, Pair.create(Side.RIGHT, currentLine));
            }
        }

        return chain;
    }
}
