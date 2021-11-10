import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.DiffRequestFactory;
import com.intellij.diff.chains.DiffRequestChain;
import com.intellij.diff.chains.SimpleDiffRequestChain;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.notification.*;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedList;


/*
 * Heavily based on CompareFileWithEditorAction
 */
public class ShowDiffAction extends BaseShowDiffAction {

    @Override
    protected boolean isAvailable(@NotNull AnActionEvent e) {

        VirtualFile selectedFile = getSelectedFile(e);
        Project project = e.getProject();
        return selectedFile != null && project != null;
    }

    private static @Nullable VirtualFile getSelectedFile(@NotNull AnActionEvent e) {
        VirtualFile[] array = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
        if (array == null || array.length != 1) {
            return null;
        }

        return array[0];
    }

    @Override
    protected DiffRequestChain getDiffRequestChain(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        assert project != null;

        VirtualFile selectedFile = getSelectedFile(e);
        assert selectedFile != null;

        VirtualFile root = ProjectRootManager.getInstance(project).getFileIndex().getSourceRootForFile(selectedFile);

        if ( root == null ){
            if( selectedFile.isDirectory() ) {
                root = selectedFile;
            } else {
                root = selectedFile.getParent();
            }
        }

        if( root == null ) {
            Notifications.Bus.notify(new Notification("Editor notifications",
                    "Could not find a suitable spot to start searching for snapshots", NotificationType.ERROR));

            return null;
        }

        LinkedList<VirtualFile> conflictingSnapshots = new LinkedList<>();
        VfsUtilCore.visitChildrenRecursively(root, new SnapshotConflictFinder(conflictingSnapshots));

        if( conflictingSnapshots.size() == 0 ) {
            Notifications.Bus.notify(new Notification("Editor notifications",
                    "No conflicting snapshots were found", NotificationType.INFORMATION));
            return null;
        }

        var list = new LinkedList<SimpleDiffRequestChain.DiffRequestProducerWrapper>();

        for ( var conflictingSnapshot : conflictingSnapshots ){
            var originalSnapshot = conflictingSnapshot.getParent().getParent().findFileByRelativePath( conflictingSnapshot.getName() );
            if( originalSnapshot == null ) continue;
            var diffRequest = createDiffRequest(project, originalSnapshot, conflictingSnapshot);
            list.add(new SimpleDiffRequestChain.DiffRequestProducerWrapper(diffRequest));
        }

        return SimpleDiffRequestChain.fromProducers(list);
    }

    private static class SnapshotConflictFinder extends VirtualFileVisitor<Void> {

        private final LinkedList<VirtualFile> conflictingSnapshots;

        private SnapshotConflictFinder(LinkedList<VirtualFile> result) {
            this.conflictingSnapshots = result;
        }

        @Override
        public boolean visitFile(@NotNull VirtualFile file) {
            var inMismatchDirectory = file.getParent() != null && file.getParent().getName().contains("__mismatch__");
            System.out.println("Visiting file " + file.getName());
            if( inMismatchDirectory && !file.isDirectory() ){
                this.conflictingSnapshots.add(file);
                System.out.println("Adding " + file.getName());
            }

            return file.isDirectory();
        }
    }

    @NotNull
    protected static SimpleDiffRequest createDiffRequest(@Nullable Project project,
                                                                   @NotNull VirtualFile currentSnapshot,
                                                                   @NotNull VirtualFile conflictingSnapshot) {

        DiffContentFactory contentFactory = DiffContentFactory.getInstance();
        DiffRequestFactory requestFactory = DiffRequestFactory.getInstance();

        DiffContent content1 = contentFactory.create(project, currentSnapshot);
        DiffContent content2 = contentFactory.create(project, conflictingSnapshot);

        return new SimpleDiffRequest(requestFactory.getTitle(currentSnapshot, conflictingSnapshot),
                content1,
                content2,
                "Current snapshot: " + currentSnapshot.getName(), "Conflicting snapshot");

    }
}
