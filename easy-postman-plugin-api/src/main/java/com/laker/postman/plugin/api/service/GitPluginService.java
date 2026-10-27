package com.laker.postman.plugin.api.service;

import com.laker.postman.model.GitAuthType;
import com.laker.postman.model.GitBranchInfo;
import com.laker.postman.model.GitCommitInfo;
import com.laker.postman.model.GitFileChange;
import com.laker.postman.model.GitOperation;
import com.laker.postman.model.GitOperationResult;
import com.laker.postman.model.GitStatusCheck;
import com.laker.postman.model.RemoteStatus;
import com.laker.postman.model.Workspace;

import java.util.List;

public interface GitPluginService {

    void prepareGitWorkspace(Workspace workspace) throws Exception;

    GitStatusCheck checkGitStatus(Workspace workspace, GitOperation operation);

    void clearSshCache(String privateKeyPath);

    GitOperationResult pullUpdates(Workspace workspace) throws Exception;

    GitOperationResult pushChanges(Workspace workspace) throws Exception;

    GitOperationResult forcePushChanges(Workspace workspace) throws Exception;

    GitOperationResult stashChanges(Workspace workspace) throws Exception;

    GitOperationResult popStashChanges(Workspace workspace) throws Exception;

    GitOperationResult forcePullUpdates(Workspace workspace) throws Exception;

    GitOperationResult commitChanges(Workspace workspace, String message) throws Exception;

    List<String> getChangedFilesBetweenCommits(Workspace workspace, String oldCommitId, String newCommitId) throws Exception;

    default List<GitFileChange> listWorkingTreeChanges(Workspace workspace) throws Exception {
        throw new UnsupportedOperationException("Git working tree changes are not supported by this Git service");
    }

    default String getWorkingTreeDiff(Workspace workspace, String filePath) throws Exception {
        throw new UnsupportedOperationException("Git working tree diff is not supported by this Git service");
    }

    void addRemoteRepository(Workspace workspace, String remoteUrl, String remoteBranch,
                             GitAuthType authType, String username, String password, String token) throws Exception;

    default void addRemoteRepository(Workspace workspace, String remoteUrl, String remoteBranch,
                                     GitAuthType authType, String username, String password, String token,
                                     String sshPrivateKeyPath, String sshPassphrase) throws Exception {
        if (authType == GitAuthType.SSH_KEY
                || (sshPrivateKeyPath != null && !sshPrivateKeyPath.isBlank())
                || (sshPassphrase != null && !sshPassphrase.isBlank())) {
            throw new UnsupportedOperationException("Git SSH remote configuration is not supported by this Git service");
        }
        addRemoteRepository(workspace, remoteUrl, remoteBranch, authType, username, password, token);
    }

    default List<String> testRemoteConnection(String remoteUrl, GitAuthType authType,
                                              String username, String password, String token,
                                              String sshPrivateKeyPath, String sshPassphrase) throws Exception {
        throw new UnsupportedOperationException("Remote connection testing is not supported by this Git service");
    }

    RemoteStatus getRemoteStatus(Workspace workspace) throws Exception;

    void convertLocalToGit(Workspace workspace, String localBranch) throws Exception;

    List<GitCommitInfo> getGitHistory(Workspace workspace, int maxCount) throws Exception;

    GitOperationResult restoreToCommit(Workspace workspace, String commitId, boolean createBackup) throws Exception;

    String getCommitDetails(Workspace workspace, String commitId) throws Exception;

    default List<GitBranchInfo> listBranches(Workspace workspace) throws Exception {
        throw new UnsupportedOperationException("Git branch listing is not supported by this Git service");
    }

    default GitOperationResult switchBranch(Workspace workspace, String branchName) throws Exception {
        throw new UnsupportedOperationException("Git branch switching is not supported by this Git service");
    }

    default GitOperationResult fetchBranches(Workspace workspace) throws Exception {
        throw new UnsupportedOperationException("Git branch fetch is not supported by this Git service");
    }

    default GitOperationResult createBranch(Workspace workspace, String branchName) throws Exception {
        throw new UnsupportedOperationException("Git branch creation is not supported by this Git service");
    }

    default GitOperationResult deleteBranch(Workspace workspace, String branchName) throws Exception {
        throw new UnsupportedOperationException("Git branch deletion is not supported by this Git service");
    }

    default GitOperationResult deleteBranch(Workspace workspace, String branchName, boolean force) throws Exception {
        if (force) {
            throw new UnsupportedOperationException("Git force branch deletion is not supported by this Git service");
        }
        return deleteBranch(workspace, branchName);
    }

    default GitOperationResult publishBranch(Workspace workspace) throws Exception {
        throw new UnsupportedOperationException("Git branch publishing is not supported by this Git service");
    }
}
